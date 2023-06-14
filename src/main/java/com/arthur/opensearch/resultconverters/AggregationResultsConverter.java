package com.arthur.opensearch.resultconverters;

import java.util.Optional;
import org.opensearch.search.aggregations.Aggregation;

public interface AggregationResultsConverter<AggregationResultType extends Aggregation, T> {

  /**
   * Converts aggregation results into a results object
   *
   * @param aggregationResult
   * @return {@link Optional<T>} that is either (a) empty if no results were found for the aggregate, or (b) non-empty if results were found for the aggregate
   * query.
   */
  Optional<T> convert(AggregationResultType aggregationResult);
}
