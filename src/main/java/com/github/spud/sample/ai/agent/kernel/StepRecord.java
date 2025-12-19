package com.github.spud.sample.ai.agent.kernel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * 每一步执行的详细记录，用于可观测性与审计
 */
@Data
@Builder
public class StepRecord {

  private int stepNumber;
  private String state;
  private String event;
  private String promptSummary;
  private List<ToolCallRecord> toolCalls;
  private List<ToolResultRecord> toolResults;
  private List<RetrievalDocRecord> retrievalDocs;
  private long durationMs;
  private String error;
  private Instant timestamp;

  @Data
  @Builder
  public static class ToolCallRecord {

    private String id;
    private String name;
    private String arguments;
  }

  @Data
  @Builder
  public static class ToolResultRecord {

    private String toolCallId;
    private String toolName;
    private String result;
    private boolean success;
    private String error;
  }

  @Data
  @Builder
  public static class RetrievalDocRecord {

    private String docId;
    private String content;
    private double score;
    private Map<String, Object> metadata;
  }
}
