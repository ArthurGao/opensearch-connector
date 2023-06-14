package com.arthur.opensearch.testutils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.Base58;
import org.testcontainers.utility.DockerImageName;

@Log4j2
public class OpenSearchExtention implements BeforeAllCallback, AfterAllCallback {

  public static final int OPEN_SEARCH_PORT = 9200;

  static GenericContainer<?> opensearchContainer;

  static {
    opensearchContainer = new GenericContainer<>(
        DockerImageName.parse("opensearchproject/opensearch").withTag("2.0.0")
    );
    opensearchContainer.withReuse(true);
    opensearchContainer.withEnv("discovery.type", "single-node");
    opensearchContainer.withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true");
    opensearchContainer.withEnv("DISABLE_SECURITY_PLUGIN", "true");
    opensearchContainer.withNetworkAliases("opensearch-" + Base58.randomString(6));
    opensearchContainer.addExposedPorts(OPEN_SEARCH_PORT, 9300);
    opensearchContainer.getPortBindings().add("9200:9200/tcp");
    opensearchContainer.start();
    log.info("OpenSearch container starting...");
    Runtime.getRuntime()
        .addShutdownHook(new Thread(() -> {
          opensearchContainer.stop();
          log.info("OpenSearch container stopped.");
        }));
  }


  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    log.info("Checking for liveliness of the OpenSearch container");
    opensearchContainer.setWaitStrategy(
        (new HttpWaitStrategy()).forPort(OPEN_SEARCH_PORT)
            .forStatusCodeMatching((response) -> response == 200 || response == 401)
            .withStartupTimeout(Duration.ofMinutes(2L)));
    assertTrue(opensearchContainer.isRunning());
    log.info("OpenSearch container is up and running");
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    // do nothing - container will be stopped when the JVM terminates
  }
}
