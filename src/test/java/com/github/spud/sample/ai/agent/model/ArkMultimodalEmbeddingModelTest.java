package com.github.spud.sample.ai.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Ark 多模态嵌入模型解析测试
 */
class ArkMultimodalEmbeddingModelTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        objectMapper = new ObjectMapper();
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
                baseUrl, embeddingsPath, apiKey, modelName, restClient, objectMapper);

        // Mock 响应
        String mockResponse = """
                {
                  "data": [
                    {"embedding": [0.1, 0.2, 0.3]},
                    {"embedding": [0.4, 0.5, 0.6]}
                  ]
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
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(results.get(1)).containsExactly(0.4f, 0.5f, 0.6f);

        mockServer.verify();
    }

    @Test
    void emptyInput_returnsEmptyResults() {
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        ArkMultimodalEmbeddingModel model = new ArkMultimodalEmbeddingModel(
                "https://ark.example.com", "/embeddings/multimodal",
                "test-key", "test-model", restClient, objectMapper);

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
                baseUrl, embeddingsPath, "test-key", "test-model", restClient, objectMapper);

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
                baseUrl, embeddingsPath, "test-key", "test-model", restClient, objectMapper);

        String mockResponse = """
                {
                  "data": [
                    {"embedding": [1.0, 2.0]}
                  ]
                }
                """;

        mockServer.expect(requestTo(baseUrl + embeddingsPath))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        EmbeddingResponse response = model.embedForResponse(List.of("test"));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getOutput()).containsExactly(1.0f, 2.0f);

        mockServer.verify();
    }
}
