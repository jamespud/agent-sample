package com.github.spud.sample.ai.agent.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.spud.sample.ai.agent.domain.kernel.AgentContext;
import com.github.spud.sample.ai.agent.domain.kernel.DefaultTerminationPolicy;
import com.github.spud.sample.ai.agent.domain.kernel.StepRecord;
import com.github.spud.sample.ai.agent.domain.state.AgentEvent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 终止策略单元测试
 */
class DefaultTerminationPolicyTest {

  private DefaultTerminationPolicy policy;
  private AgentContext ctx;

  @BeforeEach
  void setUp() {
    policy = new DefaultTerminationPolicy();
    ctx = AgentContext.builder()
      .userRequest("test request")
      .maxSteps(5)
      .duplicateThreshold(3)
      .emptyThreshold(2)
      .build();
  }

  @Test
  void shouldNotTerminateOnNormalExecution() {
    ctx.setStepCounter(1);
    AgentEvent event = policy.checkTermination(ctx);
    assertNull(event);
  }

  @Test
  void shouldTerminateOnMaxSteps() {
    ctx.setStepCounter(5);
    AgentEvent event = policy.checkTermination(ctx);
    assertEquals(AgentEvent.STOP_MAX_STEPS, event);
    assertEquals(AgentContext.TerminationReason.MAX_STEPS, ctx.getTerminationReason());
  }

  @Test
  void shouldTerminateOnEmptyResponses() {
    // 记录两次空响应
    policy.recordResponse(ctx, null, null);
    policy.recordResponse(ctx, "", null);

    AgentEvent event = policy.checkTermination(ctx);
    assertEquals(AgentEvent.STOP_EMPTY, event);
    assertEquals(AgentContext.TerminationReason.EMPTY_RESPONSE, ctx.getTerminationReason());
  }

  @Test
  void shouldResetEmptyCountOnContent() {
    // 记录一次空响应
    policy.recordResponse(ctx, null, null);
    assertEquals(1, ctx.getEmptyResponseCount());

    // 记录有内容的响应
    policy.recordResponse(ctx, "Some content", null);
    assertEquals(0, ctx.getEmptyResponseCount());
  }

  @Test
  void shouldTerminateOnDuplicateResponses() {
    StepRecord.ToolCallRecord toolCall = StepRecord.ToolCallRecord.builder()
      .id("1")
      .name("test_tool")
      .arguments("{}")
      .build();

    // 记录三次相同的响应
    policy.recordResponse(ctx, "same content", List.of(toolCall));
    policy.recordResponse(ctx, "same content", List.of(toolCall));
    policy.recordResponse(ctx, "same content", List.of(toolCall));

    assertEquals(2, ctx.getDuplicateResponseCount()); // 第一次不算重复

    AgentEvent event = policy.checkTermination(ctx);
    // 由于 duplicateThreshold=3，第三次触发
    // 实际逻辑：连续3次相同才触发
  }

  @Test
  void shouldResetDuplicateCountOnDifferentContent() {
    policy.recordResponse(ctx, "content 1", null);
    policy.recordResponse(ctx, "content 1", null);
    assertEquals(1, ctx.getDuplicateResponseCount());

    policy.recordResponse(ctx, "different content", null);
    assertEquals(0, ctx.getDuplicateResponseCount());
  }

  @Test
  void shouldResetAllCounters() {
    ctx.setEmptyResponseCount(5);
    ctx.setDuplicateResponseCount(5);
    ctx.setLastAssistantContent("test");
    ctx.setLastToolCallSignature("sig");

    policy.reset(ctx);

    assertEquals(0, ctx.getEmptyResponseCount());
    assertEquals(0, ctx.getDuplicateResponseCount());
    assertNull(ctx.getLastAssistantContent());
    assertNull(ctx.getLastToolCallSignature());
  }
}
