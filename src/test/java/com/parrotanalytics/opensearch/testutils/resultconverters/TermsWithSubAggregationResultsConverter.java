package com.arthur.opensearch.testutils.resultconverters;

import com.arthur.opensearch.resultconverters.AbstractMultiBucketsAggregationResultsConverter;
import com.arthur.opensearch.testutils.resultconverters.TermsWithSubAggregationResultsConverter.TermsAggregationResult;
import com.arthur.opensearch.testutils.resultconverters.TermsWithSubAggregationResultsConverter.TermsAggregationResult.CatalogStateStats;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.Max;

public class TermsWithSubAggregationResultsConverter extends AbstractMultiBucketsAggregationResultsConverter<Terms, TermsAggregationResult> {


  /**
   * <pre>
   *    "aggregations": {
   *     "sterms#catalog_state_terms": {
   *       "doc_count_error_upper_bound": 0,
   *       "sum_other_doc_count": 0,
   *       "buckets": [
   *         {
   *           "key": "client_ready",
   *           "doc_count": 2,
   *           "max#total_seasons_max": {
   *             "value": null
   *           }
   *         },
   *         {
   *           "key": "metadata_ready",
   *           "doc_count": 1,
   *           "max#total_seasons_max": {
   *             "value": null
   *           }
   *         }
   *       ]
   *     }
   *   }
   * </pre>
   *
   * @param buckets
   * @return
   */
  @Override
  protected TermsAggregationResult parseAggregationResults(List<? extends Bucket> buckets) {
    Map<String, CatalogStateStats> bucketAndStatsMap = buckets.stream()
        .collect(Collectors.toMap(bucket -> bucket.getKeyAsString(), bucket -> createCatalogStateStats(bucket)));
    return TermsAggregationResult.builder()
        .totalBuckets(buckets.size())
        .keyToStatsMap(bucketAndStatsMap)
        .build();
  }

  private CatalogStateStats createCatalogStateStats(Bucket bucket) {
    // get max count using the name for the sub-aggregation given in the aggs query that produced the results
    Max maxResult = bucket.getAggregations().get("total_seasons_max");
    return new CatalogStateStats((int) bucket.getDocCount(), maxResult.getValue());
  }

  @Builder
  @Getter
  public static final class TermsAggregationResult {

    private final int totalBuckets;
    private final Map<String, CatalogStateStats> keyToStatsMap;

    @Getter
    @AllArgsConstructor
    public static final class CatalogStateStats {

      private final int docCount;
      private final double maxSeasons;
    }
  }
}
