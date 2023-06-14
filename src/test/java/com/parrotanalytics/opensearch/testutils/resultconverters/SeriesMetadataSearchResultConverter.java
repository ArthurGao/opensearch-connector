package com.arthur.opensearch.testutils.resultconverters;

import com.google.gson.JsonObject;
import com.arthur.common.utils.json.JsonUtils;
import com.arthur.opensearch.resultconverters.AbstractSearchResultsConverter;
import com.arthur.opensearch.testutils.SeriesMetadata;
import java.util.Optional;

public class SeriesMetadataSearchResultConverter extends AbstractSearchResultsConverter<SeriesMetadata> {

  @Override
  public SeriesMetadata convert(JsonObject sourceResult, JsonObject fieldsResult, Optional<JsonObject> highlightFields, Optional<Double> score) {
    return SeriesMetadata.builder()
        .arthurId(JsonUtils.getString(sourceResult, "arthur_id"))
        .shortId(fieldsResult.getAsJsonArray("short_id.long").get(0).getAsLong())
        .releaseYear(JsonUtils.getInt(sourceResult, "data.release_year"))
        .esScore(score.orElse(null))
        .build();
  }
}
