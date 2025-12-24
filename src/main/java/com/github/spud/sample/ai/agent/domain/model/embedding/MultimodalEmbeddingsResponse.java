package com.github.spud.sample.ai.agent.domain.model.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

/**
 * 多模态向量化服务响应实体类
 * <p>
 * 接口地址：POST <a href="https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal">API</a>
 */
@Data
public class MultimodalEmbeddingsResponse {

  /**
   * 本次请求的唯一标识
   */
  @JsonProperty("id")
  private String id;

  /**
   * 本次请求实际使用的模型名称和版本
   */
  @JsonProperty("model")
  private String model;

  /**
   * 本次请求创建时间的 Unix 时间戳（秒）
   */
  @JsonProperty("created")
  private Integer created;

  /**
   * 固定值：list
   */
  @JsonProperty("object")
  private String object = "list";

  /**
   * 本次请求的算法输出内容（数组形式，与输入列表一一对应）
   */
  @JsonProperty("data")
  private DataItem data;

  /**
   * 本次请求的 token 用量统计
   */
  @JsonProperty("usage")
  private Usage usage;

  public EmbeddingResponse toEmbeddingResponse() {
    List<Float> list = data.getEmbedding();
    float[] arr = new float[list.size()];
    int i = 0;
    for (Float v : data.getEmbedding()) {
      arr[i] = (v != null) ? v : 0f;
      i++;
    }
    var metadata = new EmbeddingResponseMetadata(this.model,
      new DefaultUsage(this.usage.promptTokens, 0, this.usage.totalTokens, this.usage));
    return new EmbeddingResponse(List.of(new Embedding(arr, 0)), metadata);
  }

  /**
   * 算法输出内容项（与输入的每个内容一一对应）
   */
  @Data
  public static class DataItem {

    /**
     * 对应内容的向量化结果（稠密向量）
     */
    @JsonProperty("embedding")
    private List<Float> embedding;

    /**
     * 多向量列表（条件返回） 仅当请求中 multi_embedding.type = "enabled" 时返回 外层为数组，每个成员为一个 float 型向量数组
     */
    @JsonProperty("multi_embedding")
    private List<List<Float>> multiEmbedding;

    /**
     * 稀疏向量（条件返回） 仅当请求中 sparse_embedding.type = "enabled" 且为纯文本输入时返回 每个成员包含维度索引和非零值
     */
    @JsonProperty("sparse_embedding")
    private List<SparseEmbeddingItem> sparseEmbedding;

    /**
     * 固定值：embedding
     */
    @JsonProperty("object")
    private String object = "embedding";
  }

  /**
   * 稀疏向量元素（仅稀疏向量开启时返回）
   */
  @Data
  public static class SparseEmbeddingItem {

    /**
     * 维度索引
     */
    @JsonProperty("index")
    private Integer index;

    /**
     * 该维度的非零值
     */
    @JsonProperty("value")
    private Float value;
  }

  /**
   * Token 用量统计详情
   */
  @Data
  public static class Usage {

    /**
     * 输入内容 token 数量
     */
    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    /**
     * 本次请求消耗的总 token 数量（输入 + 输出）
     */
    @JsonProperty("total_tokens")
    private Integer totalTokens;

    /**
     * 输入内容的 token 用量细分
     */
    @JsonProperty("prompt_tokens_details")
    private PromptTokensDetails promptTokensDetails;
  }

  /**
   * 输入内容的 token 用量细分详情
   */
  @Data
  public static class PromptTokensDetails {

    /**
     * 文本内容 + 视频时间轴对应的 token 量 说明：图片/视频传入时会生成少量预设文本 token，计入此字段
     */
    @JsonProperty("text_tokens")
    private Integer textTokens;

    /**
     * 图片内容 + 视频抽帧图片对应的 token 量
     */
    @JsonProperty("image_tokens")
    private Integer imageTokens;
  }

}