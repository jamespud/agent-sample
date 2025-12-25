package com.github.spud.sample.ai.agent.domain.mcp;

import lombok.experimental.UtilityClass;

/**
 * Utility for MCP tool naming conventions
 * 
 * Naming format: mcp.<serverId>.<toolName>
 * 
 * Normalization rules:
 * - serverId and toolName must NOT contain '.' (dots)
 * - If they do, dots are replaced with '_' (underscores)
 */
@UtilityClass
public class McpToolNaming {

  private static final String MCP_PREFIX = "mcp.";
  private static final String SEPARATOR = ".";
  private static final String DOT_REPLACEMENT = "_";

  /**
   * Generate model-visible tool name: mcp.<serverId>.<toolName>
   */
  public static String toModelToolName(String serverId, String toolName) {
    String normalizedServerId = normalize(serverId);
    String normalizedToolName = normalize(toolName);
    return MCP_PREFIX + normalizedServerId + SEPARATOR + normalizedToolName;
  }

  /**
   * Parse model tool name to extract serverId and toolName
   * Returns null if not a valid MCP tool name
   */
  public static McpToolIdentifier parseModelToolName(String modelToolName) {
    if (modelToolName == null || !modelToolName.startsWith(MCP_PREFIX)) {
      return null;
    }

    String withoutPrefix = modelToolName.substring(MCP_PREFIX.length());
    int separatorIndex = withoutPrefix.indexOf(SEPARATOR);

    if (separatorIndex <= 0 || separatorIndex >= withoutPrefix.length() - 1) {
      return null;
    }

    String serverId = denormalize(withoutPrefix.substring(0, separatorIndex));
    String toolName = denormalize(withoutPrefix.substring(separatorIndex + 1));

    return new McpToolIdentifier(serverId, toolName);
  }

  /**
   * Check if a tool name is an MCP tool (starts with mcp. prefix)
   */
  public static boolean isMcpTool(String toolName) {
    return toolName != null && toolName.startsWith(MCP_PREFIX);
  }

  /**
   * Normalize serverId/toolName by replacing dots with underscores
   */
  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.replace(".", DOT_REPLACEMENT);
  }

  /**
   * Denormalize by replacing underscores back to dots (if needed)
   * For now, keep as-is since we normalize on input
   */
  private static String denormalize(String value) {
    // Keep normalized form as actual server/tool identifiers
    return value;
  }

  /**
   * Parsed MCP tool identifier
   */
  public static record McpToolIdentifier(String serverId, String toolName) {
  }
}
