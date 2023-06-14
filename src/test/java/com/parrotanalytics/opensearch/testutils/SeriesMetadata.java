package com.arthur.opensearch.testutils;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SeriesMetadata {
  private String arthurId;
  private long shortId;
  private int releaseYear;
  private Double esScore;
}
