package com.github.spud.sample.ai.agent.kernel;

import com.github.spud.sample.ai.agent.mcp.McpToolSynchronizer;
import com.github.spud.sample.ai.agent.state.AgentEvent;
import com.github.spud.sample.ai.agent.state.AgentState;
import com.github.spud.sample.ai.agent.state.StateMachineDriver;
import com.github.spud.sample.ai.agent.tools.ToolExecutionService;
import com.github.spud.sample.ai.agent.tools.ToolRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Agent 核心编排器 驱动状态机，协调 think/act/terminate 循环
 */
@Slf4j
@Component
public class AgentKernel {

  private final ChatClient chatClient;
  private final ToolRegistry toolRegistry;
  private final ToolExecutionService toolExecutionService;
  private final StateMachineDriver stateMachineDriver;
  private final TerminationPolicy terminationPolicy;
  private final McpToolSynchronizer mcpToolSynchronizer;

  @Value("${app.agent.system-prompt:You are an intelligent AI agent.}")
  private String systemPrompt;

  @Value("${app.agent.next-step-prompt:What is the next step to take?}")
  private String nextStepPrompt;

  @Value("${app.agent.max-steps:15}")
  private int defaultMaxSteps;

  @Value("${app.agent.duplicate-threshold:3}")
  private int defaultDuplicateThreshold;

  @Value("${app.agent.empty-threshold:2}")
  private int defaultEmptyThreshold;

  public AgentKernel(ChatClient chatClient, ToolRegistry toolRegistry,
    ToolExecutionService toolExecutionService, StateMachineDriver stateMachineDriver,
    TerminationPolicy terminationPolicy, McpToolSynchronizer mcpToolSynchronizer) {
    this.chatClient = chatClient;
    this.toolRegistry = toolRegistry;
    this.toolExecutionService = toolExecutionService;
    this.stateMachineDriver = stateMachineDriver;
    this.terminationPolicy = terminationPolicy;
    this.mcpToolSynchronizer = mcpToolSynchronizer;
  }

  /**
   * 执行 Agent
   */
  public AgentResult execute(String userRequest) {
    return execute(AgentContext.builder()
      .userRequest(userRequest)
      .maxSteps(defaultMaxSteps)
      .duplicateThreshold(defaultDuplicateThreshold)
      .emptyThreshold(defaultEmptyThreshold)
      .build());
  }

  /**
   * 执行 Agent（完整上下文）
   */
  public AgentResult execute(AgentContext ctx) {
    log.info("Starting agent execution: traceId={}, request='{}'",
      ctx.getTraceId(), truncate(ctx.getUserRequest(), 100));

    // 创建状态机
    StateMachine<AgentState, AgentEvent> sm = stateMachineDriver.create(ctx.getConversationId());

    // 初始化消息历史
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt));
    messages.add(new UserMessage(ctx.getUserRequest()));

    try {
      // 同步 MCP 工具
      mcpToolSynchronizer.synchronizeAll();

      // 启动状态机
      stateMachineDriver.sendEvent(sm, AgentEvent.START, ctx);
      ctx.setCurrentState(AgentState.THINKING);

      // 主循环
      while (!stateMachineDriver.isInFinalState(sm)) {
        AgentState currentState = stateMachineDriver.getCurrentState(sm);
        ctx.setCurrentState(currentState);

        // 检查终止条件
        AgentEvent terminationEvent = terminationPolicy.checkTermination(ctx);
        if (terminationEvent != null) {
          log.info("Termination triggered: {}", terminationEvent);
          stateMachineDriver.sendEvent(sm, terminationEvent, ctx);
          break;
        }

        // 执行当前状态的动作
        if (currentState == AgentState.THINKING) {
          executeThink(ctx, sm, messages);
        } else if (currentState == AgentState.ACTING) {
          executeAct(ctx, sm, messages);
        }
      }

      // 设置结束时间
      ctx.setEndTime(Instant.now());
      ctx.setCurrentState(stateMachineDriver.getCurrentState(sm));

      if (ctx.getFinalAnswer() == null && ctx.getLastAssistantContent() != null) {
        ctx.setFinalAnswer(ctx.getLastAssistantContent());
      }

      if (ctx.getTerminationReason() == null) {
        ctx.setTerminationReason(AgentContext.TerminationReason.COMPLETED);
      }

      return AgentResult.fromContext(ctx);

    } catch (Exception e) {
      log.error("Agent execution failed: {}", e.getMessage(), e);
      stateMachineDriver.sendEvent(sm, AgentEvent.FAIL, ctx);
      return AgentResult.error(ctx, e.getMessage());

    } finally {
      stateMachineDriver.stop(sm);
    }
  }

  /**
   * 执行思考阶段
   */
  private void executeThink(AgentContext ctx, StateMachine<AgentState, AgentEvent> sm,
    List<Message> messages) {
    long startTime = System.currentTimeMillis();
    ctx.incrementStep();

    log.debug("Step {}: THINKING", ctx.getStepCounter());

    StepRecord.StepRecordBuilder stepBuilder = StepRecord.builder()
      .stepNumber(ctx.getStepCounter())
      .state("THINKING")
      .timestamp(Instant.now());

    try {
      // 构建 prompt，包含可用工具
      List<ToolCallback> tools = new ArrayList<>(toolRegistry.getAllCallbacks());
      Prompt prompt = new Prompt(messages);

      // 调用 LLM
      ChatResponse response = chatClient.prompt(prompt)
        .toolCallbacks(tools.toArray(new ToolCallback[0]))
        .call()
        .chatResponse();

      AssistantMessage assistantMessage = response.getResult().getOutput();
      messages.add(assistantMessage);

      // 记录响应
      String content = assistantMessage.getText();
      List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

      List<StepRecord.ToolCallRecord> toolCallRecords = null;
      if (!toolCalls.isEmpty()) {
        toolCallRecords = toolCalls.stream()
          .map(tc -> StepRecord.ToolCallRecord.builder()
            .id(tc.id())
            .name(tc.name())
            .arguments(tc.arguments())
            .build())
          .collect(Collectors.toList());
      }

      // 更新终止策略计数
      terminationPolicy.recordResponse(ctx, content, toolCallRecords);

      stepBuilder.promptSummary(truncate(content, 200))
        .toolCalls(toolCallRecords)
        .durationMs(System.currentTimeMillis() - startTime);

      ctx.addStepRecord(stepBuilder.build());

      // 决定下一步
      if (!toolCalls.isEmpty()) {
        stateMachineDriver.sendEvent(sm, AgentEvent.THINK_DONE_WITH_TOOLS, ctx);
      } else {
        // 无工具调用，检查是否有内容
        if (!StringUtils.hasText(content)) {
          ctx.setFinalAnswer(content);
        }
        stateMachineDriver.sendEvent(sm, AgentEvent.THINK_DONE_NO_TOOLS, ctx);
      }

    } catch (Exception e) {
      log.error("Think phase failed: {}", e.getMessage(), e);
      stepBuilder.error(e.getMessage())
        .durationMs(System.currentTimeMillis() - startTime);
      ctx.addStepRecord(stepBuilder.build());
      stateMachineDriver.sendEvent(sm, AgentEvent.FAIL, ctx);
    }
  }

  /**
   * 执行行动阶段
   */
  private void executeAct(AgentContext ctx, StateMachine<AgentState, AgentEvent> sm,
    List<Message> messages) {
    long startTime = System.currentTimeMillis();

    log.debug("Step {}: ACTING", ctx.getStepCounter());

    StepRecord.StepRecordBuilder stepBuilder = StepRecord.builder()
      .stepNumber(ctx.getStepCounter())
      .state("ACTING")
      .timestamp(Instant.now());

    try {
      // 获取最后一条 assistant 消息的工具调用
      Message lastMessage = messages.get(messages.size() - 1);
      if (!(lastMessage instanceof AssistantMessage assistantMessage)) {
        log.warn("Expected AssistantMessage but got: {}", lastMessage.getClass());
        stateMachineDriver.sendEvent(sm, AgentEvent.ACT_DONE, ctx);
        return;
      }

      List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
      if (toolCalls.isEmpty()) {
        stateMachineDriver.sendEvent(sm, AgentEvent.ACT_DONE, ctx);
        return;
      }

      List<StepRecord.ToolResultRecord> resultRecords = new ArrayList<>();
      List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
      boolean terminateTriggered = false;

      for (AssistantMessage.ToolCall toolCall : toolCalls) {
        String toolName = toolCall.name();
        String arguments = toolCall.arguments();

        log.debug("Executing tool: {} with args: {}", toolName, truncate(arguments, 100));

        // 执行工具
        ToolExecutionService.ToolExecutionResult result = toolExecutionService.execute(toolName,
          arguments);

        // 记录结果
        StepRecord.ToolResultRecord resultRecord = toolExecutionService.toResultRecord(
          toolCall.id(), result);
        resultRecords.add(resultRecord);

        // 构建响应
        toolResponses.add(new ToolResponseMessage.ToolResponse(
          toolCall.id(),
          toolName,
          result.getResult() != null ? result.getResult() : ""
        ));

        // 检查是否是 terminate 工具
        if ("terminate".equals(toolName)) {
          terminateTriggered = true;
          // 解析最终答案
          String answer = result.getResult();
          if (answer != null && answer.startsWith("TERMINATE:")) {
            answer = answer.substring("TERMINATE:".length());
            try {
              var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(answer);
              ctx.setFinalAnswer(node.path("answer").asText());
            } catch (Exception e) {
              ctx.setFinalAnswer(answer);
            }
          }
          ctx.setTerminationReason(AgentContext.TerminationReason.TOOL_TERMINATE);
        }
      }

      // 添加工具响应到消息历史
      messages.add(new ToolResponseMessage(toolResponses));

      stepBuilder.toolResults(resultRecords)
        .durationMs(System.currentTimeMillis() - startTime);
      ctx.addStepRecord(stepBuilder.build());

      // 决定下一步
      if (terminateTriggered) {
        stateMachineDriver.sendEvent(sm, AgentEvent.TOOL_TERMINATE, ctx);
      } else {
        stateMachineDriver.sendEvent(sm, AgentEvent.ACT_DONE, ctx);
      }

    } catch (Exception e) {
      log.error("Act phase failed: {}", e.getMessage(), e);
      stepBuilder.error(e.getMessage())
        .durationMs(System.currentTimeMillis() - startTime);
      ctx.addStepRecord(stepBuilder.build());
      stateMachineDriver.sendEvent(sm, AgentEvent.FAIL, ctx);
    }
  }

  private String truncate(String text, int maxLen) {
    if (text == null) {
      return null;
    }
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
  }
}
