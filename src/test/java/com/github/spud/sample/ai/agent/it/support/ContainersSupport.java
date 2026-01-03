package com.github.spud.sample.ai.agent.it.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers support for integration tests
 * Provides production-like Postgres (with pgvector) and Redis
 */
@Slf4j
public abstract class ContainersSupport {

  // Static containers shared across all tests (start once per JVM)
  private static final PostgreSQLContainer<?> POSTGRES;
  private static final GenericContainer<?> REDIS;

  static {
    // Postgres with pgvector extension (same as docker-compose)
    POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg18-trixie"))
      .withDatabaseName("agent_test")
      .withUsername("agent_test")
      .withPassword("agent_test")
      .withReuse(true);

    // Redis (same as docker-compose)
    REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.4.0"))
      .withExposedPorts(6379)
      .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
      .withReuse(true);

    // Start containers
    log.info("Starting Testcontainers: Postgres + Redis");
    POSTGRES.start();
    REDIS.start();
    log.info("Testcontainers started: Postgres at {}, Redis at {}:{}",
      POSTGRES.getJdbcUrl(), REDIS.getHost(), REDIS.getFirstMappedPort());
  }

  /**
   * Inject container properties and MockWebServer URL into Spring environment
   */
  @DynamicPropertySource
  static void configureContainers(DynamicPropertyRegistry registry) {
    // Postgres
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);

    // Redis
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getFirstMappedPort().toString());

    // MockWebServer for OpenAI (get URL without trailing slash for Spring AI)
    // Spring AI will append /v1/chat/completions
    String mockServerUrl = OpenAiMockSupport.getServer().url("").toString();
    // Remove trailing slash if present
    if (mockServerUrl.endsWith("/")) {
      mockServerUrl = mockServerUrl.substring(0, mockServerUrl.length() - 1);
    }
    final String finalUrl = mockServerUrl;
    registry.add("spring.ai.openai.base-url", () -> finalUrl);

    log.info("Injected container properties: DB={}, Redis={}:{}, OpenAI MockServer={}",
      POSTGRES.getJdbcUrl(), REDIS.getHost(), REDIS.getFirstMappedPort(), finalUrl);
  }

  /**
   * Get Postgres container for advanced queries in tests
   */
  public static PostgreSQLContainer<?> getPostgres() {
    return POSTGRES;
  }

  /**
   * Get Redis container for advanced operations in tests
   */
  public static GenericContainer<?> getRedis() {
    return REDIS;
  }
}
