package com.github.spud.sample.ai.agent.interfaces.rest;

import com.github.spud.sample.ai.agent.domain.kernel.AgentContext;
import com.github.spud.sample.ai.agent.domain.kernel.AgentKernel;
import com.github.spud.sample.ai.agent.domain.kernel.AgentResult;
import com.github.spud.sample.ai.agent.domain.kernel.StepRecord;
import com.github.spud.sample.ai.agent.domain.rag.RagIngestService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kernel Agent API Controller 提供 Agent 执行与追踪 API
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

  private final AgentKernel agentKernel;
  private final Optional<RagIngestService> ragIngestService;

  @Autowired
  public AgentController(AgentKernel agentKernel,
    @Autowired(required = false) RagIngestService ragIngestService) {
    this.agentKernel = agentKernel;
    this.ragIngestService = Optional.ofNullable(ragIngestService);
  }

  // 简单的 trace 存储
  private final Map<String, AgentResult> traceStore = new ConcurrentHashMap<>();

  /**
   * 执行 Agent（同步）
   */
  @PostMapping("/run")
  public Mono<ResponseEntity<AgentResult>> run(@RequestBody RunRequest request) {
    return Mono.fromCallable(() -> {
      log.info("Received agent run request: {}", StringUtils.truncate(request.getRequest(), 100));

      AgentContext.AgentContextBuilder ctxBuilder = AgentContext.builder()
        .userRequest(request.getRequest());

      if (request.getMaxSteps() != null) {
        ctxBuilder.maxSteps(request.getMaxSteps());
      }
      if (request.getModelProvider() != null) {
        ctxBuilder.modelProvider(request.getModelProvider());
      }
      if (request.getRagEnabled() != null) {
        ctxBuilder.ragEnabled(request.getRagEnabled());
      }
      if (request.getEnabledMcpServers() != null) {
        ctxBuilder.enabledMcpServers(request.getEnabledMcpServers());
      }

      AgentResult result = agentKernel.execute(ctxBuilder.build());

      // 存储 trace
      traceStore.put(result.getTraceId(), result);

      return ResponseEntity.ok(result);
    }).subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * 获取执行 trace
   */
  @GetMapping("/{traceId}")
  public ResponseEntity<TraceResponse> getTrace(@PathVariable String traceId) {
    AgentResult result = traceStore.get(traceId);
    if (result == null) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(TraceResponse.fromResult(result));
  }

  /**
   * 获取 trace 列表
   */
  @GetMapping("/traces")
  public ResponseEntity<List<TraceSummary>> listTraces(
    @RequestParam(defaultValue = "20") int limit) {

    List<TraceSummary> summaries = traceStore.values().stream()
      .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
      .limit(limit)
      .map(TraceSummary::fromResult)
      .toList();

    return ResponseEntity.ok(summaries);
  }

  /**
   * RAG 文本摄取
   */
  @PostMapping("/rag/ingest/text")
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
  @PostMapping("/rag/ingest/file")
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
  public static class RunRequest {

    private String request;
    private Integer maxSteps;
    private String modelProvider;
    private Boolean ragEnabled;
    private List<String> enabledMcpServers;
  }

  @Data
  public static class TraceResponse {

    private String traceId;
    private String conversationId;
    private boolean success;
    private String answer;
    private String terminationReason;
    private int totalSteps;
    private long totalDurationMs;
    private List<StepRecord> steps;

    public static TraceResponse fromResult(AgentResult result) {
      TraceResponse resp = new TraceResponse();
      resp.setTraceId(result.getTraceId());
      resp.setConversationId(result.getConversationId());
      resp.setSuccess(result.isSuccess());
      resp.setAnswer(result.getAnswer());
      resp.setTerminationReason(result.getTerminationReason() != null
        ? result.getTerminationReason().name() : null);
      resp.setTotalSteps(result.getTotalSteps());
      resp.setTotalDurationMs(result.getTotalDurationMs());
      resp.setSteps(result.getStepRecords());
      return resp;
    }
  }

  @Data
  public static class TraceSummary {

    private String traceId;
    private boolean success;
    private int totalSteps;
    private long totalDurationMs;
    private String terminationReason;

    public static TraceSummary fromResult(AgentResult result) {
      TraceSummary summary = new TraceSummary();
      summary.setTraceId(result.getTraceId());
      summary.setSuccess(result.isSuccess());
      summary.setTotalSteps(result.getTotalSteps());
      summary.setTotalDurationMs(result.getTotalDurationMs());
      summary.setTerminationReason(result.getTerminationReason() != null
        ? result.getTerminationReason().name() : null);
      return summary;
    }
  }

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

  // Use a regular POJO instead of a Java record to maintain compatibility with older
  // JVMs that may be used to run this application.
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
