package com.github.spud.sample.ai.agent.domain.mcp;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP 服务器配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.mcp")
public class McpServersProperties {

  /**
   * MCP 服务器列表
   */
  private List<McpServerConfig> servers = new ArrayList<>();

  /**
   * 命名空间配置
   */
  private NamespaceConfig namespace = new NamespaceConfig();

  @Data
  public static class McpServerConfig {

    /**
     * 服务器 ID（唯一标识）
     */
    private String id;

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 传输类型: sse | stdio
     */
    private String transport = "sse";

    /**
     * SSE 配置
     */
    private SseConfig sse = new SseConfig();

    /**
     * STDIO 配置
     */
    private StdioConfig stdio = new StdioConfig();

    /**
     * 工具刷新配置
     */
    private RefreshConfig refresh = new RefreshConfig();
  }

  @Data
  public static class SseConfig {

    /**
     * SSE 服务器 URL
     */
    private String url = "http://localhost:8000/sse";

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = 30000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = 60000;
  }

  @Data
  public static class StdioConfig {

    /**
     * 命令
     */
    private String command;

    /**
     * 参数列表
     */
    private List<String> args = new ArrayList<>();

    /**
     * 环境变量
     */
    private java.util.Map<String, String> env = new java.util.HashMap<>();
  }

  @Data
  public static class RefreshConfig {

    /**
     * 每隔多少步刷新工具列表
     */
    private int everySteps = 5;
  }

  @Data
  public static class NamespaceConfig {

    /**
     * 是否启用命名空间
     */
    private boolean enabled = true;

    /**
     * 分隔符
     */
    private String separator = "__";
  }
}
