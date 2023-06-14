package com.arthur.opensearch.model;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.sort.SortOrder;

@Builder
@Getter
public class SearchQuery {

  private final BoolQueryBuilder boolQuery;
  private final FunctionScoreQueryBuilder functionScoreQueryBuilder;
  private final String[] requestedSources;
  private final String[] requestedFields;
  // optional
  private final SortCriteria sortCriteria;
  private final HighlightBuilder highlightBuilder;

  @Builder
  @Getter
  public static class SortCriteria {

    private final String sortField;
    private final SortOrder sortOrder;
  }
}
