package com.github.spud.sample.ai.agent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.github.spud.sample.ai.agent.domain.model.embedding.ArkMultimodalEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Ark 多模态嵌入模型解析测试
 */
class ArkMultimodalEmbeddingModelTest {

  private RestClient.Builder restClientBuilder;
  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() {
    restClientBuilder = RestClient.builder();
  }

  @Test
  void parseEmbeddings_success() {
    // 构建 RestClient 和 MockServer
    mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    RestClient restClient = restClientBuilder.build();

    String baseUrl = "https://ark.example.com";
    String embeddingsPath = "/api/v3/embeddings/multimodal";
    String apiKey = "test-key";
    String modelName = "test-model";

    ArkMultimodalEmbeddingModel model = new ArkMultimodalEmbeddingModel(
      baseUrl, embeddingsPath, apiKey, modelName, restClient);

    // Mock 响应
    String mockResponse = """
      {
        "created": 1743575029,
        "data": {
          "embedding": [
            -0.123046875, -0.35546875, -0.318359375, -0.255859375
          ],
          "object": "embedding"
        },
        "id": "021743575029461acbe49a31755bec77b2f09448eb15fa9a88e47",
        "model": "doubao-embedding-vision-250615",
        "object": "list",
        "usage": {
          "prompt_tokens": 13987,
          "prompt_tokens_details": {
            "image_tokens": 13800,
            "text_tokens": 187
          },
          "total_tokens": 13987
        }
      }
      """;

    mockServer.expect(requestTo(baseUrl + embeddingsPath))
      .andExpect(method(HttpMethod.POST))
      .andExpect(header("Authorization", "Bearer " + apiKey))
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

    // 调用 embed
    List<float[]> results = model.embed(List.of("text1", "text2"));

    // 断言
    assertThat(results).hasSize(1);
    assertThat(results.get(0)).containsExactly(-0.123046875f, -0.35546875f, -0.318359375f,
      -0.255859375f);

    mockServer.verify();
  }

  @Test
  void emptyInput_returnsEmptyResults() {
    mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    RestClient restClient = restClientBuilder.build();

    ArkMultimodalEmbeddingModel model = new ArkMultimodalEmbeddingModel(
      "https://ark.example.com", "/embeddings/multimodal",
      "test-key", "test-model", restClient);

    // 空输入不应触发 HTTP 调用
    EmbeddingResponse response = model.call(new EmbeddingRequest(List.of(), null));

    assertThat(response.getResults()).isEmpty();
    // 不验证 mockServer，因为不应该有调用
  }

  @Test
  void httpError_throwsRuntimeException() {
    mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    RestClient restClient = restClientBuilder.build();

    String baseUrl = "https://ark.example.com";
    String embeddingsPath = "/api/v3/embeddings/multimodal";

    ArkMultimodalEmbeddingModel model = new ArkMultimodalEmbeddingModel(
      baseUrl, embeddingsPath, "test-key", "test-model", restClient);

    // Mock 400 错误
    mockServer.expect(requestTo(baseUrl + embeddingsPath))
      .andExpect(method(HttpMethod.POST))
      .andRespond(withBadRequest().body("Invalid request"));

    // 断言抛出异常
    assertThatThrownBy(() -> model.embed(List.of("text")))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("Ark embedding failed");

    mockServer.verify();
  }

  @Test
  void embedForResponse_parsesCorrectly() {
    mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    RestClient restClient = restClientBuilder.build();

    String baseUrl = "https://ark.example.com";
    String embeddingsPath = "/api/v3/embeddings/multimodal";

    ArkMultimodalEmbeddingModel model = new ArkMultimodalEmbeddingModel(
      baseUrl, embeddingsPath, "test-key", "test-model", restClient);

    String mockResponse = """
      {
        "created": 1743575029,
        "data": {
          "embedding": [
            -0.123046875, -0.35546875, -0.318359375, -0.255859375
          ],
          "object": "embedding"
        },
        "id": "021743575029461acbe49a31755bec77b2f09448eb15fa9a88e47",
        "model": "doubao-embedding-vision-250615",
        "object": "list",
        "usage": {
          "prompt_tokens": 13987,
          "prompt_tokens_details": {
            "image_tokens": 13800,
            "text_tokens": 187
          },
          "total_tokens": 13987
        }
      }
      """;

    mockServer.expect(requestTo(baseUrl + embeddingsPath))
      .andExpect(method(HttpMethod.POST))
      .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

    EmbeddingResponse response = model.embedForResponse(List.of("test"));

    assertThat(response.getResults()).hasSize(1);
    assertThat(response.getResults().get(0).getOutput()).containsExactly(
      -0.123046875f, -0.35546875f, -0.318359375f, -0.255859375f);

    EmbeddingResponseMetadata metadata = response.getMetadata();
    assertThat(metadata).isNotNull();
    assertThat(metadata.getModel()).isEqualTo("doubao-embedding-vision-250615");
    assertThat(metadata.getUsage()).isNotNull();
    assertThat(metadata.getUsage().getPromptTokens()).isEqualTo(13987);
    assertThat(metadata.getUsage().getTotalTokens()).isEqualTo(13987);

    mockServer.verify();
  }
}
