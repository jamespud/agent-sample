package com.github.spud.sample.ai.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 工具命名空间测试
 */
class ToolNamespaceTest {

  private ToolNamespace namespace;

  @BeforeEach
  void setUp() {
    namespace = new ToolNamespace();
    // 设置测试属性
    ReflectionTestUtils.setField(namespace, "namespaceEnabled", true);
    ReflectionTestUtils.setField(namespace, "separator", "__");
  }

  @Test
  void shouldBuildNamespaceWithServerId() {
    String name = namespace.namespacedName("github-mcp", "search_repos");
    assertEquals("github-mcp__search_repos", name);
  }

  @Test
  void shouldBuildNamespaceForLocalTool() {
    String name = namespace.namespacedName("local", "terminate");
    assertEquals("local__terminate", name);
  }

  @Test
  void shouldParseServerIdFromNamespace() {
    String[] parts = namespace.parse("github-mcp__search_repos");
    assertEquals("github-mcp", parts[0]);
    assertEquals("search_repos", parts[1]);
  }

  @Test
  void shouldHandleNoNamespace() {
    String[] parts = namespace.parse("simple_tool");
    assertNull(parts[0]);
    assertEquals("simple_tool", parts[1]);
  }

  @Test
  void shouldHandleMultipleSeparators() {
    // 工具名本身包含 __
    String[] parts = namespace.parse("server__tool__name");
    assertEquals("server", parts[0]);
    assertEquals("tool__name", parts[1]);
  }

  @Test
  void shouldGetServerPrefix() {
    String prefix = namespace.getServerPrefix("github-mcp");
    assertEquals("github-mcp__", prefix);
  }

  @Test
  void shouldBypassNamespaceWhenDisabled() {
    ReflectionTestUtils.setField(namespace, "namespaceEnabled", false);

    String name = namespace.namespacedName("server", "tool");
    assertEquals("tool", name); // 只返回工具名，无前缀
  }
}
