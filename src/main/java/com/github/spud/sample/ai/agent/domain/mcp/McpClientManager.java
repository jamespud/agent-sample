package com.github.spud.sample.ai.agent.domain.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP 客户端管理器（增强版） 支持多服务器、生命周期管理、健康检查
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

  private final McpServersProperties properties;
  private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
  private final Map<String, McpServersProperties.McpServerConfig> serverConfigs = new ConcurrentHashMap<>();

  @PostConstruct
  public void initialize() {
    log.info("Initializing MCP client manager with {} servers", properties.getServers().size());

    for (McpServersProperties.McpServerConfig config : properties.getServers()) {
      if (config.isEnabled()) {
        try {
          connect(config);
          serverConfigs.put(config.getId(), config);
        } catch (Exception e) {
          log.error("Failed to connect to MCP server: {}", config.getId(), e);
        }
      }
    }
  }

  /**
   * 连接到 MCP 服务器
   */
  public void connect(McpServersProperties.McpServerConfig config) {
    String serverId = config.getId();
    log.info("Connecting to MCP server: {} ({})", serverId, config.getTransport());

    McpSyncClient client;
    if ("sse".equalsIgnoreCase(config.getTransport())) {
      client = connectSse(config.getSse());
    } else if ("stdio".equalsIgnoreCase(config.getTransport())) {
      client = connectStdio(config.getStdio());
    } else {
      throw new IllegalArgumentException("Unknown transport type: " + config.getTransport());
    }

    // 初始化客户端
    client.initialize();

    clients.put(serverId, client);
    log.info("Connected to MCP server: {}", serverId);
  }

  private McpSyncClient connectSse(McpServersProperties.SseConfig sseConfig) {
    McpClientTransport transport = HttpClientSseClientTransport.builder(sseConfig.getUrl())
      .build();
    return McpClient.sync(transport).build();
  }

  private McpSyncClient connectStdio(McpServersProperties.StdioConfig stdioConfig) {
    ServerParameters.Builder paramsBuilder = new ServerParameters.Builder(stdioConfig.getCommand());

    if (stdioConfig.getArgs() != null && !stdioConfig.getArgs().isEmpty()) {
      paramsBuilder.args(stdioConfig.getArgs());
    }

    if (stdioConfig.getEnv() != null && !stdioConfig.getEnv().isEmpty()) {
      paramsBuilder.env(stdioConfig.getEnv());
    }

    McpClientTransport transport = new StdioClientTransport(paramsBuilder.build());
    return McpClient.sync(transport).build();
  }

  /**
   * 断开指定服务器连接
   */
  public void disconnect(String serverId) {
    McpSyncClient client = clients.remove(serverId);
    if (client != null) {
      try {
        client.close();
        log.info("Disconnected from MCP server: {}", serverId);
      } catch (Exception e) {
        log.warn("Error closing MCP client: {}", serverId, e);
      }
    }
    serverConfigs.remove(serverId);
  }

  /**
   * 获取客户端
   */
  public Optional<McpSyncClient> getClient(String serverId) {
    return Optional.ofNullable(clients.get(serverId));
  }

  /**
   * 获取服务器配置
   */
  public Optional<McpServersProperties.McpServerConfig> getServerConfig(String serverId) {
    return Optional.ofNullable(serverConfigs.get(serverId));
  }

  /**
   * 获取所有已连接的服务器 ID
   */
  public Collection<String> getConnectedServerIds() {
    return clients.keySet();
  }

  /**
   * 检查服务器是否已连接
   */
  public boolean isConnected(String serverId) {
    return clients.containsKey(serverId);
  }

  /**
   * 重新连接服务器
   */
  public void reconnect(String serverId) {
    disconnect(serverId);
    McpServersProperties.McpServerConfig config = properties.getServers().stream()
      .filter(c -> c.getId().equals(serverId))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverId));
    connect(config);
  }

  @PreDestroy
  public void shutdown() {
    log.info("Shutting down MCP client manager");
    for (String serverId : clients.keySet()) {
      disconnect(serverId);
    }
  }
}
