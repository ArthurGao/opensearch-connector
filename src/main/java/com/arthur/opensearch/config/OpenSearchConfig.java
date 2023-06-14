package com.arthur.opensearch.config;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class OpenSearchConfig {
  private final String hostUrl;

  private final int defaultScrollTimeoutInSeconds;
  private final int maxSearchResultsSize;
}
