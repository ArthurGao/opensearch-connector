package com.arthur.opensearch.client;

import static com.arthur.opensearch.testutils.OpenSearchExtention.OPEN_SEARCH_PORT;
import static com.arthur.opensearch.testutils.OpenSearchTestHelper.MAX_RESULTS_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.arthur.opensearch.config.OpenSearchConfig;
import com.arthur.opensearch.model.Index;
import com.arthur.opensearch.model.SearchTemplateRequestBuilder;
import com.arthur.opensearch.testutils.OpenSearchExtention;
import com.arthur.opensearch.testutils.OpenSearchTestHelper;
import com.arthur.opensearch.testutils.SeriesMetadata;
import com.arthur.opensearch.testutils.SeriesMetadataIndex;
import com.arthur.opensearch.testutils.resultconverters.SeriesMetadataSearchResultConverter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.script.mustache.SearchTemplateRequest;
import org.testcontainers.junit.jupiter.Testcontainers;

@ExtendWith(OpenSearchExtention.class)
@Testcontainers
public class OpenSearchTemplateITest {
  private static final Index SERIES_METADATA_INDEX = new SeriesMetadataIndex();
  private static final String SEARCH_TEMPLATE_NAME = "seria_metadata_search_template";

  private static final OpenSearchConfig OPEN_SEARCH_CONFIG = OpenSearchConfig.builder()
      .maxSearchResultsSize(MAX_RESULTS_SIZE)
      .defaultScrollTimeoutInSeconds(2)
      .hostUrl("http://localhost:" + OPEN_SEARCH_PORT)
      .build();
  private static final SeriesMetadataSearchResultConverter RESULT_CONVERTER = new SeriesMetadataSearchResultConverter();

  private OpenSearchRESTClient subject;

  @BeforeAll
  public static void setupOpenSearch() throws IOException {
    OpenSearchTestHelper.createIndex(SERIES_METADATA_INDEX, "series_metadata_index.json");
    OpenSearchTestHelper.createSearchTemplate(SEARCH_TEMPLATE_NAME);
  }

  @AfterAll
  public static void deleteIndex() throws IOException {
    OpenSearchTestHelper.deleteIndex(SERIES_METADATA_INDEX);
  }

  @BeforeEach
  public void setUp() {
    subject = new OpenSearchRESTClient(OPEN_SEARCH_CONFIG);
    subject.init();
  }

  @AfterEach
  public void removeData() throws IOException {
    //OpenSearchTestHelper.deleteAllData(SERIES_METADATA_INDEX);
  }

  @Test
  public void testExecuteTemplateQuery_givenValidInput_getResultExpected() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    Map<String, Object> params = new HashMap<>();
    params.put("query_string", "Do NOT Try Asia");
    SearchTemplateRequest request = SearchTemplateRequestBuilder.createSearchTemplateRequest(SERIES_METADATA_INDEX.getIndexName(), SEARCH_TEMPLATE_NAME, params);
    List<SeriesMetadata> results = subject.executeTemplateQuery(request, RESULT_CONVERTER);
    assertThat(results).hasSize(1);
    validate(results.get(0), "pid-3333333", 3333333, 2032);
  }

  @Test
  public void testExecuteTemplateQuery_givenInputUnmatched_getNoContent() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    Map<String, Object> params = new HashMap<>();
    params.put("query_string", "Nomatched");
    SearchTemplateRequest request = SearchTemplateRequestBuilder.createSearchTemplateRequest(SERIES_METADATA_INDEX.getIndexName(), SEARCH_TEMPLATE_NAME, params);
    List<SeriesMetadata> results = subject.executeTemplateQuery(request, RESULT_CONVERTER);
    assertThat(results).hasSize(0);
  }

  @Test
  public void testExecuteTemplateQuery_givenNoParams_getNoContent() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    Map<String, Object> params = new HashMap<>();
    SearchTemplateRequest request = SearchTemplateRequestBuilder.createSearchTemplateRequest(SERIES_METADATA_INDEX.getIndexName(), SEARCH_TEMPLATE_NAME, params);
    List<SeriesMetadata> results = subject.executeTemplateQuery(request, RESULT_CONVERTER);
    assertThat(results).hasSize(0);
  }

  @Test
  public void testExecuteTemplateQuery_givenNullParams_getNoContent() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    SearchTemplateRequest request = SearchTemplateRequestBuilder.createSearchTemplateRequest(SERIES_METADATA_INDEX.getIndexName(), SEARCH_TEMPLATE_NAME, null);
    assertThatExceptionOfType(OpenSearchStatusException.class)
        .isThrownBy(() -> {
          subject.executeTemplateQuery(request, RESULT_CONVERTER);
        }).withMessageContaining("params doesn't support values of type");
  }

  @Test
  public void testExecuteTemplateQuery_givenNullIndexName_getNoContent() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    Map<String, Object> params = new HashMap<>();
    params.put("query_string", "Nomatched");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> SearchTemplateRequestBuilder.createSearchTemplateRequest(null, SEARCH_TEMPLATE_NAME, params)).withMessageContaining("IndexName, titleName must not be null");
  }

  @Test
  public void testExecuteTemplateQuery_givenNullScriptName_getNoContent() throws IOException {
    OpenSearchTestHelper.addData(SERIES_METADATA_INDEX, "series_metadata_2_data_items.json");
    Map<String, Object> params = new HashMap<>();
    params.put("query_string", "Nomatched");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> SearchTemplateRequestBuilder.createSearchTemplateRequest(SERIES_METADATA_INDEX.getIndexName(), null, params)).withMessageContaining("IndexName, titleName must not be null");
  }

  private void validate(SeriesMetadata actual, String expectedarthurId, long expectedShortId, int expectedReleaseYear) {
    assertEquals(expectedarthurId, actual.getarthurId());
    assertEquals(expectedShortId, actual.getShortId());
    assertEquals(expectedReleaseYear, actual.getReleaseYear());
  }

}
