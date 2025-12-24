package com.github.spud.sample.ai.agent.domain.kernel.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReAct JSON 协议的单步完整结构
 * <p>
 * 每轮模型输出应符合此格式： { "thought": "...", "action": { "type": "tool|final|none", ... } }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactJsonStep {

  /**
   * 思考内容（建议 ≤200 字，用于可观测性）
   */
  private String thought;

  /**
   * 动作
   */
  private ReactJsonAction action;

  /**
   * 验证整体结构
   */
  public void validate() {
    if (action == null) {
      throw new IllegalArgumentException("Action is required in ReactJsonStep");
    }
    action.validate();
  }
}
