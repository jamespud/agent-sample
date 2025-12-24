package com.github.spud.sample.ai.agent.model.embedding;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.spud.sample.ai.agent.model.embedding.MultimodalEmbeddingsRequest.Input;
import com.github.spud.sample.ai.agent.util.JsonUtils;
import jakarta.annotation.Nonnull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Ark 多模态嵌入模型 按照火山引擎 Ark API 规范构造请求体并调用多模态向量化服务
 */
@Slf4j
public class ArkMultimodalEmbeddingModel implements EmbeddingModel {

  private final String baseUrl;
  private final String embeddingsPath;
  private final String apiKey;
  private final String modelName;
  private final RestClient restClient;

  public ArkMultimodalEmbeddingModel(String baseUrl, String embeddingsPath,
    String apiKey, String modelName) {
    this(baseUrl, embeddingsPath, apiKey, modelName, null);
  }

  public ArkMultimodalEmbeddingModel(String baseUrl, String embeddingsPath,
    String apiKey, String modelName, RestClient restClient) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.embeddingsPath = embeddingsPath.startsWith("/") ? embeddingsPath : "/" + embeddingsPath;
    this.apiKey = apiKey;
    this.modelName = modelName;
    this.restClient = restClient != null ? restClient : RestClient.builder().build();

    log.info("ArkMultimodalEmbeddingModel initialized: baseUrl={}, path={}, model={}",
      this.baseUrl, this.embeddingsPath, this.modelName);
  }

  @Override
  public @Nonnull EmbeddingResponse call(@Nonnull EmbeddingRequest request) {
    List<String> texts = request.getInstructions();
    if (texts.isEmpty()) {
      return new EmbeddingResponse(List.of());
    }

    List<Input> inputs = texts.stream()
      .map(text -> new Input("text", text))
      .toList();

    MultimodalEmbeddingsRequest embeddingsRequest = MultimodalEmbeddingsRequest.builder()
      .model(modelName)
      .input(inputs)
      .build();
    try {
      // 构造请求体
      ObjectNode requestBody = JsonUtils.objectMapper().createObjectNode();
      requestBody.put("model", modelName);

      ArrayNode inputArray = JsonUtils.objectMapper().createArrayNode();
      for (String text : texts) {
        ObjectNode textItem = JsonUtils.objectMapper().createObjectNode();
        textItem.put("type", "text");
        textItem.put("text", text);
        inputArray.add(textItem);
      }
      requestBody.set("input", inputArray);

      String requestJson = JsonUtils.toJson(requestBody);
      log.debug("Ark embedding request: {}", requestJson);

      // 发送请求
      MultimodalEmbeddingsResponse response = restClient.post()
        .uri(baseUrl + embeddingsPath)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(embeddingsRequest)
        .retrieve()
        .body(MultimodalEmbeddingsResponse.class);

      log.debug("Ark embedding response: {}", response);

      if (response == null || response.getData() == null) {
        throw new RuntimeException("Invalid response from Ark embedding API");
      }

      return response.toEmbeddingResponse();

    } catch (Exception e) {
      log.error("Ark embedding call failed: {}", e.getMessage(), e);
      throw new RuntimeException("Ark embedding failed: " + e.getMessage(), e);
    }
  }

  @Override
  public float[] embed(Document document) {
    throw new UnsupportedOperationException("embed(Document) not supported");
  }
}
