package com.arthur.opensearch.resultconverters;

import java.util.List;
import java.util.Optional;
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;

/**
 * Results converter for {@link MultiBucketsAggregation}s such as term, histogram aggregations etc.
 *
 * @param <AggregationResultType>
 * @param <T>
 */
public abstract class AbstractMultiBucketsAggregationResultsConverter<AggregationResultType extends MultiBucketsAggregation, T> implements
    AggregationResultsConverter<AggregationResultType, T> {

  @Override
  public final Optional<T> convert(AggregationResultType aggregationResult) {
    if (aggregationResult == null || aggregationResult.getBuckets().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(parseAggregationResults(aggregationResult.getBuckets()));
  }

  /**
   * @param buckets
   * @return a non-null result
   */
  protected abstract T parseAggregationResults(List<? extends Bucket> buckets);
}
