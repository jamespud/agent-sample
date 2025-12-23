package com.github.spud.sample.ai.agent.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.spud.sample.ai.agent.kernel.protocol.ReactJsonAction;
import com.github.spud.sample.ai.agent.kernel.protocol.ReactJsonParseException;
import com.github.spud.sample.ai.agent.kernel.protocol.ReactJsonParser;
import com.github.spud.sample.ai.agent.kernel.protocol.ReactJsonStep;
import com.github.spud.sample.ai.agent.state.AgentEvent;
import com.github.spud.sample.ai.agent.state.AgentState;
import com.github.spud.sample.ai.agent.state.StateMachineDriver;
import com.github.spud.sample.ai.agent.tools.ToolExecutionService;
import com.github.spud.sample.ai.agent.tools.ToolFilteringService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;

/**
 * Agent 核心编排器 驱动状态机，协调 think/act/terminate 循环
 * <p>
 * 关键设计：<p> - THINK 阶段：使用 NoOp 工具回调，仅判定是否需要工具，不执行<p> - ACT 阶段：使用真正的工具回调执行工具<p> - 问题增强：THINK
 * 前对用户问题进行释义/补充/结构化<p> - RAG 降级：检索失败时注入提示，继续生成文本回答<p>
 */
@Slf4j
@Component
public class AgentKernel {

  private final ChatClient chatClient;
  private final ToolFilteringService toolFilteringService;
  private final ToolExecutionService toolExecutionService;
  private final StateMachineDriver stateMachineDriver;
  private final TerminationPolicy terminationPolicy;
  private final ReactJsonParser reactJsonParser;
  private final QuestionEnhancer questionEnhancer;

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

  @Value("${app.agent.degrade-prefix:未查到相关数据，以下为直接回答：}")
  private String degradePrefix;

  // THINK 阶段的工具使用约束提示
  private static final String THINK_TOOL_CONSTRAINT =
    """
      【重要约束】
      在此阶段，你可以查看可用工具并决定是否需要调用它们。
      - 如果需要使用工具，请输出 tool_calls
      - 如果不需要工具，请直接生成回答文本
      - 当你完成任务后，请调用 terminate 工具并提供最终答案
      """;

  public AgentKernel(ChatClient chatClient, ToolFilteringService toolFilteringService,
    ToolExecutionService toolExecutionService, StateMachineDriver stateMachineDriver,
    TerminationPolicy terminationPolicy, ReactJsonParser reactJsonParser,
    QuestionEnhancer questionEnhancer) {
    this.chatClient = chatClient;
    this.toolFilteringService = toolFilteringService;
    this.toolExecutionService = toolExecutionService;
    this.stateMachineDriver = stateMachineDriver;
    this.terminationPolicy = terminationPolicy;
    this.reactJsonParser = reactJsonParser;
    this.questionEnhancer = questionEnhancer;
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

    try {
      // 注入工具目录与 ReAct JSON 协议提示（按请求过滤可用工具）
      String toolCatalogPrompt = toolFilteringService.buildToolCatalogPrompt(ctx);
      messages.add(new SystemMessage(toolCatalogPrompt));

      // 问题增强
      QuestionEnhancer.EnhancementResult enhancement = questionEnhancer.enhance(chatClient,
        ctx.getUserRequest());
      ctx.setEnhancedQuery(enhancement.getSearchQuery());
      ctx.setEnhancementSummary(questionEnhancer.toSummaryMessage(enhancement));
      log.debug("Question enhanced: original='{}', rephrased='{}'",
        ctx.getUserRequest(), enhancement.getRephrased());

      // 将增强摘要加入消息历史
      messages.add(new SystemMessage(ctx.getEnhancementSummary()));
      messages.add(new UserMessage(ctx.getUserRequest()));

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

      // 处理最终答案
      if (ctx.getFinalAnswer() == null && ctx.getLastAssistantContent() != null) {
        // 如果 RAG 无数据，添加降级前缀
        if (ctx.isRagNoData()) {
          ctx.setFinalAnswer(degradePrefix + ctx.getLastAssistantContent());
        } else {
          ctx.setFinalAnswer(ctx.getLastAssistantContent());
        }
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
   * 执行思考阶段 使用 ReactJsonParser 解析模型输出的 JSON action
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
      // 调用 LLM
      Prompt prompt = new Prompt(messages);
      ChatResponse response = chatClient.prompt(prompt)
        .call()
        .chatResponse();

      AssistantMessage assistantMessage = response.getResult().getOutput();
      String modelText = assistantMessage.getText();
      messages.add(assistantMessage);

      log.debug("Model response text: {}", truncate(modelText, 300));

      // 解析 ReAct JSON
      ReactJsonStep step;
      try {
        step = reactJsonParser.parse(modelText);
      } catch (ReactJsonParseException e) {
        log.warn("Failed to parse ReAct JSON: {} - Original text: {}",
          e.getReason(), truncate(e.getOriginalText(), 200));

        // 注入纠错提示
        String correctionPrompt = String.format(
          "【解析错误】你上一轮的输出无法解析为合法的 ReAct JSON。错误原因：%s\\n\\n" +
            "请严格按照以下格式重新输出（只输出 JSON，不要有其他文字）：\\n" +
            "{\\n" +
            "  \\\"thought\\\": \\\"你的思考过程（≤200字）\\\",\\n" +
            "  \\\"action\\\": {\\\"type\\\":\\\"tool|final|none\\\", ...}\\n" +
            "}",
          e.getReason()
        );
        messages.add(new SystemMessage(correctionPrompt));

        stepBuilder.promptSummary("Parse error: " + e.getReason())
          .error(e.getMessage())
          .durationMs(System.currentTimeMillis() - startTime);
        ctx.addStepRecord(stepBuilder.build());

        // 不发送状态机事件，交由终止策略控制重试
        terminationPolicy.recordEmptyResponse(ctx);
        return;
      }

      // 记录 thought
      ctx.setLastThought(step.getThought());
      ctx.setLastAssistantContent(modelText);

      ReactJsonAction action = step.getAction();
      String actionType = action.getType().toLowerCase();

      log.debug("Parsed action type: {}", actionType);

      switch (actionType) {
        case "tool":
          // 工具调用：暂存到 ctx，进入 ACT 阶段
          ctx.setPendingToolName(action.getName());
          try {
            ctx.setPendingToolArgs(
              new ObjectMapper().writeValueAsString(action.getArgs())
            );
          } catch (Exception e) {
            log.error("Failed to serialize tool args: {}", e.getMessage(), e);
            ctx.setPendingToolArgs("{}");
          }

          // 记录工具调用到 StepRecord
          List<StepRecord.ToolCallRecord> toolCallRecords = List.of(
            StepRecord.ToolCallRecord.builder()
              .id("step-" + ctx.getStepCounter())
              .name(action.getName())
              .arguments(ctx.getPendingToolArgs())
              .build()
          );

          stepBuilder.promptSummary(truncate(step.getThought(), 200))
            .toolCalls(toolCallRecords)
            .durationMs(System.currentTimeMillis() - startTime);
          ctx.addStepRecord(stepBuilder.build());

          // 更新终止策略计数
          terminationPolicy.recordResponse(ctx, modelText, toolCallRecords);

          // 进入 ACT 阶段
          stateMachineDriver.sendEvent(sm, AgentEvent.THINK_DONE_WITH_TOOLS, ctx);
          break;

        case "final":
          // 最终答案
          String finalAnswer = action.getAnswer();

          // 处理 RAG 降级前缀
          if (ctx.isRagNoData() && !finalAnswer.startsWith(degradePrefix)) {
            finalAnswer = degradePrefix + finalAnswer;
          }

          ctx.setFinalAnswer(finalAnswer);
          ctx.setTerminationReason(AgentContext.TerminationReason.COMPLETED);

          stepBuilder.promptSummary(truncate(step.getThought(), 200))
            .durationMs(System.currentTimeMillis() - startTime);
          ctx.addStepRecord(stepBuilder.build());

          terminationPolicy.recordResponse(ctx, modelText, null);

          // 完成
          stateMachineDriver.sendEvent(sm, AgentEvent.THINK_DONE_NO_TOOLS, ctx);
          break;

        case "none":
          // 无动作：注入提示要求选择 tool 或 final
          log.debug("Action type is 'none', injecting correction prompt");
          String nonePrompt =
            "【提示】你上一轮选择了 action.type=none，但你必须采取行动。\\n" +
              "请重新思考并选择：\\n" +
              "- 如果需要工具帮助，使用 {\\\"type\\\":\\\"tool\\\", \\\"name\\\":\\\"...\\\", \\\"args\\\":{...}}\\n"
              +
              "- 如果可以直接回答，使用 {\\\"type\\\":\\\"final\\\", \\\"answer\\\":\\\"...\\\"}";
          messages.add(new SystemMessage(nonePrompt));

          stepBuilder.promptSummary(truncate(step.getThought(), 200))
            .durationMs(System.currentTimeMillis() - startTime);
          ctx.addStepRecord(stepBuilder.build());

          terminationPolicy.recordEmptyResponse(ctx);
          // 不发送事件，交由终止策略控制
          break;

        default:
          log.warn("Unknown action type: {}", actionType);
          stepBuilder.error("Unknown action type: " + actionType)
            .durationMs(System.currentTimeMillis() - startTime);
          ctx.addStepRecord(stepBuilder.build());
          terminationPolicy.recordEmptyResponse(ctx);
          break;
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
   * 执行行动阶段 读取挂起工具执行并注入 Observation JSON
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
      String toolName = ctx.getPendingToolName();
      String toolArgs = ctx.getPendingToolArgs();

      if (toolName == null || toolName.isBlank()) {
        log.warn("No pending tool to execute in ACT phase");
        stateMachineDriver.sendEvent(sm, AgentEvent.ACT_DONE, ctx);
        return;
      }

      log.debug("Executing tool: {} with args: {}", toolName, truncate(toolArgs, 100));

      // 执行工具
      ToolExecutionService.ToolExecutionResult result =
        toolExecutionService.execute(toolName, toolArgs);

      // 构建 Observation JSON
      String observationJson;
      try {
        ObjectMapper mapper =
          new ObjectMapper();
        ObjectNode obsNode = mapper.createObjectNode();
        ObjectNode dataNode = obsNode.putObject("observation");
        dataNode.put("tool", toolName);
        dataNode.put("ok", result.isSuccess());

        if (result.isSuccess()) {
          dataNode.put("result", result.getResult() != null ? result.getResult() : "");
        } else {
          dataNode.put("error", result.getError() != null ? result.getError() : "Unknown error");
        }

        observationJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obsNode);
      } catch (Exception e) {
        log.error("Failed to build observation JSON: {}", e.getMessage(), e);
        observationJson = String.format(
          "{\"observation\":{\"tool\":\"%s\",\"ok\":false,\"error\":\"Failed to format observation\"}}",
          toolName
        );
      }

      // 注入 Observation 到消息历史
      messages.add(new SystemMessage(observationJson));
      log.debug("Injected observation JSON: {}", truncate(observationJson, 300));

      // 检测 RAG 无数据
      if ("retrieve_knowledge".equals(toolName)) {
        String toolResult = result.getResult() != null ? result.getResult() : "";
        if (toolResult.contains("Error retrieving knowledge:")
          || toolResult.equals("No relevant documents found.")
          || toolResult.isBlank()) {
          log.info("RAG returned no data, setting degrade flag");
          ctx.setRagNoData(true);

          // 注入降级提示
          messages.add(new SystemMessage(
            "【提示】知识库检索未返回结果。请直接根据已有上下文与常识回答用户问题。" +
              "在最终答案前加前缀：'" + degradePrefix + "'"
          ));
        }
      }

      // 记录工具结果
      StepRecord.ToolResultRecord resultRecord = toolExecutionService.toResultRecord(
        "step-" + (ctx.getStepCounter() - 1), // 对应上一个 THINK step
        result
      );

      stepBuilder.toolResults(List.of(resultRecord))
        .durationMs(System.currentTimeMillis() - startTime);
      ctx.addStepRecord(stepBuilder.build());

      // 清空挂起动作
      ctx.setPendingToolName(null);
      ctx.setPendingToolArgs(null);

      // 回到 THINK 继续处理
      stateMachineDriver.sendEvent(sm, AgentEvent.ACT_DONE, ctx);

    } catch (Exception e) {
      log.error("Act phase failed: {}", e.getMessage(), e);
      stepBuilder.error(e.getMessage())
        .durationMs(System.currentTimeMillis() - startTime);
      ctx.addStepRecord(stepBuilder.build());

      // 清空挂起动作
      ctx.setPendingToolName(null);
      ctx.setPendingToolArgs(null);

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
