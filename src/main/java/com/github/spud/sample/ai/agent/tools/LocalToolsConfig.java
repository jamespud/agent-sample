package com.github.spud.sample.ai.agent.tools;

import com.github.spud.sample.ai.agent.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.annotation.Configuration;

/**
 * 本地工具配置 注册系统内置工具
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LocalToolsConfig {

  private final ToolRegistry toolRegistry;

  @PostConstruct
  public void registerLocalTools() {
    log.info("Registering local tools...");

    // 注册 terminate 工具
    registerTerminateTool();

    // 注册 time 工具
    registerTimeTool();

    // 注册 echo 工具
    registerEchoTool();

    log.info("Local tools registered: {}", toolRegistry.size());
  }

  private void registerTerminateTool() {
    ToolDefinition def = DefaultToolDefinition.builder()
      .name("terminate")
      .description(
        "Terminate the agent with a final answer. Call this when you have completed the task or gathered enough information to provide a final response.")
      .inputSchema("""
        {
            "type": "object",
            "properties": {
                "answer": {
                    "type": "string",
                    "description": "The final answer to return to the user"
                }
            },
            "required": ["answer"]
        }
        """)
      .build();

    ToolCallback callback = new ToolCallback() {
      @Override
      public ToolDefinition getToolDefinition() {
        return def;
      }

      @Override
      public String call(String toolInput) {
        // 返回输入作为最终答案标记
        return "TERMINATE:" + toolInput;
      }
    };

    toolRegistry.register("terminate", def, callback);
  }

  private void registerTimeTool() {
    ToolDefinition def = DefaultToolDefinition.builder()
      .name("get_current_time")
      .description("Get the current date and time")
      .inputSchema("""
        {
            "type": "object",
            "properties": {}
        }
        """)
      .build();

    ToolCallback callback = new ToolCallback() {
      @Override
      public ToolDefinition getToolDefinition() {
        return def;
      }

      @Override
      public String call(String toolInput) {
        return LocalDateTime.now().toString();
      }
    };

    toolRegistry.register("get_current_time", def, callback);
  }

  private void registerEchoTool() {
    ToolDefinition def = DefaultToolDefinition.builder()
      .name("echo")
      .description("Echo back the input message. Useful for testing.")
      .inputSchema("""
        {
            "type": "object",
            "properties": {
                "message": {
                    "type": "string",
                    "description": "The message to echo back"
                }
            },
            "required": ["message"]
        }
        """)
      .build();

    ToolCallback callback = new ToolCallback() {
      @Override
      public ToolDefinition getToolDefinition() {
        return def;
      }

      @Override
      public String call(String toolInput) {
        // 简单解析 JSON 获取 message
        try {
          var node = JsonUtils.readTree(toolInput);
          return "Echo: " + node.path("message").asText();
        } catch (Exception e) {
          return "Echo: " + toolInput;
        }
      }
    };

    toolRegistry.register("echo", def, callback);
  }
}
