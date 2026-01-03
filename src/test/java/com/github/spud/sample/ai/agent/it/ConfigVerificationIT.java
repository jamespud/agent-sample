package com.github.spud.sample.ai.agent.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spud.sample.ai.agent.it.support.ContainersSupport;
import com.github.spud.sample.ai.agent.it.support.OpenAiMockSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verify that Testcontainers and MockWebServer properties are correctly injected
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
class ConfigVerificationIT extends ContainersSupport {

  @Value("${spring.datasource.url}")
  private String datasourceUrl;

  @Value("${spring.data.redis.host}")
  private String redisHost;

  @Value("${spring.ai.openai.base-url}")
  private String openaiBaseUrl;

  @Test
  void shouldInjectAllContainerProperties() {
    log.info("=== Configuration Verification ===");
    log.info("Datasource URL: {}", datasourceUrl);
    log.info("Redis Host: {}", redisHost);
    log.info("OpenAI Base URL: {}", openaiBaseUrl);
    log.info("MockWebServer actual URL: {}", OpenAiMockSupport.getServer().url("/"));

    // Verify properties are injected
    assertThat(datasourceUrl).contains("jdbc:postgresql://")
      .contains("agent_test");
    assertThat(redisHost).isNotEmpty();
    assertThat(openaiBaseUrl).isNotEmpty()
      .contains("http://")
      .contains("localhost");

    // Verify MockWebServer is accessible
    String mockUrl = OpenAiMockSupport.getServer().url("").toString();
    if (mockUrl.endsWith("/")) {
      mockUrl = mockUrl.substring(0, mockUrl.length() - 1);
    }
    assertThat(openaiBaseUrl).isEqualTo(mockUrl);

    log.info("✅ All container properties correctly injected");
  }
}
