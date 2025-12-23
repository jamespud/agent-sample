package com.github.spud.sample.ai.agent.react.agent;

import com.github.spud.sample.ai.agent.mcp.McpClientManager;
import com.github.spud.sample.ai.agent.mcp.McpToolSynchronizer;
import com.github.spud.sample.ai.agent.react.ReactAgent;
import com.github.spud.sample.ai.agent.state.AgentState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Mono;

/**
 * MCP Agent - 对齐 Python MCPAgent 功能
 * <p>
 * 主要特性： 1. 动态工具集合（从 MCP 服务器） 2. 定期刷新工具列表 3. 检测工具变更并通知 4. 自动清理 MCP 连接
 */
@Slf4j
@SuperBuilder
public class McpAgent extends ToolCallAgent implements ReactAgent {

  private final McpClientManager mcpClientManager;
  private final McpToolSynchronizer mcpToolSynchronizer;

  // 刷新间隔（步数）
  @Getter
  @Setter
  private int refreshToolsInterval = 5;

  // 当前步数计数（用于触发刷新）
  private int stepsSinceLastRefresh = 0;

  // 上次工具快照（用于检测变更）
  private Map<String, ToolDefinition> lastToolSnapshot = new HashMap<>();

  // 启用的 MCP 服务器列表
  @Getter
  @Setter
  private List<String> enabledMcpServers;

//  public McpAgent(String name, String description, String systemPrompt, String nextStepPrompt,
//    ChatClient chatClient, List<Message> messages, Integer maxSteps, Integer duplicateThreshold,
//    McpClientManager mcpClientManager, McpToolSynchronizer mcpToolSynchronizer,
//    @Nullable ToolRegistry toolRegistry, @Nullable ToolChoice toolChoice) {
//    super(name, description, systemPrompt, nextStepPrompt, chatClient, messages, maxSteps,
//      duplicateThreshold, toolRegistry, toolChoice);
//    this.mcpClientManager = mcpClientManager;
//    this.mcpToolSynchronizer = mcpToolSynchronizer;
//  }

  /**
   * 初始化 MCP Agent（扩展父类初始化）
   */
  public void initializeMcp(
    List<String> enabledMcpServers,
    int refreshToolsInterval) {
    this.enabledMcpServers = enabledMcpServers;
    this.refreshToolsInterval = refreshToolsInterval;

    log.info("Initializing MCP Agent with servers: {}, refresh interval: {}",
      enabledMcpServers, refreshToolsInterval);

    // 初始同步工具
    synchronizeTools();

    // 创建初始快照
    captureToolSnapshot();

    // 添加工具信息到系统消息
    addToolInfoToSystemMessages();
  }

  @Override
  protected Mono<Boolean> think() {
    return Mono.fromCallable(() -> {
      // 检查是否需要刷新工具
      if (shouldRefreshTools()) {
        refreshTools();
      }
      return true;
    }).flatMap(ignored -> super.think());
  }

  @Override
  protected Mono<String> act() {
    // 在 ACT 后检查 MCP 服务是否可用
    return super.act()
      .doOnNext(result -> checkMcpServiceAvailability())
      .doOnError(error -> log.error("Error during act phase in MCP Agent", error));
  }

  /**
   * 判断是否需要刷新工具
   */
  private boolean shouldRefreshTools() {
    stepsSinceLastRefresh++;

    if (stepsSinceLastRefresh >= refreshToolsInterval) {
      stepsSinceLastRefresh = 0;
      return true;
    }
    return false;
  }

  /**
   * 刷新工具列表
   */
  private void refreshTools() {
    log.debug("Refreshing MCP tools (step interval reached)");

    try {
      // 同步所有启用的服务器
      int syncedCount = 0;
      for (String serverId : enabledMcpServers) {
        if (mcpClientManager.isConnected(serverId)) {
          syncedCount += mcpToolSynchronizer.synchronize(serverId);
        }
      }

      log.info("Refreshed {} MCP tools", syncedCount);

      // 检测工具变更
      detectToolChanges();

    } catch (Exception e) {
      log.error("Error refreshing MCP tools", e);
    }
  }

  /**
   * 同步所有工具
   */
  private void synchronizeTools() {
    if (enabledMcpServers == null || enabledMcpServers.isEmpty()) {
      log.warn("No MCP servers enabled, synchronizing all connected servers");
      mcpToolSynchronizer.synchronizeAll();
    } else {
      for (String serverId : enabledMcpServers) {
        try {
          if (mcpClientManager.isConnected(serverId)) {
            mcpToolSynchronizer.synchronize(serverId);
          } else {
            log.warn("MCP server not connected: {}", serverId);
          }
        } catch (Exception e) {
          log.error("Failed to synchronize tools from server: {}", serverId, e);
        }
      }
    }
  }

  /**
   * 检测工具变更
   */
  private void detectToolChanges() {
    Map<String, ToolDefinition> currentTools = getCurrentToolMap();

    // 检测新增工具
    List<String> addedTools = currentTools.keySet().stream()
      .filter(name -> !lastToolSnapshot.containsKey(name))
      .collect(java.util.stream.Collectors.toList());

    // 检测移除工具
    List<String> removedTools = lastToolSnapshot.keySet().stream()
      .filter(name -> !currentTools.containsKey(name))
      .collect(java.util.stream.Collectors.toList());

    if (!addedTools.isEmpty()) {
      log.info("New MCP tools available: {}", addedTools);
      String notification = String.format(
        "New tools available: %s", String.join(", ", addedTools));
      this.messages.add(new SystemMessage(notification));
    }

    if (!removedTools.isEmpty()) {
      log.info("MCP tools removed: {}", removedTools);
      String notification = String.format(
        "Tools no longer available: %s", String.join(", ", removedTools));
      this.messages.add(new SystemMessage(notification));
    }

    // 更新快照
    lastToolSnapshot = currentTools;
  }

  /**
   * 获取当前工具映射（过滤 MCP 工具）
   */
  private Map<String, ToolDefinition> getCurrentToolMap() {
    Map<String, ToolDefinition> toolMap = new HashMap<>();

    for (ToolDefinition def : this.toolRegistry.getAllDefinitions()) {
      // 只包含 MCP 工具（带命名空间的）
      if (def.name().contains("::")) {
        toolMap.put(def.name(), def);
      }
    }

    return toolMap;
  }

  /**
   * 捕获工具快照
   */
  private void captureToolSnapshot() {
    lastToolSnapshot = getCurrentToolMap();
    log.debug("Captured tool snapshot: {} tools", lastToolSnapshot.size());
  }

  /**
   * 检查 MCP 服务可用性
   */
  private void checkMcpServiceAvailability() {
    if (enabledMcpServers == null || enabledMcpServers.isEmpty()) {
      return;
    }

    boolean allDisconnected = enabledMcpServers.stream()
      .noneMatch(mcpClientManager::isConnected);

    if (allDisconnected) {
      log.warn("All MCP services are unavailable, ending agent execution");
      this.messages.add(new SystemMessage(
        "MCP service is no longer available, ending interaction"));
      this.state = AgentState.FINISHED;
      this.finalAnswer = "MCP service unavailable - agent terminated";
    }
  }

  /**
   * 添加工具信息到系统消息
   */
  private void addToolInfoToSystemMessages() {
    List<String> mcpToolNames = lastToolSnapshot.keySet().stream()
      .sorted()
      .collect(java.util.stream.Collectors.toList());

    if (!mcpToolNames.isEmpty()) {
      String toolsInfo = String.format(
        "Available MCP tools: %s", String.join(", ", mcpToolNames));
      this.messages.add(new SystemMessage(toolsInfo));
      log.info("Added {} MCP tools to system messages", mcpToolNames.size());
    }
  }

  @Override
  protected void cleanup() {
    super.cleanup();

    log.info("Cleaning up MCP Agent resources");

    // 不主动断开 MCP 连接（由 McpClientManager 生命周期管理）
    // 但可以清理本地状态
    lastToolSnapshot.clear();
    stepsSinceLastRefresh = 0;
  }

}
