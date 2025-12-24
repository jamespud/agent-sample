package com.github.spud.sample.ai.agent.domain.kernel;

import com.github.spud.sample.ai.agent.domain.state.AgentState;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Agent 执行的最终结果
 */
@Data
@Builder
public class AgentResult {

  /**
   * 追踪 ID
   */
  private String traceId;

  /**
   * 会话 ID
   */
  private String conversationId;

  /**
   * 最终状态
   */
  private AgentState finalState;

  /**
   * 是否成功完成
   */
  private boolean success;

  /**
   * 最终回答
   */
  private String answer;

  /**
   * 终止原因
   */
  private AgentContext.TerminationReason terminationReason;

  /**
   * 总步数
   */
  private int totalSteps;

  /**
   * 总耗时（毫秒）
   */
  private long totalDurationMs;

  /**
   * 步骤记录摘要
   */
  private List<StepRecord> stepRecords;

  /**
   * 错误信息（如果有）
   */
  private String error;

  /**
   * 开始时间
   */
  private Instant startTime;

  /**
   * 结束时间
   */
  private Instant endTime;

  /**
   * 从上下文创建成功结果
   */
  public static AgentResult fromContext(AgentContext ctx) {
    Instant end = Instant.now();
    return AgentResult.builder()
      .traceId(ctx.getTraceId())
      .conversationId(ctx.getConversationId())
      .finalState(ctx.getCurrentState())
      .success(ctx.getCurrentState() == AgentState.FINISHED)
      .answer(ctx.getFinalAnswer())
      .terminationReason(ctx.getTerminationReason())
      .totalSteps(ctx.getStepCounter())
      .totalDurationMs(end.toEpochMilli() - ctx.getStartTime().toEpochMilli())
      .stepRecords(ctx.getStepRecords())
      .startTime(ctx.getStartTime())
      .endTime(end)
      .build();
  }

  /**
   * 创建错误结果
   */
  public static AgentResult error(AgentContext ctx, String errorMessage) {
    Instant end = Instant.now();
    return AgentResult.builder()
      .traceId(ctx.getTraceId())
      .conversationId(ctx.getConversationId())
      .finalState(AgentState.ERROR)
      .success(false)
      .terminationReason(AgentContext.TerminationReason.ERROR)
      .totalSteps(ctx.getStepCounter())
      .totalDurationMs(end.toEpochMilli() - ctx.getStartTime().toEpochMilli())
      .stepRecords(ctx.getStepRecords())
      .error(errorMessage)
      .startTime(ctx.getStartTime())
      .endTime(end)
      .build();
  }
}
