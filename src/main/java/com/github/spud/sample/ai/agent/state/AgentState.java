package com.github.spud.sample.ai.agent.state;

/**
 * Agent 状态枚举
 * <pre>
 * IDLE → THINKING → ACTING → THINKING (循环) → FINISHED
 *                                            → ERROR
 * </pre>
 */
public enum AgentState {
  /**
   * 空闲状态，等待启动
   */
  IDLE,

  /**
   * 思考状态，调用 LLM 决定下一步
   */
  THINKING,

  /**
   * 行动状态，执行工具调用
   */
  ACTING,

  /**
   * 完成状态（终态）
   */
  FINISHED,

  /**
   * 错误状态（终态）
   */
  ERROR;

  /**
   * 是否为终态
   */
  public static boolean isFinal(AgentState state) {
    return state == FINISHED || state == ERROR;
  }
}
