package com.github.spud.sample.ai.agent.domain.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.spud.sample.ai.agent.infrastructure.util.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

/**
 * 检索结果缓存 缓存相同查询的检索结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class RetrievalCache {

  private static final String KEY_PREFIX = "rag:";

  private final StringRedisTemplate redisTemplate;
  private final RagProperties ragProperties;

  /**
   * 获取缓存的检索结果
   */
  public Optional<List<CachedDocument>> get(String query, int topK, String filters) {
    String key = buildKey(query, topK, filters);
    try {
      String cached = redisTemplate.opsForValue().get(key);
      if (cached != null) {
        log.debug("Retrieval cache hit for key: {}", key);
        List<CachedDocument> docs = JsonUtils.fromJson(cached, new TypeReference<>() {
        });
        return Optional.of(docs);
      }
    } catch (Exception e) {
      log.warn("Failed to get retrieval from cache: {}", e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * 缓存检索结果
   */
  public void put(String query, int topK, String filters, List<Document> documents) {
    String key = buildKey(query, topK, filters);
    try {
      List<CachedDocument> cachedDocs = documents.stream()
        .map(doc -> new CachedDocument(
          doc.getId(),
          doc.getText(),
          doc.getMetadata(),
          doc.getScore() != null ? doc.getScore() : 0.0
        ))
        .toList();

      String serialized = JsonUtils.toJson(cachedDocs);
      Duration ttl = Duration.ofSeconds(ragProperties.getCache().getRetrieval().getTtl());
      redisTemplate.opsForValue().set(key, serialized, ttl);
      log.debug("Cached retrieval for key: {}", key);
    } catch (Exception e) {
      log.warn("Failed to cache retrieval: {}", e.getMessage());
    }
  }

  private String buildKey(String query, int topK, String filters) {
    String raw = query + "|" + topK + "|" + (filters != null ? filters : "");
    String hash = DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    return KEY_PREFIX + hash;
  }

  /**
   * 缓存文档结构
   */
  public record CachedDocument(
    String id,
    String content,
    java.util.Map<String, Object> metadata,
    double score
  ) {

  }
}
