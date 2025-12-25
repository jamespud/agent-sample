package com.github.spud.sample.ai.agent.domain.react.agent;

import com.github.spud.sample.ai.agent.domain.mcp.McpClientManager;
import com.github.spud.sample.ai.agent.domain.mcp.McpToolCallback;
import com.github.spud.sample.ai.agent.domain.state.AgentState;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Mono;

/**
 * MCP Agent - extends ToolCallAgent with MCP tool capabilities
 *
 * Key features:
 * 1. Dynamic tool set from MCP servers
 * 2. Periodic tool refresh
 * 3. MCP server availability monitoring
 */
@Slf4j
@Getter
@SuperBuilder
public class McpAgent extends ToolCallAgent {

  private final McpClientManager mcpClientManager;

  // Refresh interval (in steps)
  @Builder.Default
  private int refreshToolsInterval = 5;

  // Step counter for triggering refresh
  @Builder.Default
  private int stepsSinceLastRefresh = 0;

  // Enabled MCP servers list
  private List<String> enabledMcpServers;

  /**
   * Initialize MCP Agent with enabled servers
   */
  public void initializeMcp(List<String> enabledMcpServers, int refreshToolsInterval) {
    this.enabledMcpServers = enabledMcpServers;
    this.refreshToolsInterval = refreshToolsInterval;

    log.info("Initializing MCP Agent with servers: {}, refresh interval: {}",
      enabledMcpServers, refreshToolsInterval);

    // Build MCP tool callbacks and inject into availableCallbacks
    buildMcpCallbacks();

    // Add tool info to system messages
    addToolInfoToSystemMessages();
  }

  /**
   * Build MCP tool callbacks from enabled servers
   */
  private void buildMcpCallbacks() {
    if (enabledMcpServers == null || enabledMcpServers.isEmpty()) {
      log.warn("No MCP servers enabled");
      return;
    }

    List<ToolCallback> mcpCallbacks = new ArrayList<>();

    for (String serverId : enabledMcpServers) {
      if (!mcpClientManager.isConnected(serverId)) {
        log.warn("MCP server not connected: {}", serverId);
        continue;
      }

      // Get original callbacks and wrap them with MCP naming
      List<ToolCallback> originalCallbacks = mcpClientManager.listToolCallbacks(serverId);
      for (ToolCallback originalCallback : originalCallbacks) {
        McpToolCallback wrappedCallback = McpToolCallback.wrap(serverId, originalCallback);
        mcpCallbacks.add(wrappedCallback);
      }
    }

    log.info("Built {} MCP tool callbacks from {} servers",
      mcpCallbacks.size(), enabledMcpServers.size());

    // Combine local tools (from toolRegistry) with MCP tools
    List<ToolCallback> localCallbacks = new ArrayList<>(toolRegistry.getAllCallbacks());
    List<ToolCallback> combinedCallbacks = new ArrayList<>(localCallbacks);
    combinedCallbacks.addAll(mcpCallbacks);

    this.availableCallbacks = combinedCallbacks;
  }

  @Override
  protected Mono<Boolean> think() {
    return Mono.fromCallable(() -> {
      // Check if tools need refresh
      if (shouldRefreshTools()) {
        refreshTools();
      }
      return true;
    }).flatMap(ignored -> super.think());
  }

  @Override
  protected Mono<String> act() {
    // Check MCP service availability after ACT
    return super.act()
      .doOnNext(result -> checkMcpServiceAvailability())
      .doOnError(error -> log.error("Error during act phase in MCP Agent", error));
  }

  /**
   * Determine if tools need refresh
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
   * Refresh tool list
   */
  private void refreshTools() {
    log.debug("Refreshing MCP tools (step interval reached)");

    try {
      // Rebuild MCP callbacks
      buildMcpCallbacks();

      log.info("Refreshed MCP tools, total available callbacks: {}", availableCallbacks.size());

    } catch (Exception e) {
      log.error("Error refreshing MCP tools", e);
    }
  }

  /**
   * Check MCP service availability
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
   * Add tool info to system messages
   */
  private void addToolInfoToSystemMessages() {
    long mcpToolCount = availableCallbacks.stream()
      .filter(cb -> cb.getToolDefinition().name().startsWith("mcp."))
      .count();

    if (mcpToolCount > 0) {
      String toolsInfo = String.format(
        "You have access to %d MCP tools from enabled servers.", mcpToolCount);
      this.messages.add(new SystemMessage(toolsInfo));
      log.info("Added {} MCP tools to system messages", mcpToolCount);
    }
  }

  @Override
  protected void cleanup() {
    super.cleanup();

    log.info("Cleaning up MCP Agent resources");

    // Don't actively disconnect MCP connections (managed by McpClientManager lifecycle)
    // But clean up local state
    stepsSinceLastRefresh = 0;
  }
}
