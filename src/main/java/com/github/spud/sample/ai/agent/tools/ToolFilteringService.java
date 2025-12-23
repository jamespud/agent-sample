package com.github.spud.sample.ai.agent.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.spud.sample.ai.agent.kernel.AgentContext;
import com.github.spud.sample.ai.agent.util.JsonUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;

/**
 * 工具过滤服务
 * <p>
 * 根据请求上下文（AgentContext）按需过滤可用工具： - 永远隐藏 terminate（由协议的 action.type=final 收口） - 根据 ragEnabled 隐藏/显示
 * retrieve_knowledge - 根据 enabledMcpServers 过滤 MCP 工具（按命名空间前缀）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolFilteringService {

  private final ToolRegistry toolRegistry;
  private final ToolNamespace toolNamespace;

  /**
   * 获取本次请求允许使用的工具定义
   */
  public Collection<ToolDefinition> getAllowedDefinitions(AgentContext ctx) {
    Collection<ToolDefinition> allTools = toolRegistry.getAllDefinitions();
    List<ToolDefinition> allowed = new ArrayList<>();

    for (ToolDefinition def : allTools) {
      if (isToolAllowed(def.name(), ctx)) {
        allowed.add(def);
      }
    }

    log.debug("Filtered tools: total={}, allowed={}, ragEnabled={}, enabledMcpServers={}",
      allTools.size(), allowed.size(), ctx.isRagEnabled(), ctx.getEnabledMcpServers());

    return allowed;
  }

  /**
   * 构建工具目录提示（包含工具清单 JSON + ReAct 协议说明）
   */
  public String buildToolCatalogPrompt(AgentContext ctx) {
    Collection<ToolDefinition> allowed = getAllowedDefinitions(ctx);

    StringBuilder sb = new StringBuilder();
    sb.append("【可用工具清单】\n");

    if (allowed.isEmpty()) {
      sb.append("当前无可用工具。请直接使用 action.type=final 提供答案。\n\n");
    } else {
      sb.append("以下是你可以调用的工具，格式为 JSON 数组：\n\n");
      sb.append(buildToolsJsonArray(allowed));
      sb.append("\n\n");
    }

    sb.append("【ReAct 协议要求】\n");
    sb.append("你必须严格输出一个 JSON 对象，包含两个字段：\n");
    sb.append("1. \"thought\": 你的思考过程（建议 ≤200 字，用于可观测性）\n");
    sb.append("2. \"action\": 你的动作，必须是以下三种之一：\n");
    sb.append("   a) 调用工具: {\"type\":\"tool\",\"name\":\"工具名\",\"args\":{...}}\n");
    sb.append("   b) 返回最终答案: {\"type\":\"final\",\"answer\":\"你的答案\"}\n");
    sb.append("   c) 无动作（触发重试）: {\"type\":\"none\"}\n\n");
    sb.append("【重要约束】\n");
    sb.append("- 只输出 JSON，不要有其他文字或解释\n");
    sb.append("- 每轮只能执行一个 action（单动作原则）\n");
    sb.append("- 工具执行结果会以 observation JSON 的形式返回给你\n");
    sb.append("- 当你完成任务时，使用 action.type=final 返回最终答案\n");

    return sb.toString();
  }

  /**
   * 判断工具是否允许在本次请求中使用
   */
  private boolean isToolAllowed(String toolName, AgentContext ctx) {
    // 规则1：永远隐藏 terminate（由 final action 替代）
    if ("terminate".equals(toolName)) {
      return false;
    }

    // 规则2：ragEnabled 控制 retrieve_knowledge
    if ("retrieve_knowledge".equals(toolName)) {
      return ctx.isRagEnabled();
    }

    // 规则3：MCP 工具按命名空间过滤
    String[] parts = toolNamespace.parse(toolName);
    String serverId = parts[0]; // null 表示本地工具，非 null 表示 MCP 工具

    if (serverId != null) {
      // 这是一个带命名空间的 MCP 工具
      List<String> enabledServers = ctx.getEnabledMcpServers();
      if (enabledServers == null || enabledServers.isEmpty()) {
        // 未指定则全部允许
        return true;
      }

      return enabledServers.contains(serverId);
    }

    // 规则4：本地工具（无命名空间）默认允许
    return true;
  }

  /**
   * 将工具定义列表转换为 JSON 数组字符串
   */
  private String buildToolsJsonArray(Collection<ToolDefinition> tools) {
    try {
      ArrayNode arrayNode = JsonUtils.objectMapper().createArrayNode();

      for (ToolDefinition def : tools) {
        ObjectNode toolNode = JsonUtils.objectMapper().createObjectNode();
        toolNode.put("name", def.name());
        def.description();
        toolNode.put("description", def.description());

        // inputSchema 已经是 JSON 字符串，需要解析后再嵌入
        def.inputSchema();
        if (!def.inputSchema().isBlank()) {
          try {
            toolNode.set("inputSchema", JsonUtils.objectMapper().readTree(def.inputSchema()));
          } catch (Exception e) {
            log.warn("Failed to parse inputSchema for tool {}: {}", def.name(), e.getMessage());
            toolNode.put("inputSchema", def.inputSchema());
          }
        } else {
          toolNode.put("inputSchema", "{}");
        }

        arrayNode.add(toolNode);
      }

      return JsonUtils.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(arrayNode);
    } catch (Exception e) {
      log.error("Failed to build tools JSON array: {}", e.getMessage(), e);
      return "[]";
    }
  }
}
