package com.arthur.opensearch.testutils.resultconverters;

import com.arthur.opensearch.resultconverters.AbstractMultiBucketsAggregationResultsConverter;
import com.arthur.opensearch.testutils.resultconverters.HistogramAggregationResultsConverter.HistogramResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.opensearch.search.aggregations.bucket.histogram.Histogram;

public class HistogramAggregationResultsConverter extends AbstractMultiBucketsAggregationResultsConverter<Histogram, HistogramResult> {


  /**
   * Example top-level aggregation response for a histogram aggregation request:
   *
   * <pre>
   *     "aggregations" : {
   *     "updated_on_histogram" : {
   *       "buckets" : [
   *         {
   *           "key_as_string" : "2022-10-17T00:00:00.000Z",
   *           "key" : 1665964800000,
   *           "doc_count" : 21893
   *         },
   *         {
   *           "key_as_string" : "2022-10-18T00:00:00.000Z",
   *           "key" : 1666051200000,
   *           "doc_count" : 6
   *         }
   *       ]
   *     }
   *   }
   * </pre>
   *
   * @param buckets
   * @return {@link HistogramResult}
   */
  @Override
  protected HistogramResult parseAggregationResults(List<? extends MultiBucketsAggregation.Bucket> buckets) {
    final Map<String, Long> keyToDocCountsMap = buckets.stream()
        .collect(Collectors.toMap(bucket -> bucket.getKeyAsString(), bucket -> bucket.getDocCount()));
    return HistogramResult.builder()
        .totalBuckets(buckets.size())
        .keyToDocCountsMap(keyToDocCountsMap)
        .build();
  }

  @Builder
  @Getter
  public static final class HistogramResult {

    private final int totalBuckets;
    private final Map<String, Long> keyToDocCountsMap;
  }
}

