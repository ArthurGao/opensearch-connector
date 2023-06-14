package com.arthur.opensearch.resultconverters;

import com.google.gson.JsonObject;
import com.arthur.common.utils.json.JsonUtils;
import com.arthur.opensearch.model.SearchQuery;
import java.util.Optional;

public abstract class AbstractSearchResultsConverter<T> {

  public final T convert(JsonObject searchResult) {
    JsonObject sourceObj = searchResult.getAsJsonObject("_source");
    Optional<Double> score = Optional.ofNullable(JsonUtils.getOptDouble(searchResult, "_score"));
    JsonObject fieldObj = searchResult.getAsJsonObject("fields");
    Optional<JsonObject> highlightFieldsObj = Optional.ofNullable(searchResult.getAsJsonObject("highlight"));
    return convert(sourceObj, fieldObj, highlightFieldsObj, score);
  }

  /**
   * @param sourceResult    source data from <code>_source</code>
   * @param score    score data from <code>_score</code>
   * @param fieldsResult    field data (E.g. inner fields) from <code>fields</code>
   * @param highlightFields Optional highlighted field data from <code>highlight</code>. This will only be available if you specify the
   *                        {@link SearchQuery#getHighlightBuilder()} property to retrieve highlighted data from your query.
   * @return converted object with data
   * @see https://www.elastic.co/guide/en/elasticsearch/reference/current/highlighting.html
   */
  protected abstract T convert(JsonObject sourceResult, JsonObject fieldsResult, Optional<JsonObject> highlightFields, Optional<Double> score);
}