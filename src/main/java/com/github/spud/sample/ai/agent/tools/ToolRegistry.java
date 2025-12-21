package com.github.spud.sample.ai.agent.tools;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * 统一工具注册中心 支持本地工具与 MCP 远程工具的统一注册与解析
 */
@Slf4j
@Component
public class ToolRegistry {

  /**
   * 工具名 -> ToolCallback
   */
  private final Map<String, ToolCallback> callbackMap = new ConcurrentHashMap<>();

  /**
   * 工具名 -> ToolDefinition
   */
  private final Map<String, ToolDefinition> definitionMap = new ConcurrentHashMap<>();

  /**
   * 注册工具
   */
  public void register(String toolName, ToolDefinition definition, ToolCallback callback) {
    log.info("Registering tool: {}", toolName);
    definitionMap.put(toolName, definition);
    callbackMap.put(toolName, callback);
  }

  /**
   * 注册工具（从 ToolCallback 提取 definition）
   */
  public void register(ToolCallback callback) {
    ToolDefinition def = callback.getToolDefinition();
    if (def != null) {
      register(def.name(), def, callback);
    } else {
      log.warn("Cannot register tool without definition: {}", callback);
    }
  }

  /**
   * 注销工具
   */
  public void unregister(String toolName) {
    log.info("Unregistering tool: {}", toolName);
    definitionMap.remove(toolName);
    callbackMap.remove(toolName);
  }

  /**
   * 按前缀注销（用于 MCP server 断开时清理）
   */
  public void unregisterByPrefix(String prefix) {
    log.info("Unregistering tools with prefix: {}", prefix);
    callbackMap.keySet().removeIf(k -> k.startsWith(prefix));
    definitionMap.keySet().removeIf(k -> k.startsWith(prefix));
  }

  /**
   * 获取工具 callback
   */
  public Optional<ToolCallback> getCallback(String toolName) {
    return Optional.ofNullable(callbackMap.get(toolName));
  }

  /**
   * 获取工具定义
   */
  public Optional<ToolDefinition> getDefinition(String toolName) {
    return Optional.ofNullable(definitionMap.get(toolName));
  }

  /**
   * 获取所有工具定义（供模型选择）
   */
  public Collection<ToolDefinition> getAllDefinitions() {
    return definitionMap.values();
  }

  /**
   * 获取所有工具 callback
   */
  public Collection<ToolCallback> getAllCallbacks() {
    return callbackMap.values();
  }

  /**
   * 获取"无操作"工具回调（仅用于 THINK 阶段，让模型输出 toolCalls 但不执行） 这些回调的 call() 方法返回占位符，实际执行在 ACT 阶段使用真正的回调
   */
  public Collection<ToolCallback> getNoOpCallbacks() {
    return definitionMap.entrySet().stream()
      .map(entry -> new NoOpToolCallback(entry.getValue()))
      .collect(java.util.stream.Collectors.toList());
  }

  /**
   * 无操作工具回调包装器
   */
  private static class NoOpToolCallback implements ToolCallback {

    private final ToolDefinition definition;

    public NoOpToolCallback(ToolDefinition definition) {
      this.definition = definition;
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return definition;
    }

    @Override
    public String call(String toolInput) {
      // 占位返回，不执行实际逻辑
      return "[PENDING_EXECUTION]";
    }
  }

  /**
   * 检查工具是否存在
   */
  public boolean hasToolByName(String toolName) {
    return callbackMap.containsKey(toolName);
  }

  /**
   * 获取工具数量
   */
  public int size() {
    return callbackMap.size();
  }

  /**
   * 清空所有工具
   */
  public void clear() {
    log.warn("Clearing all tools from registry");
    callbackMap.clear();
    definitionMap.clear();
  }

  /**
   * 构建 THINK 阶段工具 Schema 提示 汇总所有工具定义为文本形式，供 THINK 阶段注入系统消息
   */
  public String buildThinkToolSchemaPrompt() {
    if (definitionMap.isEmpty()) {
      return "【可用工具清单】\n当前没有可用工具。";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("【可用工具清单】\n");
    sb.append("以下工具可在后续 ACT 阶段执行，THINK 阶段仅用于决策是否需要工具及其参数。\n\n");

    int index = 1;
    for (ToolDefinition def : definitionMap.values()) {
      sb.append(index++).append(". 工具名: ").append(def.name()).append("\n");
      sb.append("   描述: ").append(def.description() != null ? def.description() : "无描述")
        .append("\n");
      sb.append("   输入Schema: ").append(def.inputSchema() != null ? def.inputSchema() : "{}")
        .append("\n\n");
    }

    sb.append("【输出要求】\n");
    sb.append("- 如果需要使用工具，请在响应中包含 tool_calls，指定工具名和参数\n");
    sb.append("- 如果不需要工具，请直接生成文本回答\n");
    sb.append("- 工具的实际执行将在 ACT 阶段进行，THINK 阶段仅做决策\n");

    return sb.toString();
  }
}
