package com.github.spud.sample.ai.agent.interfaces.rest;

import com.github.spud.sample.ai.agent.domain.rag.RagIngestService;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 摄取 API Controller
 * 提供文本与文件的向量化摄取能力
 */
@Slf4j
@RestController
@RequestMapping("/rag")
public class RagController {

  private final Optional<RagIngestService> ragIngestService;

  @Autowired
  public RagController(@Autowired(required = false) RagIngestService ragIngestService) {
    this.ragIngestService = Optional.ofNullable(ragIngestService);
  }

  /**
   * RAG 文本摄取
   */
  @PostMapping("/ingest/text")
  public ResponseEntity<IngestResponse> ingestText(@RequestBody IngestTextRequest request) {
    if (ragIngestService.isEmpty()) {
      return ResponseEntity.badRequest()
        .body(new IngestResponse(false, 0, "RAG is not enabled"));
    }

    log.info("Ingesting text: length={}", request.getContent().length());

    int chunks = ragIngestService.get().ingestText(request.getContent(), request.getMetadata());

    return ResponseEntity.ok(new IngestResponse(true, chunks, "Text ingested successfully"));
  }

  /**
   * RAG 文件摄取
   */
  @PostMapping("/ingest/file")
  public ResponseEntity<IngestResponse> ingestFile(@RequestBody IngestFileRequest request) {
    if (ragIngestService.isEmpty()) {
      return ResponseEntity.badRequest()
        .body(new IngestResponse(false, 0, "RAG is not enabled"));
    }

    log.info("Ingesting file: {}", request.getFilePath());

    try {
      int chunks = ragIngestService.get().ingestFile(
        Path.of(request.getFilePath()),
        request.getMetadata()
      );
      return ResponseEntity.ok(new IngestResponse(true, chunks, "File ingested successfully"));
    } catch (Exception e) {
      log.error("Failed to ingest file: {}", e.getMessage(), e);
      return ResponseEntity.badRequest()
        .body(new IngestResponse(false, 0, "Failed: " + e.getMessage()));
    }
  }

  // ===== Request/Response DTOs =====

  @Data
  public static class IngestTextRequest {
    private String content;
    private Map<String, Object> metadata;
  }

  @Data
  public static class IngestFileRequest {
    private String filePath;
    private Map<String, Object> metadata;
  }

  @Data
  public static class IngestResponse {
    private boolean success;
    private int chunks;
    private String message;

    public IngestResponse() {
    }

    public IngestResponse(boolean success, int chunks, String message) {
      this.success = success;
      this.chunks = chunks;
      this.message = message;
    }
  }
}
