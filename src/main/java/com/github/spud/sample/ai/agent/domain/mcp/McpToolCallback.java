package com.github.spud.sample.ai.agent.domain.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * ToolCallback wrapper for MCP tools
 * 
 * This wraps MCP client tool with a renamed ToolDefinition (mcp.<serverId>.<toolName>)
 * and delegates execution to the original callback from McpToolUtils
 */
@RequiredArgsConstructor
public class McpToolCallback implements ToolCallback {

  private final ToolCallback originalCallback;
  private final ToolDefinition renamedDefinition;

  /**
   * Create MCP tool callback from original callback
   */
  public static McpToolCallback wrap(String serverId, ToolCallback originalCallback) {
    ToolDefinition originalDef = originalCallback.getToolDefinition();
    String modelToolName = McpToolNaming.toModelToolName(serverId, originalDef.name());

    // Create renamed definition
    ToolDefinition renamedDef = ToolDefinition.builder()
      .name(modelToolName)
      .description(originalDef.description())
      .inputSchema(originalDef.inputSchema())
      .build();

    return new McpToolCallback(originalCallback, renamedDef);
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return renamedDefinition;
  }

  @Override
  public String call(String toolInput) {
    // Delegate to original MCP callback (already handles MCP protocol)
    return originalCallback.call(toolInput);
  }
}

