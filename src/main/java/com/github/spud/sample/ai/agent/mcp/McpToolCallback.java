package com.github.spud.sample.ai.agent.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * MCP 工具回调代理 将远程 MCP 工具包装为本地 ToolCallback
 */
@Slf4j
public class McpToolCallback implements ToolCallback {

  @Getter
  private final String serverId;
  @Getter
  private final String originalToolName;
  @Getter
  private final String namespacedName;
  private final ToolDefinition toolDefinition;
  private final McpSyncClient client;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  public McpToolCallback(
    String serverId,
    String originalToolName,
    String namespacedName,
    ToolDefinition toolDefinition,
    McpSyncClient client) {
    this.serverId = serverId;
    this.originalToolName = originalToolName;
    this.namespacedName = namespacedName;
    this.toolDefinition = toolDefinition;
    this.client = client;
    this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return toolDefinition;
  }

  @Override
  public String call(String toolInput) {
    log.debug("Calling MCP tool: {} (server: {}) with input: {}", originalToolName, serverId,
      toolInput);

    try {
      // 解析输入参数
      Map<String, Object> params;
      if (toolInput == null || toolInput.isBlank()) {
        params = Map.of();
      } else {
        params = objectMapper.readValue(toolInput,
          new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
          });
      }

      // 调用远程工具
      CallToolResult result = client.callTool(new CallToolRequest(originalToolName, params));

      // 处理结果
      if (result.content() == null || result.content().isEmpty()) {
        return "";
      }

      // 将所有内容拼接为字符串
      StringBuilder sb = new StringBuilder();
      for (var content : result.content()) {
        if (content instanceof io.modelcontextprotocol.spec.McpSchema.TextContent textContent) {
          sb.append(textContent.text());
        } else {
          sb.append(content.toString());
        }
      }

      String resultStr = sb.toString();
      log.debug("MCP tool {} returned: {}", originalToolName, resultStr);
      return resultStr;

    } catch (Exception e) {
      log.error("Failed to call MCP tool: {} - {}", originalToolName, e.getMessage(), e);
      return "Error calling tool " + originalToolName + ": " + e.getMessage();
    }
  }
}
