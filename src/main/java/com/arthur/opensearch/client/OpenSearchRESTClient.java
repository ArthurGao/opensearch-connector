package com.arthur.opensearch.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.arthur.opensearch.config.OpenSearchConfig;
import com.arthur.opensearch.model.AggregationQuery;
import com.arthur.opensearch.model.Index;
import com.arthur.opensearch.model.SearchQuery;
import com.arthur.opensearch.resultconverters.AbstractSearchResultsConverter;
import com.arthur.opensearch.resultconverters.AggregationResultsConverter;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpHost;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.ClearScrollResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.Strings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.script.mustache.SearchTemplateRequest;
import org.opensearch.script.mustache.SearchTemplateResponse;
import org.opensearch.search.Scroll;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;

@Log4j2
public class OpenSearchRESTClient implements Closeable {

  private static final int DEFAULT_AGGS_RESULTS_SIZE = 0;
  private final Gson gson;
  private final OpenSearchConfig config;
  private RestHighLevelClient client;


  public OpenSearchRESTClient(@NonNull OpenSearchConfig config) {
    this(config, new Gson());
  }

  public OpenSearchRESTClient(@NonNull OpenSearchConfig config, @NonNull Gson gson) {
    this.gson = gson;
    this.config = config;
  }

  public void init() {
    client = new RestHighLevelClient(RestClient.builder(HttpHost.create(config.getHostUrl())));
    log.info("Attempting to establish connectivity with OpenSearch at {}.", config.getHostUrl());
  }

  @Override
  public void close() throws IOException {
    client.close();
  }

  public <R> List<R> executeSingleQuery(Index index, @NonNull SearchQuery searchQuery, @NonNull AbstractSearchResultsConverter<R> searchResultsConverter)
      throws IOException {
    SearchRequest searchRequest = buildSearchRequest(index, searchQuery);
    SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
    SearchHit[] searchHits = response.getHits().getHits();
    final long totalResults = response.getHits().getTotalHits().value;
    log.info("Found {} hits [total: {}] for the query {}", searchHits.length, totalResults, searchQuery.getBoolQuery());
    if (totalResults > searchHits.length) {
      log.warn("Has more than {} results for the query. Need to use scroll API to obtain full set of results.", totalResults);
    }
    return processSearchResults(searchHits, searchResultsConverter);
  }

  public <R> List<R> executeQueryWithScroll(Index index, @NonNull SearchQuery searchQuery, @NonNull AbstractSearchResultsConverter<R> searchResultsConverter)
      throws IOException {
    final Scroll scroll = new Scroll(TimeValue.timeValueSeconds(config.getDefaultScrollTimeoutInSeconds()));
    SearchRequest searchRequest = buildSearchRequest(index, searchQuery);
    searchRequest.scroll(scroll);
    SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
    String scrollId = searchResponse.getScrollId();
    SearchHit[] searchHits = searchResponse.getHits().getHits();
    List<R> processedResults = new ArrayList<>();
    while (searchHits != null && searchHits.length > 0) {
      final long totalResults = searchResponse.getHits().getTotalHits().value;
      log.info("Found {} hits [total: {}] for scrollID: {} [index: {}]", searchHits.length, totalResults, searchQuery.getBoolQuery(), scrollId, index);
      processedResults.addAll(processSearchResults(searchHits, searchResultsConverter));
      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(scroll);
      searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
      scrollId = searchResponse.getScrollId();
      searchHits = searchResponse.getHits().getHits();
    }
    log.info("Found {} results for the query {}", processedResults.size(), searchQuery.getBoolQuery());
    String finalScrollId = scrollId;
    clearScroll(finalScrollId, index);
    return processedResults;
  }

  public <R> void executeQueryWithScrollListener(Index index, @NonNull SearchQuery searchQuery,
      @NonNull AbstractSearchResultsConverter<R> searchResultsConverter, Consumer<List<R>> listener) throws IOException {

    final Scroll scroll = new Scroll(TimeValue.timeValueSeconds(config.getDefaultScrollTimeoutInSeconds()));
    SearchRequest searchRequest = buildSearchRequest(index, searchQuery);
    searchRequest.scroll(scroll);
    SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
    String scrollId = searchResponse.getScrollId();
    SearchHit[] searchHits = searchResponse.getHits().getHits();

    while (searchHits != null && searchHits.length > 0) {
      final long totalResults = searchResponse.getHits().getTotalHits().value;
      log.info("Found {} hits [total: {}] for scrollID: {} [index: {}]", searchHits.length, totalResults, searchQuery.getBoolQuery(), scrollId, index);

      listener.accept(processSearchResults(searchHits, searchResultsConverter));

      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(scroll);
      searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
      scrollId = searchResponse.getScrollId();
      searchHits = searchResponse.getHits().getHits();
    }
    String finalScrollId = scrollId;
    clearScroll(finalScrollId, index);
  }

  public <A extends Aggregation, R> Optional<R> executeAggregateQuery(Index index, @NonNull AggregationQuery aggregationQuery,
      @NonNull AggregationResultsConverter<A, R> aggregationResultConverter) throws IOException {
    SearchSourceBuilder searchSourceBuilder = createSearchSourceBuilder(aggregationQuery.getBoolQuery(), DEFAULT_AGGS_RESULTS_SIZE)
        .aggregation(aggregationQuery.getAggregationQuery());
    SearchRequest searchRequest = new SearchRequest()
        .indices(index.getIndexName())
        .source(searchSourceBuilder);
    SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
    A agg = searchResponse.getAggregations().get(aggregationQuery.getAggregationQuery().getName());
    if (agg == null) {
      log.warn("No results found for aggregation [{}] on index: {}", aggregationQuery.getAggregationQuery().getName(), index.getIndexName());
    } else {
      log.info("Found results for aggregation [{}] of type {} on index: {}.", agg.getName(), agg.getType(), index.getIndexName());
    }
    return aggregationResultConverter.convert(agg);
  }

  public <R> List<R> executeTemplateQuery(@NonNull SearchTemplateRequest searchTemplateRequest,
      @NonNull AbstractSearchResultsConverter<R> searchResultsConverter) throws IOException {

    SearchTemplateResponse response = client.searchTemplate(searchTemplateRequest, RequestOptions.DEFAULT);
    SearchHit[] searchHits = response.getResponse().getHits().getHits();
    final long totalResults = response.getResponse().getHits().getTotalHits().value;
    log.info("Found {} hits [total: {}] for the template {}", searchHits.length, totalResults, searchTemplateRequest.getScript());
    if (totalResults > searchHits.length) {
      log.warn("Has more than {} results for the query. Need to use scroll API to obtain full set of results.", totalResults);
    }
    return processSearchResults(searchHits, searchResultsConverter);
  }

  private void clearScroll(String scrollId, Index index) {
    ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
    clearScrollRequest.addScrollId(scrollId);
    ActionListener<ClearScrollResponse> listener =
        new ActionListener<>() {
          @Override
          public void onResponse(ClearScrollResponse clearScrollResponse) {
            log.info("Cleared scroll ID {} for index {} successfully.", scrollId, index.getIndexName());
          }

          @Override
          public void onFailure(Exception e) {
            log.error("Failed to clear scroll ID {} for index {}. Please manually clear it!", scrollId, index.getIndexName(), e);
          }
        };
    client.clearScrollAsync(clearScrollRequest, RequestOptions.DEFAULT, listener);
  }

  private SearchRequest buildSearchRequest(Index index, SearchQuery searchQuery) {
    FetchSourceContext fetchSourceContext = new FetchSourceContext(true, searchQuery.getRequestedSources(),
        Strings.EMPTY_ARRAY);
    SearchRequest request = new SearchRequest();
    request.indices(index.getIndexName());
    AbstractQueryBuilder query = (searchQuery.getFunctionScoreQueryBuilder() != null ? searchQuery.getFunctionScoreQueryBuilder() : searchQuery.getBoolQuery());
    SearchSourceBuilder searchSourceBuilder = createSearchSourceBuilder(query, config.getMaxSearchResultsSize())
        .fetchSource(fetchSourceContext);
    if (searchQuery.getSortCriteria() != null) {
      searchSourceBuilder = searchSourceBuilder
          .sort(searchQuery.getSortCriteria().getSortField(), searchQuery.getSortCriteria().getSortOrder());
    }
    if (searchQuery.getHighlightBuilder() != null) {
      searchSourceBuilder.highlighter(searchQuery.getHighlightBuilder());
    }
    if (searchQuery.getRequestedFields() != null && searchQuery.getRequestedFields().length > 0) {
      Arrays.stream(searchQuery.getRequestedFields()).forEach(searchSourceBuilder::fetchField);
    }
    request.source(searchSourceBuilder);
    return request;
  }


  private SearchSourceBuilder createSearchSourceBuilder(AbstractQueryBuilder query, int resultsSize) {
    return SearchSourceBuilder.searchSource()
        .query(query)
        .size(resultsSize);
  }

  private <R> List<R> processSearchResults(SearchHit[] hits, AbstractSearchResultsConverter<R> searchResultsConverter) {
    if (hits == null || hits.length == 0) {
      return Collections.emptyList();
    }

    return Arrays.stream(hits)
        .map(hit -> JsonParser.parseString(hit.toString()).getAsJsonObject())
        .map(searchResultsConverter::convert)
        .collect(Collectors.toList());
  }

}