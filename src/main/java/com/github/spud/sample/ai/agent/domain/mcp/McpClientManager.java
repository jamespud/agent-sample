package com.github.spud.sample.ai.agent.domain.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * MCP 客户端管理器（增强版） 支持多服务器、生命周期管理、健康检查
 * 
 * Note: MCP tools are NOT registered to ToolRegistry. They are dynamically injected
 * into McpAgent only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

  private final List<McpSyncClient> clients;

  // name (mcp client 配置中的key) -> client
  private final Map<String, McpSyncClient> clientMap = new ConcurrentHashMap<>();

  @PostConstruct
  public void initialize() {
    for (McpSyncClient client : clients) {
      clientMap.put(client.getServerInfo().name(), client);
      if (!client.isInitialized()) {
        client.isInitialized();
      }
    }
  }

  /**
   * 获取客户端
   */
  public Optional<McpSyncClient> getClient(String serverId) {
    return Optional.ofNullable(clientMap.get(serverId));
  }

  /**
   * 列出指定服务器的所有工具回调（用于 McpAgent 注入）
   */
  public List<ToolCallback> listToolCallbacks(String serverId) {
    McpSyncClient client = getClient(serverId).orElseThrow(
      () -> new IllegalArgumentException("MCP server not found: " + serverId));
    return McpToolUtils.getToolCallbacksFromSyncClients(client);
  }

  /**
   * 聚合多个服务器的工具回调
   */
  public List<ToolCallback> listToolCallbacks(List<String> serverIds) {
    return serverIds.stream()
      .filter(this::isConnected)
      .flatMap(serverId -> listToolCallbacks(serverId).stream())
      .toList();
  }

  /**
   * 获取所有已连接的服务器 ID
   */
  public Collection<String> getConnectedServerIds() {
    return clientMap.keySet();
  }

  /**
   * 检查服务器是否已连接
   */
  public boolean isConnected(String serverId) {
    return clientMap.containsKey(serverId) && clientMap.get(serverId).isInitialized();
  }

  @PreDestroy
  public void shutdown() {
    log.info("Shutting down MCP client manager");
    for (McpSyncClient client : clientMap.values()) {
      client.closeGracefully();
    }
  }
}
