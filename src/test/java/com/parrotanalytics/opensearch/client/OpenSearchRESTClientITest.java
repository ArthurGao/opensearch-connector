package com.arthur.opensearch.client;

import static com.arthur.opensearch.testutils.OpenSearchExtention.OPEN_SEARCH_PORT;
import static com.arthur.opensearch.testutils.OpenSearchTestHelper.MAX_RESULTS_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arthur.opensearch.config.OpenSearchConfig;
import com.arthur.opensearch.model.AggregationQuery;
import com.arthur.opensearch.model.Index;
import com.arthur.opensearch.model.SearchQuery;
import com.arthur.opensearch.model.SearchQuery.SearchQueryBuilder;
import com.arthur.opensearch.model.SearchQuery.SortCriteria;
import com.arthur.opensearch.testutils.OpenSearchExtention;
import com.arthur.opensearch.testutils.OpenSearchTestHelper;
import com.arthur.opensearch.testutils.SeriesMetadata;
import com.arthur.opensearch.testutils.SeriesMetadataIndex;
import com.arthur.opensearch.testutils.resultconverters.HistogramAggregationResultsConverter;
import com.arthur.opensearch.testutils.resultconverters.HistogramAggregationResultsConverter.HistogramResult;
import com.arthur.opensearch.testutils.resultconverters.SeriesMetadataSearchResultConverter;
import com.arthur.opensearch.testutils.resultconverters.TermsWithSubAggregationResultsConverter;
import com.arthur.opensearch.testutils.resultconverters.TermsWithSubAggregationResultsConverter.TermsAggregationResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.lucene.search.function.FunctionScoreQuery.ScoreMode;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder.Type;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.sort.SortOrder;
import org.testcontainers.junit.jupiter.Testcontainers;

// NOTE: The maximum results returned by the OpenSearch instance is set in OpenSearchTestHelper
@ExtendWith(OpenSearchExtention.class)
@Testcontainers
class OpenSearchRESTClientITest {

  private static final Index SERIES_METADATA_INDEX = new SeriesMetadataIndex();
  private static final OpenSearchConfig OPEN_SEARCH_CONFIG = OpenSearchConfig.builder()
      .maxSearchResultsSize(MAX_RESULTS_SIZE)
      .defaultScrollTimeoutInSeconds(2)
      .hostUrl("http://localhost:" + OPEN_SEARCH_PORT)
      .build();
  private static final SeriesMetadataSearchResultConverter RESULT_CONVERTER = new SeriesMetadataSearchResultConverter();

  private OpenSearchRESTClient subject;

  @BeforeAll
  public static void setupOpenSearch() throws IOException {
    OpenSearchTestHelper.createIndex(SERIES_METADATA_INDEX, "series_metadata_index.json");
  }

  @AfterAll
  public static void deleteIndex() throws IOException {
    OpenSearchTestHelper.deleteIndex(SERIES_METADATA_INDEX);
  }

  @BeforeEach
  public void setUp() {
    subject = new OpenSearchRESTClient(OPEN_SEARCH_CONFIG);
    subject.init();
  }

  @AfterEach
  public void removeData() throws IOException {
    OpenSearchTestHelper.deleteAllData(SERIES_METADATA_INDEX);
  }

  @Test
  void testExecuteSingleQuery_noIndex_throwsRuntimeException() throws Exception {
    SearchQuery searchQuery = createMatchAllQuery(false);

    Assertions.assertThatThrownBy(() -> subject.executeSingleQuery(() -> "unknownindex", searchQuery, RESULT_CONVERTER))
        .isInstanceOf(OpenSearchStatusException.class)
        .hasMessage("OpenSearch exception [type=index_not_found_exception, reason=no such index [unknownindex]]")
        .extracting(e -> ((OpenSearchStatusException) e).getIndex().getName())
        .isEqualTo("unknownindex");
  }

  @Test
  void testExecuteSingleQuery_noDataInIndex_emptyResults() throws IOException {
    SearchQuery searchQuery = createMatchAllQuery(false);

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(0, results.size());
  }

  @Test
  void testExecuteSingleQuery_hasDataDoesNotExceedMaxSearchResults_returnAllMatches() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createMatchAllQuery(true);

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(2, results.size());
    validate(results.get(0), "pid-3333333", 3333333, 2032);
    validate(results.get(1), "43c2aeb3-8971-496d-aca6-66f06ee46067", 123456, 2018);
  }

  @Test
  void testExecuteSingleQuery_noScore_returnNull() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createMatchAllQuery(true);

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(2, results.size());
    assertNull(results.get(0).getEsScore());
    assertNull(results.get(1).getEsScore());
  }

  @Test
  void testExecuteSingleQuery_withScore_returnNonNull() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createTermQuery("pid-3333333");

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(1, results.size());
    assertTrue(results.get(0).getEsScore() != null);
  }

  @Test
  void testExecuteSingleQuery_hasDataDoesExceedingMaxSearchResults_truncatesAtMaxResultsAndReturnOnlyFirst2Results() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_3_data_items_exceeds_max_results_window.json");
    SearchQuery searchQuery = createMatchAllQuery(true);

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(2, results.size());
    validate(results.get(0), "pid-3333333", 3333333, 2032);
    validate(results.get(1), "43c2aeb3-8971-496d-aca6-66f06ee46067", 123456, 2018);
  }

  @Test
  void testExecuteScrollQuery_noDataInIndex_emptyResults() throws IOException {
    SearchQuery searchQuery = createMatchAllQuery(false);

    List<SeriesMetadata> results = subject.executeQueryWithScroll(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(0, results.size());
  }

  @Test
  void testExecuteScrollQuery_hasDataDoesNotExceedMaxSearchResults_returnAllMatches() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createMatchAllQuery(true);

    List<SeriesMetadata> results = subject.executeQueryWithScroll(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(2, results.size());
    validate(results.get(0), "pid-3333333", 3333333, 2032);
    validate(results.get(1), "43c2aeb3-8971-496d-aca6-66f06ee46067", 123456, 2018);
  }

  @Test
  void testExecuteScrollQuery_hasDataDoesExceedingMaxSearchResults_returnAllMatches() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_3_data_items_exceeds_max_results_window.json");
    SearchQuery searchQuery = createMatchAllQuery(true);

    List<SeriesMetadata> results = subject.executeQueryWithScroll(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(3, results.size());
    validate(results.get(0), "pid-3333333", 3333333, 2032);
    validate(results.get(1), "43c2aeb3-8971-496d-aca6-66f06ee46067", 123456, 2018);
    validate(results.get(2), "b90a51b3-5f4a-4f8e-99fc-2bbaf7011710", 4164454710L, 2003);
  }

  @Test
  void testExecuteScrollQueryWithListener_noDataInIndex_emptyResults() throws IOException {
    SearchQuery searchQuery = createMatchAllQuery(false);

    AtomicInteger count = new AtomicInteger(0);
    List<SeriesMetadata> results = new ArrayList<>();
    Consumer<List<SeriesMetadata>> listener = (esResults) -> {
      int scrollNumber = count.getAndIncrement();
      results.addAll(esResults);
    };

    subject.executeQueryWithScrollListener(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER, listener);

    assertEquals(0, results.size());
  }

  @Test
  void testExecuteScrollQueryWithListener_hasDataDoesNotExceedingMaxSearchResult_returnAllMatches() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createMatchAllQuery(true);
    AtomicInteger count = new AtomicInteger(0);
    List<SeriesMetadata> results = new ArrayList<>();
    Consumer<List<SeriesMetadata>> listener = (esResults) -> {
      int scrollNumber = count.getAndIncrement();
      results.addAll(esResults);
    };

    subject.executeQueryWithScrollListener(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER, listener);

    assertEquals(1, count.get());
    validate(results.get(0), "pid-3333333", 3333333, 2032);
    validate(results.get(1), "43c2aeb3-8971-496d-aca6-66f06ee46067", 123456, 2018);
  }

  @Test
  void testExecuteScrollQueryWithListener_hasDataDoesExceedingMaxSearchResult_returnAllMatches() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_3_data_items_exceeds_max_results_window.json");
    SearchQuery searchQuery = createMatchAllQuery(true);
    List<SeriesMetadata> results = new ArrayList<>();
    AtomicInteger count = new AtomicInteger(0);
    Consumer<List<SeriesMetadata>> listener = (esResults) -> {
      int scrollNumber = count.getAndIncrement();
      results.addAll(esResults);
    };

    subject.executeQueryWithScrollListener(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER, listener);

    assertEquals(2, count.get());
    validate(results.get(0), "pid-3333333", 3333333, 2032);
    validate(results.get(1), "43c2aeb3-8971-496d-aca6-66f06ee46067", 123456, 2018);
    validate(results.get(2), "b90a51b3-5f4a-4f8e-99fc-2bbaf7011710", 4164454710L, 2003);
  }

  @Test
  void testExecuteAggregationQuery_noIndex_throwsRuntimeException() throws Exception {
    AggregationQuery aggregationQuery = createAggregationQuery(createHistogramOnUpdateOnField(), null);
    HistogramAggregationResultsConverter converter = new HistogramAggregationResultsConverter();

    Assertions.assertThatThrownBy(() -> subject.executeAggregateQuery(() -> "unknownindex", aggregationQuery, converter))
        .isInstanceOf(OpenSearchStatusException.class)
        .hasMessage("OpenSearch exception [type=index_not_found_exception, reason=no such index [unknownindex]]")
        .extracting(e -> ((OpenSearchStatusException) e).getIndex().getName())
        .isEqualTo("unknownindex");
  }

  @Test
  void testExecuteAggregationQuery_noDataInIndex() throws Exception {
    AggregationQuery aggregationQuery = createAggregationQuery(createHistogramOnUpdateOnField(), null);
    HistogramAggregationResultsConverter converter = new HistogramAggregationResultsConverter();

    Optional<HistogramResult> histogramResult = subject.executeAggregateQuery(SERIES_METADATA_INDEX, aggregationQuery, converter);

    assertTrue(histogramResult.isEmpty());
  }

  @Test
  void testExecuteAggregationQuery_noDataMatchingQuery() throws Exception {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    AggregationQuery aggregationQuery = createAggregationQuery(createHistogramOnUpdateOnField(), QueryBuilders.boolQuery()
        .must(QueryBuilders.termQuery("catalog_state", "non_existent_value"))
    );
    HistogramAggregationResultsConverter converter = new HistogramAggregationResultsConverter();

    Optional<HistogramResult> histogramResult = subject.executeAggregateQuery(SERIES_METADATA_INDEX, aggregationQuery, converter);

    assertTrue(histogramResult.isEmpty());
  }

  @Test
  void testExecuteAggregationQuery_histogramWithQueryConditionMatchingSubsetOfData_singleBucket() throws Exception {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_3_data_items_exceeds_max_results_window.json");
    AggregationQuery aggregationQuery = createAggregationQuery(createHistogramOnUpdateOnField(), QueryBuilders.boolQuery()
        .must(QueryBuilders.termQuery("catalog_state", "client_ready"))
    );
    HistogramAggregationResultsConverter converter = new HistogramAggregationResultsConverter();

    Optional<HistogramResult> histogramResult = subject.executeAggregateQuery(SERIES_METADATA_INDEX, aggregationQuery, converter);

    assertTrue(histogramResult.isPresent());
    assertEquals(1, histogramResult.get().getTotalBuckets());
    assertEquals(1, histogramResult.get().getKeyToDocCountsMap().size());
    assertEquals(2, histogramResult.get().getKeyToDocCountsMap().get("2022-06-10"));
  }

  @Test
  void testExecuteAggregationQuery_histogramWithNoQueryCondition_multipleBuckets() throws Exception {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_3_data_items_exceeds_max_results_window.json");
    AggregationQuery aggregationQuery = createAggregationQuery(createHistogramOnUpdateOnField(), null);
    HistogramAggregationResultsConverter converter = new HistogramAggregationResultsConverter();

    Optional<HistogramResult> histogramResult = subject.executeAggregateQuery(SERIES_METADATA_INDEX, aggregationQuery, converter);

    assertTrue(histogramResult.isPresent());
    assertEquals(3, histogramResult.get().getTotalBuckets());
    assertEquals(3, histogramResult.get().getKeyToDocCountsMap().size());
    assertEquals(1, histogramResult.get().getKeyToDocCountsMap().get("2022-06-08"));
    assertEquals(0, histogramResult.get().getKeyToDocCountsMap().get("2022-06-09"));
    assertEquals(2, histogramResult.get().getKeyToDocCountsMap().get("2022-06-10"));
  }

  @Test
  void testExecuteAggregationQuery_withSubAggregations_multipleBuckets() throws Exception {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_3_data_items_exceeds_max_results_window.json");
    TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders
        .terms("catalog_state_terms")
        .field("catalog_state")
        .subAggregation(AggregationBuilders.max("total_seasons_max").field("data.total_seasons"));
    AggregationQuery aggregationQuery = createAggregationQuery(termsAggregationBuilder, null);
    TermsWithSubAggregationResultsConverter converter = new TermsWithSubAggregationResultsConverter();

    Optional<TermsAggregationResult> termsWithSubAggregationResult = subject.executeAggregateQuery(SERIES_METADATA_INDEX, aggregationQuery, converter);

    assertTrue(termsWithSubAggregationResult.isPresent());
    assertEquals(2, termsWithSubAggregationResult.get().getTotalBuckets());
    assertEquals(2, termsWithSubAggregationResult.get().getKeyToStatsMap().size());
    assertEquals(2, termsWithSubAggregationResult.get().getKeyToStatsMap().get("client_ready").getDocCount());
    assertEquals(2, termsWithSubAggregationResult.get().getKeyToStatsMap().get("client_ready").getMaxSeasons());
    assertEquals(1, termsWithSubAggregationResult.get().getKeyToStatsMap().get("metadata_ready").getDocCount());
    assertEquals(6, termsWithSubAggregationResult.get().getKeyToStatsMap().get("metadata_ready").getMaxSeasons());
  }

  @Test
  void testExecuteMultipleMatchQuery_allWordMatched_returnAllMatchesResult() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createMultipleMatchQuery("let us try asia", Type.BEST_FIELDS, "title.keyword", "data.short_description");

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(1, results.size());
    validate(results.get(0), "pid-3333333", 3333333, 2032);
  }

  @Test
  void testExecuteMultipleMatchQuery_partialWordMatchedForBestFiled_returnNothing() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createMultipleMatchQuery("asia", Type.BEST_FIELDS, "title", "data.short_description");

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(2, results.size());
    validate(results.get(0), "43c2aeb3-8971-496d-aca6-66f06ee46067", 123456, 2018);
    validate(results.get(1), "pid-3333333", 3333333, 2032);
  }

  @Test
  void testExecuteMultipleMatchQuery_meetAnotherField_returnNothing() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createMultipleMatchQuery("test", Type.BEST_FIELDS, "title", "data.short_description");

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(1, results.size());
    validate(results.get(0), "pid-3333333", 3333333, 2032);
  }

  @Test
  void testExecuteMultipleMatchQuery_partialWordMatched_returnAllMatchesResult() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createMultipleMatchQuery("asia", Type.BEST_FIELDS, "title",
        "title._2gram",
        "title._3gram",
        "title_sanatised",
        "title_sanatised._2gram",
        "title_sanatised._3gram");

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(2, results.size());
    validate(results.get(0), "43c2aeb3-8971-496d-aca6-66f06ee46067", 123456, 2018);
    validate(results.get(1), "pid-3333333", 3333333, 2032);
  }

  @Test
  void testExecuteFunctionScoreQuery_allWordMatched_returnAllMatchesResult() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createFunctionScoreQuery("pid-3333333");

    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(1, results.size());
    assertTrue(results.get(0).getEsScore() != null);
  }

  @Test
  void testExecuteMultipleMatchFunctionScoreQuery_allWordMatched_returnAllMatchesResult() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchQuery searchQuery = createFunctionScoreMultipleMatchQuery("let us try asia", Type.BEST_FIELDS, "title.keyword", "data.short_description");
    List<SeriesMetadata> results = subject.executeSingleQuery(SERIES_METADATA_INDEX, searchQuery, RESULT_CONVERTER);

    assertEquals(1, results.size());
    validate(results.get(0), "pid-3333333", 3333333, 2032);
  }

  private AggregationQuery createAggregationQuery(AggregationBuilder aggregation, BoolQueryBuilder query) {
    return AggregationQuery.builder()
        .aggregationQuery(aggregation)
        .boolQuery(query)
        .build();
  }

  private AggregationBuilder createHistogramOnUpdateOnField() {
    return AggregationBuilders.dateHistogram("updated_on_histogram")
        .field("updated_on")
        .format("yyyy-MM-dd")
        .calendarInterval(DateHistogramInterval.DAY);
  }

  private SearchQuery createTermQuery(String arthurId) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder.must(QueryBuilders.termQuery("arthur_id", arthurId));
    SearchQueryBuilder builder = SearchQuery.builder();

    return builder
        .boolQuery(boolQueryBuilder)
        .requestedFields(new String[]{"short_id.long"})
        .requestedSources(new String[]{"arthur_id", "data.release_year"})
        .build();
  }

  private SearchQuery createMatchAllQuery(boolean enableSortByReleaseYear) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder.must(QueryBuilders.matchAllQuery());
    SearchQueryBuilder builder = SearchQuery.builder();
    if (enableSortByReleaseYear) {
      builder = builder.sortCriteria(SortCriteria.builder()
          .sortField("data.release_year")
          .sortOrder(SortOrder.DESC)
          .build());
    }
    return builder
        .boolQuery(boolQueryBuilder)

        .requestedFields(new String[]{"short_id.long"})
        .requestedSources(new String[]{"arthur_id", "data.release_year"})
        .build();
  }

  private SearchQuery createFunctionScoreQuery(String arthurId) {

    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder.must(QueryBuilders.termQuery("arthur_id", arthurId));
    FunctionScoreQueryBuilder fqBuilder = QueryBuilders.functionScoreQuery(boolQueryBuilder)
        .scoreMode(ScoreMode.SUM);
    SearchQueryBuilder builder = SearchQuery.builder();
    return builder
        .functionScoreQueryBuilder(fqBuilder)
        .requestedFields(new String[]{"short_id.long"})
        .requestedSources(new String[]{"arthur_id", "data.release_year"})
        .build();
  }

  private SearchQuery createMultipleMatchQuery(String queryString, Type type, String... fieldNames) {
    Map<String, Float> filedBoosts = new HashMap<>();
    Arrays.stream(fieldNames).forEach(fieldName -> filedBoosts.put(fieldName, 1.0f));
    MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(queryString).fields(filedBoosts).type(type);
    multiMatchQueryBuilder.type(type);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder.should(multiMatchQueryBuilder);
    SearchQueryBuilder builder = SearchQuery.builder();
    return builder
        .boolQuery(boolQueryBuilder)
        .requestedFields(new String[]{"short_id.long"})
        .requestedSources(new String[]{"arthur_id", "data.release_year"})
        .build();
  }

  private SearchQuery createFunctionScoreMultipleMatchQuery(String queryString, Type type, String... fieldNames) {
    Map<String, Float> filedBoosts = new HashMap<>();
    Arrays.stream(fieldNames).forEach(fieldName -> filedBoosts.put(fieldName, 1.0f));
    MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(queryString).fields(filedBoosts).type(type);
    multiMatchQueryBuilder.type(type);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder.should(multiMatchQueryBuilder);
    FunctionScoreQueryBuilder fqBuilder = QueryBuilders.functionScoreQuery(boolQueryBuilder)
        .scoreMode(ScoreMode.SUM);

    SearchQueryBuilder builder = SearchQuery.builder();
    return builder
        .functionScoreQueryBuilder(fqBuilder)
        .requestedFields(new String[]{"short_id.long"})
        .requestedSources(new String[]{"arthur_id", "data.release_year"})
        .build();
  }

  private SearchQuery createMultipleMatchQuery(String queryString, Type type, int fuzziness, String... fieldNames) {
    Map<String, Float> filedBoosts = new HashMap<>();
    Arrays.stream(fieldNames).forEach(fieldName -> filedBoosts.put(fieldName, 1.0f));
    MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(queryString).fields(filedBoosts).type(type).fuzziness(fuzziness);
    multiMatchQueryBuilder.type(type);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder.should(multiMatchQueryBuilder);
    SearchQueryBuilder builder = SearchQuery.builder();
    return builder
        .boolQuery(boolQueryBuilder)
        .requestedFields(new String[]{"short_id.long"})
        .requestedSources(new String[]{"arthur_id", "data.release_year"})
        .build();
  }

  private void validate(SeriesMetadata actual, String expectedarthurId, long expectedShortId, int expectedReleaseYear) {
    assertEquals(expectedarthurId, actual.getarthurId());
    assertEquals(expectedShortId, actual.getShortId());
    assertEquals(expectedReleaseYear, actual.getReleaseYear());
  }
}