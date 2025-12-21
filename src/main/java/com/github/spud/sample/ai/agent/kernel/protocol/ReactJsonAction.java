package com.github.spud.sample.ai.agent.kernel.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReAct JSON 协议中的 Action 部分
 * <p>
 * 支持三种类型： - tool: 调用单个工具 - final: 返回最终答案 - none: 无动作（触发重试或终止策略）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactJsonAction {

  /**
   * 动作类型: "tool" | "final" | "none"
   */
  private String type;

  /**
   * 工具名称（仅当 type=tool 时有效）
   */
  private String name;

  /**
   * 工具参数（仅当 type=tool 时有效，原始 JSON 对象）
   */
  private JsonNode args;

  /**
   * 最终答案（仅当 type=final 时有效）
   */
  private String answer;

  /**
   * 验证 action 结构是否合法
   */
  public void validate() {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("Action type is required");
    }

    switch (type.toLowerCase()) {
      case "tool":
        if (name == null || name.isBlank()) {
          throw new IllegalArgumentException("Tool name is required for action type 'tool'");
        }
        if (args == null) {
          throw new IllegalArgumentException("Tool args is required for action type 'tool'");
        }
        break;
      case "final":
        if (answer == null) {
          throw new IllegalArgumentException("Answer is required for action type 'final'");
        }
        break;
      case "none":
        // none 不需要额外字段
        break;
      default:
        throw new IllegalArgumentException("Unknown action type: " + type +
          ". Expected: tool, final, or none");
    }
  }
}
