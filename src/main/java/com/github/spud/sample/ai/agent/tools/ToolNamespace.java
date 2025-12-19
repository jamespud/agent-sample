package com.github.spud.sample.ai.agent.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 工具命名空间规则 用于区分不同来源的工具（本地/MCP server）
 */
@Component
public class ToolNamespace {

  @Value("${app.mcp.namespace.enabled:true}")
  private boolean namespaceEnabled;

  @Value("${app.mcp.namespace.separator:__}")
  private String separator;

  /**
   * 为 MCP 工具生成带命名空间的名称
   */
  public String namespacedName(String serverId, String toolName) {
    if (!namespaceEnabled) {
      return toolName;
    }
    return serverId + separator + toolName;
  }

  /**
   * 解析命名空间名称，返回 [serverId, toolName]
   */
  public String[] parse(String namespacedName) {
    if (!namespaceEnabled || !namespacedName.contains(separator)) {
      return new String[]{null, namespacedName};
    }
    int idx = namespacedName.indexOf(separator);
    return new String[]{
      namespacedName.substring(0, idx),
      namespacedName.substring(idx + separator.length())
    };
  }

  /**
   * 获取 server 前缀（用于批量清理）
   */
  public String getServerPrefix(String serverId) {
    return serverId + separator;
  }

  /**
   * 判断工具名是否属于指定 server
   */
  public boolean belongsToServer(String toolName, String serverId) {
    return namespaceEnabled && toolName.startsWith(getServerPrefix(serverId));
  }

  /**
   * 判断是否为本地工具（无命名空间前缀）
   */
  public boolean isLocalTool(String toolName) {
    return !namespaceEnabled || !toolName.contains(separator);
  }
}
