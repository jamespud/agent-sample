package com.github.spud.sample.ai.agent.tools;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 工具回调自动注册器
 * <p>
 * 扫描 Spring 容器中所有的 ToolCallback beans（包括由 Spring AI MCP 自动配置产生的）， 并注册到 ToolRegistry 中，实现统一管理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCallbackAutoRegistrar {

  private final ObjectProvider<ToolCallback> toolCallbackProvider;
  private final ToolRegistry toolRegistry;

  @PostConstruct
  public void registerAllToolCallbacks() {
    log.info("Auto-registering ToolCallback beans from Spring container...");

    int registered = 0;
    int skipped = 0;

    for (ToolCallback callback : toolCallbackProvider) {
      try {
        ToolDefinition def = callback.getToolDefinition();

        if (def.name().isBlank()) {
          log.warn("Skipping ToolCallback with null/empty definition: {}",
            callback.getClass().getName());
          skipped++;
          continue;
        }

        // 避免重复注册（ToolRegistry 内部会覆盖同名工具，这里做个检查）
        if (toolRegistry.hasToolByName(def.name())) {
          log.debug("Tool {} already registered, will overwrite", def.name());
        }

        toolRegistry.register(callback);
        registered++;

        log.debug("Auto-registered tool: {} from {}", def.name(),
          callback.getClass().getSimpleName());
      } catch (Exception e) {
        log.error("Failed to register ToolCallback: {} - {}",
          callback.getClass().getName(), e.getMessage(), e);
        skipped++;
      }
    }

    log.info("Auto-registration complete: registered={}, skipped={}, total tools in registry={}",
      registered, skipped, toolRegistry.size());
  }
}
