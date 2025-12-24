package com.github.spud.sample.ai.agent.domain.kernel;

import com.github.spud.sample.ai.agent.domain.state.AgentEvent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

/**
 * 默认终止策略实现
 */
@Component
public class DefaultTerminationPolicy implements TerminationPolicy {

  @Override
  public AgentEvent checkTermination(AgentContext ctx) {
    // 1. 检查最大步数
    if (ctx.getStepCounter() >= ctx.getMaxSteps()) {
      ctx.setTerminationReason(AgentContext.TerminationReason.MAX_STEPS);
      return AgentEvent.STOP_MAX_STEPS;
    }

    // 2. 检查连续空响应
    if (ctx.getEmptyResponseCount() >= ctx.getEmptyThreshold()) {
      ctx.setTerminationReason(AgentContext.TerminationReason.EMPTY_RESPONSE);
      return AgentEvent.STOP_EMPTY;
    }

    // 3. 检查连续重复响应
    if (ctx.getDuplicateResponseCount() >= ctx.getDuplicateThreshold()) {
      ctx.setTerminationReason(AgentContext.TerminationReason.DUPLICATE_RESPONSE);
      return AgentEvent.STOP_DUPLICATE;
    }

    return null; // 继续执行
  }

  @Override
  public void recordEmptyResponse(AgentContext ctx) {
    ctx.setEmptyResponseCount(ctx.getEmptyResponseCount() + 1);
  }

  @Override
  public void recordResponse(AgentContext ctx, String content,
    List<StepRecord.ToolCallRecord> toolCalls) {
    // 计算当前响应签名
    String signature = computeSignature(content, toolCalls);

    // 检查是否与上次相同
    if (signature.equals(ctx.getLastToolCallSignature())) {
      ctx.setDuplicateResponseCount(ctx.getDuplicateResponseCount() + 1);
    } else {
      ctx.setDuplicateResponseCount(0); // 重置
    }

    // 更新最后签名
    ctx.setLastToolCallSignature(signature);
    ctx.setLastAssistantContent(content);

    // 检查是否空响应
    if (!StringUtils.hasText(content) && (toolCalls == null || toolCalls.isEmpty())) {
      recordEmptyResponse(ctx);
    } else {
      ctx.setEmptyResponseCount(0); // 有内容则重置空计数
    }
  }

  @Override
  public void reset(AgentContext ctx) {
    ctx.setEmptyResponseCount(0);
    ctx.setDuplicateResponseCount(0);
    ctx.setLastAssistantContent(null);
    ctx.setLastToolCallSignature(null);
  }

  /**
   * 计算响应签名（用于重复检测）
   */
  private String computeSignature(String content, List<StepRecord.ToolCallRecord> toolCalls) {
    StringBuilder sb = new StringBuilder();

    if (StringUtils.hasText(content)) {
      sb.append("content:").append(content.trim());
    }

    if (toolCalls != null && !toolCalls.isEmpty()) {
      String toolsSig = toolCalls.stream()
        .map(tc -> tc.getName() + ":" + tc.getArguments())
        .sorted()
        .collect(Collectors.joining("|"));
      sb.append("tools:").append(toolsSig);
    }

    if (sb.length() == 0) {
      return "EMPTY";
    }

    return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
  }
}
