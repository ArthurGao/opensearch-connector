package com.arthur.opensearch.testutils;

import com.arthur.opensearch.model.Index;

public class SeriesMetadataIndex implements Index {

  @Override
  public String getIndexName() {
    return "series_metadata";
  }
}
