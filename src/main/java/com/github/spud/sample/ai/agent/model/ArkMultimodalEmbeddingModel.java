package com.github.spud.sample.ai.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Ark 多模态嵌入模型 按照火山引擎 Ark API 规范构造请求体： { "model": "...", "input": [ { "type": "text", "text": "..."
 * }, ... ] }
 */
@Slf4j
public class ArkMultimodalEmbeddingModel implements EmbeddingModel {

  private final String baseUrl;
  private final String embeddingsPath;
  private final String apiKey;
  private final String modelName;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public ArkMultimodalEmbeddingModel(String baseUrl, String embeddingsPath,
    String apiKey, String modelName) {
    this(baseUrl, embeddingsPath, apiKey, modelName, null, null);
  }

  public ArkMultimodalEmbeddingModel(String baseUrl, String embeddingsPath,
    String apiKey, String modelName,
    RestClient restClient, ObjectMapper objectMapper) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.embeddingsPath = embeddingsPath.startsWith("/") ? embeddingsPath : "/" + embeddingsPath;
    this.apiKey = apiKey;
    this.modelName = modelName;
    this.restClient = restClient != null ? restClient : RestClient.builder().build();
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

    log.info("ArkMultimodalEmbeddingModel initialized: baseUrl={}, path={}, model={}",
      this.baseUrl, this.embeddingsPath, this.modelName);
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<String> texts = request.getInstructions();
    if (texts == null || texts.isEmpty()) {
      return new EmbeddingResponse(List.of());
    }

    try {
      // 构造请求体
      ObjectNode requestBody = objectMapper.createObjectNode();
      requestBody.put("model", modelName);

      ArrayNode inputArray = objectMapper.createArrayNode();
      for (String text : texts) {
        ObjectNode textItem = objectMapper.createObjectNode();
        textItem.put("type", "text");
        textItem.put("text", text);
        inputArray.add(textItem);
      }
      requestBody.set("input", inputArray);

      String requestJson = objectMapper.writeValueAsString(requestBody);
      log.debug("Ark embedding request: {}", requestJson);

      // 发送请求
      String responseJson = restClient.post()
        .uri(baseUrl + embeddingsPath)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(requestJson)
        .retrieve()
        .body(String.class);

      log.debug("Ark embedding response: {}", responseJson);

      // 解析响应
      JsonNode responseNode = objectMapper.readTree(responseJson);
      List<Embedding> embeddings = parseEmbeddings(responseNode);

      return new EmbeddingResponse(embeddings);

    } catch (Exception e) {
      log.error("Ark embedding call failed: {}", e.getMessage(), e);
      throw new RuntimeException("Ark embedding failed: " + e.getMessage(), e);
    }
  }

  private List<Embedding> parseEmbeddings(JsonNode responseNode) {
    List<Embedding> embeddings = new ArrayList<>();

    JsonNode dataArray = responseNode.path("data");
    if (dataArray.isArray()) {
      for (int i = 0; i < dataArray.size(); i++) {
        JsonNode item = dataArray.get(i);
        JsonNode embeddingNode = item.path("embedding");

        if (embeddingNode.isArray()) {
          float[] vector = new float[embeddingNode.size()];
          for (int j = 0; j < embeddingNode.size(); j++) {
            vector[j] = (float) embeddingNode.get(j).asDouble();
          }
          embeddings.add(new Embedding(vector, i));
        }
      }
    }

    return embeddings;
  }

  @Override
  public float[] embed(String text) {
    EmbeddingResponse response = call(new EmbeddingRequest(List.of(text), null));
    if (response.getResults() != null && !response.getResults().isEmpty()) {
      return response.getResults().get(0).getOutput();
    }
    return new float[0];
  }

  @Override
  public float[] embed(Document document) {
    return embed(document.getText());
  }

  @Override
  public List<float[]> embed(List<String> texts) {
    EmbeddingResponse response = call(new EmbeddingRequest(texts, null));
    List<float[]> results = new ArrayList<>();
    if (response.getResults() != null) {
      for (Embedding emb : response.getResults()) {
        results.add(emb.getOutput());
      }
    }
    return results;
  }

  @Override
  public EmbeddingResponse embedForResponse(List<String> texts) {
    return call(new EmbeddingRequest(texts, null));
  }

  @Override
  public int dimensions() {
    // doubao-embedding-vision 默认维度，可从配置读取
    return 1536;
  }
}
