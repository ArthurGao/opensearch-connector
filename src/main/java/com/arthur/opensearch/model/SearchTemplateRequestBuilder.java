package com.arthur.opensearch.model;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.script.ScriptType;
import org.opensearch.script.mustache.SearchTemplateRequest;

public class SearchTemplateRequestBuilder {
  private SearchTemplateRequestBuilder() {}

  public static SearchTemplateRequest createSearchTemplateRequest(String indexName, String titleName, Map<String, Object> params) {
    if(StringUtils.isBlank(indexName) || StringUtils.isBlank(titleName)) {
      throw new IllegalArgumentException("IndexName, titleName must not be null");
    }

    SearchTemplateRequest request = new SearchTemplateRequest();
    request.setRequest(new SearchRequest(indexName));
    request.setScriptType(ScriptType.STORED);
    request.setScript(titleName);
    if(params != null) {
      request.setScriptParams(params);
    }
    return request;
  }
}
