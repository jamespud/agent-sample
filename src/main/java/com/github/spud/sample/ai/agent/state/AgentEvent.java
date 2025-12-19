package com.github.spud.sample.ai.agent.state;

/**
 * Agent 状态机事件枚举
 */
public enum AgentEvent {
  /**
   * 启动 Agent
   */
  START,

  /**
   * 思考完成，有工具需要调用
   */
  THINK_DONE_WITH_TOOLS,

  /**
   * 思考完成，无工具调用，有最终回答
   */
  THINK_DONE_NO_TOOLS,

  /**
   * 行动完成，继续思考
   */
  ACT_DONE,

  /**
   * terminate 工具被调用
   */
  TOOL_TERMINATE,

  /**
   * 达到最大步数
   */
  STOP_MAX_STEPS,

  /**
   * 连续空响应终止
   */
  STOP_EMPTY,

  /**
   * 连续重复响应终止
   */
  STOP_DUPLICATE,

  /**
   * 执行失败
   */
  FAIL
}
