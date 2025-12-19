package com.github.spud.sample.ai.agent.rag;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

/**
 * Embedding 缓存 避免重复计算相同文本的 embedding
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class EmbeddingCache {

  private static final String KEY_PREFIX = "emb:";

  private final StringRedisTemplate redisTemplate;
  private final RagProperties ragProperties;

  /**
   * 获取缓存的 embedding
   */
  public Optional<float[]> get(String text, String modelName) {
    String key = buildKey(text, modelName);
    try {
      String cached = redisTemplate.opsForValue().get(key);
      if (cached != null) {
        log.debug("Embedding cache hit for key: {}", key);
        return Optional.of(deserialize(cached));
      }
    } catch (Exception e) {
      log.warn("Failed to get embedding from cache: {}", e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * 缓存 embedding
   */
  public void put(String text, String modelName, float[] embedding) {
    String key = buildKey(text, modelName);
    try {
      String serialized = serialize(embedding);
      Duration ttl = Duration.ofSeconds(ragProperties.getCache().getEmbedding().getTtl());
      redisTemplate.opsForValue().set(key, serialized, ttl);
      log.debug("Cached embedding for key: {}", key);
    } catch (Exception e) {
      log.warn("Failed to cache embedding: {}", e.getMessage());
    }
  }

  private String buildKey(String text, String modelName) {
    String hash = DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
    return KEY_PREFIX + modelName + ":" + hash;
  }

  private String serialize(float[] embedding) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(embedding[i]);
    }
    return sb.toString();
  }

  private float[] deserialize(String cached) {
    String[] parts = cached.split(",");
    float[] result = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      result[i] = Float.parseFloat(parts[i]);
    }
    return result;
  }
}
