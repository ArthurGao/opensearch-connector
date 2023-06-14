package com.arthur.opensearch.testutils;

import static com.arthur.opensearch.testutils.OpenSearchExtention.OPEN_SEARCH_PORT;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.arthur.opensearch.model.Index;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpHost;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.flush.FlushRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.Request;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.rest.RestStatus;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

@Log4j2
public class OpenSearchTestHelper {

  /*
   * Value of the `index.max_result_window` (maximum results returned by search)
   *
   * @see https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html#dynamic-index-settings
   */
  public static final int MAX_RESULTS_SIZE = 2;

  private static RestHighLevelClient client = new RestHighLevelClient(
      RestClient.builder(HttpHost.create(String.format("http://localhost:%d", OPEN_SEARCH_PORT)))
  );

  public static void createIndex(Index index, String indexResourceName) throws IOException {
    String mappingsJson = readFromResources(indexResourceName);

    CreateIndexRequest request = new CreateIndexRequest(index.getIndexName());
    request.mapping(mappingsJson, XContentType.JSON);
    request.settings(Settings.builder()
        .put("index.max_result_window", MAX_RESULTS_SIZE)
        .build());
    CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
    RestStatus status = forceRefreshIndexChanges();
    log.info("Index {} created? {} [status: {}] [shards ready: {}].", index.getIndexName(), response.isAcknowledged(), status, response.isShardsAcknowledged());
  }

  public static void createSearchTemplate(String scriptName) throws IOException {
    RestClient restClient = RestClient.builder(new HttpHost("localhost", OPEN_SEARCH_PORT, "http")).build();
    Request scriptRequest = new Request("POST", "_scripts/" + scriptName);
    scriptRequest.setJsonEntity(
        "{\n"
            + "  \"script\": {\n"
            + "    \"lang\": \"mustache\",\n"
            + "    \"source\": {\n"
            + "      \"query\": {\n"
            + "        \"term\": {\n"
            + "          \"display_name.keyword\": \"{{query_string}}\"\n"
            + "        }\n"
            + "      },\n"
            + "      \"_source\": [\n"
            + "        \"arthur_id\",\n"
            + "        \"data.release_year\"\n"
            + "      ],  \"fields\": [\"short_id.long\"],"
            + "\"size\": 2\n"
            + "    }\n"
            + "  }\n"
            + "}");
    restClient.performRequest(scriptRequest);
  }

  public static void deleteIndex(Index index) throws IOException {
    DeleteIndexRequest request = new DeleteIndexRequest(index.getIndexName());
    AcknowledgedResponse response = client.indices().delete(request, RequestOptions.DEFAULT);
    RestStatus status = forceRefreshIndexChanges();
    log.info("Deleted index {}? {} [status: {}].", index.getIndexName(), response.isAcknowledged(), status);
  }

  public static void addData(Index index, String jsonDataArrayResourceName) throws IOException {
    JsonArray dataJson = JsonParser.parseString(readFromResources(jsonDataArrayResourceName)).getAsJsonArray();
    log.info("Adding {} entities to add to index {}.", dataJson.size(), index.getIndexName());
    dataJson.forEach(dataToAdd -> addEntity(dataToAdd, index));
    forceRefreshIndexChanges();
  }

  public static void deleteAllData(Index index) throws IOException {
    DeleteByQueryRequest request = new DeleteByQueryRequest(index.getIndexName());
    request.setBatchSize(MAX_RESULTS_SIZE);
    request.setQuery(QueryBuilders.matchAllQuery());
    BulkByScrollResponse response = client.deleteByQuery(request, RequestOptions.DEFAULT);
    RestStatus status = forceRefreshIndexChanges();
    log.info("Delete {} docs from {} [count: {}, status: {}].", response.getDeleted(), index.getIndexName(), response.getStatus().getDeleted(), status);
  }

  private static void addEntity(JsonElement dataToAdd, Index index) {
    IndexRequest request = new IndexRequest(index.getIndexName());
    request.id(dataToAdd.getAsJsonObject().getAsJsonPrimitive("arthur_id").getAsString());
    request.source(dataToAdd.toString(), XContentType.JSON);
    try {
      IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT);
      client.indices().flush(new FlushRequest(), RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new RuntimeException("Failed to index document " + request.id() + " to index " + index.getIndexName(), e);
    }
  }

  private static RestStatus forceRefreshIndexChanges() throws IOException {
    return client.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT).getStatus();
  }

  private static String readFromResources(String fileName) throws IOException {
    try (InputStream is = OpenSearchTestHelper.class.getClassLoader().getResourceAsStream(fileName)) {
      return IOUtils.toString(is, Charset.forName("UTF-8"));
    }
  }
}
