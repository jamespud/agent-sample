package com.github.spud.sample.ai.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试 - 需要完整 Spring 上下文 使用 test profile 禁用外部依赖
 */
@SpringBootTest
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "需要外部服务")
class AgentSampleApplicationTests {

  @Test
  void contextLoads() {
    // 验证上下文能够成功加载
  }

}
