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
}
