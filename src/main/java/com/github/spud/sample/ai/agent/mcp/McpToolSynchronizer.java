package com.github.spud.sample.ai.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.sample.ai.agent.tools.ToolNamespace;
import com.github.spud.sample.ai.agent.tools.ToolRegistry;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * MCP 工具同步器 从 MCP 服务器获取工具列表并注册到本地 ToolRegistry
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolSynchronizer {

  private final McpClientManager clientManager;
  private final ToolRegistry toolRegistry;
  private final ToolNamespace toolNamespace;

  /**
   * 同步指定服务器的所有工具
   */
  public int synchronize(String serverId) {
    log.info("Synchronizing tools from MCP server: {}", serverId);

    McpSyncClient client = clientManager.getClient(serverId)
      .orElseThrow(() -> new IllegalArgumentException("MCP server not connected: " + serverId));

    // 先清理该服务器的旧工具
    String prefix = toolNamespace.getServerPrefix(serverId);
    toolRegistry.unregisterByPrefix(prefix);

    // 获取工具列表
    ListToolsResult listResult = client.listTools();
    List<Tool> tools = listResult.tools();

    log.info("Found {} tools from MCP server: {}", tools.size(), serverId);

    // 注册每个工具
    int registered = 0;
    for (Tool tool : tools) {
      try {
        registerTool(serverId, tool, client);
        registered++;
      } catch (Exception e) {
        log.error("Failed to register MCP tool: {} from server: {}", tool.name(), serverId, e);
      }
    }

    log.info("Registered {} tools from MCP server: {}", registered, serverId);
    return registered;
  }

  /**
   * 同步所有已连接服务器的工具
   */
  public int synchronizeAll() {
    int total = 0;
    for (String serverId : clientManager.getConnectedServerIds()) {
      try {
        total += synchronize(serverId);
      } catch (Exception e) {
        log.error("Failed to synchronize tools from server: {}", serverId, e);
      }
    }
    return total;
  }

  /**
   * 注册单个 MCP 工具
   */
  private void registerTool(String serverId, Tool tool, McpSyncClient client) {
    String originalName = tool.name();
    String namespacedName = toolNamespace.namespacedName(serverId, originalName);

    // 构建 ToolDefinition
    ToolDefinition definition = DefaultToolDefinition.builder()
      .name(namespacedName)
      .description(tool.description() != null ? tool.description() : "MCP tool: " + originalName)
      .inputSchema(convertInputSchema(tool))
      .build();

    // 创建回调
    McpToolCallback callback = new McpToolCallback(
      serverId,
      originalName,
      namespacedName,
      definition,
      client
    );

    // 注册到 registry
    toolRegistry.register(namespacedName, definition, callback);

    log.debug("Registered MCP tool: {} -> {}", originalName, namespacedName);
  }

  /**
   * 将 MCP Tool 的 inputSchema 转换为 JSON 字符串
   */
  private String convertInputSchema(Tool tool) {
    if (tool.inputSchema() == null) {
      return "{\"type\":\"object\",\"properties\":{}}";
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writeValueAsString(tool.inputSchema());
    } catch (Exception e) {
      log.warn("Failed to convert input schema for tool: {}", tool.name(), e);
      return "{\"type\":\"object\",\"properties\":{}}";
    }
  }

  /**
   * 刷新指定服务器的工具（用于定期刷新）
   */
  public void refresh(String serverId) {
    if (clientManager.isConnected(serverId)) {
      synchronize(serverId);
    } else {
      log.warn("Cannot refresh tools, server not connected: {}", serverId);
    }
  }
}
