package com.github.spud.sample.ai.agent.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

  /**
   * 是否启用 RAG
   */
  private boolean enabled = true;

  /**
   * 检索返回的文档数量
   */
  private int topK = 5;

  /**
   * 文档切分大小
   */
  private int chunkSize = 1000;

  /**
   * 文档切分重叠
   */
  private int chunkOverlap = 200;

  /**
   * 缓存配置
   */
  private CacheConfig cache = new CacheConfig();

  @Data
  public static class CacheConfig {

    /**
     * Embedding 缓存配置
     */
    private EmbeddingCacheConfig embedding = new EmbeddingCacheConfig();

    /**
     * 检索结果缓存配置
     */
    private RetrievalCacheConfig retrieval = new RetrievalCacheConfig();
  }

  @Data
  public static class EmbeddingCacheConfig {

    /**
     * TTL（秒）
     */
    private long ttl = 86400; // 24 hours
  }

  @Data
  public static class RetrievalCacheConfig {

    /**
     * TTL（秒）
     */
    private long ttl = 3600; // 1 hour
  }
}
