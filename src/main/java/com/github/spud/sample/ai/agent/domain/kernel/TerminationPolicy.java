package com.github.spud.sample.ai.agent.domain.kernel;

import com.github.spud.sample.ai.agent.domain.state.AgentEvent;
import java.util.List;

/**
 * 终止策略接口 - 判断是否应该终止 Agent 执行
 */
public interface TerminationPolicy {

  /**
   * 检查是否应该终止
   *
   * @param ctx 当前上下文
   * @return 应触发的事件（如果需要终止），或 null 继续执行
   */
  AgentEvent checkTermination(AgentContext ctx);

  /**
   * 更新空响应计数
   */
  void recordEmptyResponse(AgentContext ctx);

  /**
   * 更新重复检测（基于内容）
   */
  void recordResponse(AgentContext ctx, String content, List<StepRecord.ToolCallRecord> toolCalls);

  /**
   * 重置计数器（用于新会话）
   */
  void reset(AgentContext ctx);
}
