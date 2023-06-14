package com.arthur.opensearch.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.search.aggregations.AggregationBuilder;

@Builder
@Getter
public class AggregationQuery {

  private final BoolQueryBuilder boolQuery;
  @NonNull
  private final AggregationBuilder aggregationQuery;
}
