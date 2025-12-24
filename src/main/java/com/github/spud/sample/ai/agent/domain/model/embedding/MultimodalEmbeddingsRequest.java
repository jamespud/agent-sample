package com.github.spud.sample.ai.agent.domain.model.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 多模态向量化服务请求实体类
 * <p>
 * 接口地址：POST <a href="https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal">API</a>
 */
@Data
@Builder
public class MultimodalEmbeddingsRequest {

  /**
   * 模型 ID 或 Endpoint ID（必选） 需先开通模型服务并查询 Model ID，或使用 Endpoint ID 获得高级能力
   */
  @JsonProperty("model")
  private String model;

  /**
   * 需要向量化的内容列表（必选） 支持文本、图片、视频三种类型，不同模型支持情况参考官方文档
   */
  @JsonProperty("input")
  private List<Input> input;

  /**
   * 向量返回格式（可选，默认值：float） 取值范围：float、base64、null
   */
  @Builder.Default
  @JsonProperty("encoding_format")
  private String encodingFormat = "float";

  /**
   * 输出向量维度（可选，默认值：2048） 取值范围：1024 或 2048（仅 doubao-embedding-vision-250615 及后续版本支持）
   */
  @Builder.Default

  @JsonProperty("dimensions")
  private Integer dimensions = 2048;

  /**
   * 推理提示词（可选） 未传入时按输入模态生成默认值
   */
  @JsonProperty("instructions")
  private String instructions;

  /**
   * 多向量开关配置（可选，默认值：type="disabled"） type 取值：disabled（仅输出稠密向量）、enabled（同时输出单稠密向量和多向量列表）
   */
  @Builder.Default
  @JsonProperty("multi_embedding")
  private MultiEmbedding multiEmbedding = new MultiEmbedding("disabled");

  /**
   * 稀疏向量开关配置（可选，仅纯文本输入支持） type 取值：disabled（仅输出稠密向量）、enabled（同时输出稠密向量和稀疏向量）
   */
  @JsonProperty("sparse_embedding")
  private SparseEmbedding sparseEmbedding;

  /**
   * 输入内容实体（支持文本、图片、视频三种类型）
   */
  @Data
  public static class Input {

    /**
     * 输入内容类型（必选） 取值：text（文本）、image_url（图片）、video_url（视频）
     */
    @JsonProperty("type")
    private String type;

    /**
     * 文本内容（仅 type="text" 时必选） 要求：utf-8 编码，长度≤100000 字节，单条 token 数≤8k，总 token 数建议≤4096 或条数≤4
     */
    @JsonProperty("text")
    private String text;

    /**
     * 图片信息（仅 type="image_url" 时必选）
     */
    @JsonProperty("image_url")
    private ImageUrl imageUrl;

    /**
     * 视频信息（仅 type="video_url" 时必选）
     */
    @JsonProperty("video_url")
    private VideoUrl videoUrl;

    private Input() {
    }

    public Input(String type, String text) {
      this.type = type;
      this.text = text;
    }

    public Input(String type, ImageUrl imageUrl) {
      this.type = type;
      this.imageUrl = imageUrl;
    }

    public Input(String type, VideoUrl videoUrl) {
      this.type = type;
      this.videoUrl = videoUrl;
    }
  }

  /**
   * 图片信息实体
   */
  @Data
  public static class ImageUrl {

    /**
     * 图片地址（必选） 支持：可访问的 URL 或 Base64 编码（格式：data:image/{格式};base64,{编码内容}）
     */
    @JsonProperty("url")
    private String url;
  }

  /**
   * 视频信息实体
   */
  @Data
  public static class VideoUrl {

    /**
     * 视频地址（必选） 支持：可访问的 URL 或 Base64 编码，格式要求：.mp4、.avi、.mov（小写），文件≤50MB
     */
    @JsonProperty("url")
    private String url;
  }

  /**
   * 多向量开关配置实体
   */
  @Data
  public static class MultiEmbedding {

    @JsonProperty("type")
    private String type;

    public MultiEmbedding(String type) {
      this.type = type;
    }
  }

  /**
   * 稀疏向量开关配置实体
   */
  @Data
  public static class SparseEmbedding {

    @JsonProperty("type")
    private String type;
  }
}