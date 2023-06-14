# Overview
Library to connect to OpenSearch and perform queries.

# How to use the library
## Maven Configuration
Include the latest version as a dependency:

E.g.
```
    <dependency>
        <groupId>com.arthur</groupId>
        <artifactId>opensearch-connector</artifactId>
        <version>1.0.0</version> <!-- Update version to the latest -->
    </dependency>
```

## 
### executeSingleQuery and executeQueryWithScroll
1. Define the Indexes that your application needs to interact with by extending the <code>com.arthur.model.Index</code> interface. E.g.
   ```java
    public class SeriesMetadataIndex implements Index {
      @Override
      public String getIndexName() {
        return "series_metadata";
      }
    }
   ```
    Usage:
   ```java
    private static final Index SERIES_METADATA_INDEX = new SeriesMetadataIndex();
    ```
2. Define a POJO to encapsulate the data you want to extract from the Index(es)
   ```java
    @Builder
    @Getter
    public class SeriesMetadata {
      private String arthurId;
      private long shortId;
      private int releaseYear;
    }
   ```
3. Define a <code>com.arthur.resultconverters.AbstractSearchResultsConverter</code> to parse the results from OpenSearch into the POJOs (E.g. `SeriesMetadata` defined in above as an example)
   ```java
   public class SeriesMetadataSearchResultConverter extends AbstractSearchResultsConverter<SeriesMetadata> {

       @Override
       public SeriesMetadata convert(JsonObject sourceResult, JsonObject fieldsResult) {
          return SeriesMetadata.builder()
            .arthurId(JsonUtils.getString(sourceResult, "arthur_id"))
            .shortId(fieldsResult.getAsJsonArray("short_id.long").get(0).getAsLong())
            .releaseYear(JsonUtils.getInt(sourceResult, "data.release_year"))
            .build();
       }
   }
   ```
4. Define the configuration to connect to OpenSearch cluster
   ```
   OpenSearchConfig OPEN_SEARCH_CONFIG = OpenSearchConfig.builder()
      .maxSearchResultsSize(10000)
      .defaultScrollTimeoutInSeconds(2)
      .hostUrl("http://localhost:9200") // "https://vpc.pa-apps-es-prod.aws.com"
      .build();
   ```
5. Use <code>com.arthur.client.OpenSearchRESTClient</code> to perform the queries and obtain results
   ```
   private void doSearch() {
      SeriesMetadataSearchResultConverter converter = new SeriesMetadataSearchResultConverter();
      OpenSearchRESTClient client = new OpenSearchRESTClient(OPEN_SEARCH_CONFIG);
      client.init();
      SearchQuery searchQuery = createMatchAllSearchQuery(true);
      List<SeriesMetadata> results = client.executeQueryWithScroll(SERIES_METADATA_INDEX, searchQuery, converter);

   }
   
   private SearchQuery createMatchAllSearchQuery(boolean enableSortByReleaseYear) {
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
   ```
   * **NOTE**: The `OpenSearchRESTClient#init` method is separated out from the constructor to ensure Spring dependency injection step
     (in Spring lifecycle) can complete faster. Also this will allow us to make the connection asynchronous in order to provide better resiliency in distributed
     systems without having to force consumers of the library to make significant code changes.
     ```java
     @Configuration
     public class MyConfiguration {
        @Bean(initMethod = "init", destroyMethod = "close")
        public OpenSearchRESTClient openSearchRESTClient(OpenSearchConfig config) {
           return new OpenSearchRESTClient(config);
        }
     }
     ```

### Execute search template

1. Define a POJO to encapsulate the data you want to extract from the Index(es) (Same with executeSingleQuery and executeQueryWithScroll)
2. Define a <code>com.arthur.resultconverters.AbstractSearchResultsConverter</code> to parse the results from OpenSearch into the POJOs (Same with
   executeSingleQuery and executeQueryWithScroll)
3. Define the configuration to connect to OpenSearch cluster (Same with executeSingleQuery and executeQueryWithScroll)
4. Registered a search template to OpenSearch via Postman (https://www.elastic.co/guide/en/elasticsearch/reference/current/search-template.html)
5. Use <code>com.arthur.client.OpenSearchRESTClient</code> and <code>com.arthur.opensearch.model.SearchTemplateRequestBuilder</code>  to
   perform the queries and obtain results Create a SearchTemplateRequest and call

```
    SearchTemplateRequest request=SearchTemplateRequestBuilder.createSearchTemplateRequest(SERIES_METADATA_INDEX.getIndexName(),SEARCH_TEMPLATE_NAME,params);
    List<SeriesMetadata> results = subject.executeTemplateQuery(request, RESULT_CONVERTER);
```

# Features

The library support the following features:

1. Ability to get maximum of `10000` results via `OpenSearchRESTClient#executeSingleQuery`
2. Ability to get ALL the results for a query via `OpenSearchRESTClient#executeQueryWithScroll`
3. Ability to return aggregation results for a query via `OpenSearchRESTClient#executeAggregateQuery`
4. Ability to execute search template

## Future Improvements

* Add support for `from` and `to` queries (needs to be careful with limits as this scans the full data set - not suitable with queries that can return lot of
  data) - see https://medium.com/everything-full-stack/elasticsearch-scroll-search-e92eb29bf773
* Add support for `searchAfter` - see https://medium.com/everything-full-stack/elasticsearch-scroll-search-e92eb29bf773
* Add support for scrollable aggregations (
  see https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-terms-aggregation.html#_filtering_values_with_partitions)
  E.g.
  ```
    "include": {
      "partition": 3,
      "num_partitions": 3
    }
  ```
* Add support for more than 1 top-level aggregation.

## Limitations
1. `OpenSearchRESTClient#executeSingleQuery` will only return the results that fit the maximum results size (`10000` by default) if the query has more results. \
   In such cases, there will be an error log message to inform applications to make necessary changes (E.g. either refine the query or use `executeQueryWithScroll`)