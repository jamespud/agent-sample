package com.github.spud.sample.ai.agent.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.spud.sample.ai.agent.domain.kernel.AgentKernel;
import com.github.spud.sample.ai.agent.domain.kernel.AgentResult;
import com.github.spud.sample.ai.agent.domain.kernel.DefaultTerminationPolicy;
import com.github.spud.sample.ai.agent.domain.kernel.QuestionEnhancer;
import com.github.spud.sample.ai.agent.domain.kernel.StepRecord;
import com.github.spud.sample.ai.agent.domain.kernel.TerminationPolicy;
import com.github.spud.sample.ai.agent.domain.kernel.protocol.ReactJsonParser;
import com.github.spud.sample.ai.agent.domain.state.AgentEvent;
import com.github.spud.sample.ai.agent.domain.state.AgentState;
import com.github.spud.sample.ai.agent.domain.state.StateMachineDriver;
import com.github.spud.sample.ai.agent.domain.tools.ToolExecutionService;
import com.github.spud.sample.ai.agent.domain.tools.ToolFilteringService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.statemachine.StateMachine;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AgentKernel 流程测试：ReAct JSON 协议驱动
 */
@ExtendWith(MockitoExtension.class)
class AgentKernelFlowTest {

  @Mock
  private ChatClient chatClient;

  @Mock
  private ChatClient.ChatClientRequestSpec requestSpec;

  @Mock
  private ChatClient.CallResponseSpec callResponseSpec;

  @Mock
  private ToolFilteringService toolFilteringService;

  @Mock
  private ToolExecutionService toolExecutionService;

  @Mock
  private StateMachineDriver stateMachineDriver;

  @Mock
  private QuestionEnhancer questionEnhancer;

  @Mock
  private StateMachine<AgentState, AgentEvent> stateMachine;

  private ReactJsonParser reactJsonParser;
  private TerminationPolicy terminationPolicy;
  private AgentKernel agentKernel;

  @BeforeEach
  void setUp() {
    // 使用真实的解析器与终止策略
    reactJsonParser = new ReactJsonParser();
    terminationPolicy = new DefaultTerminationPolicy();

    agentKernel = new AgentKernel(
      chatClient,
      toolFilteringService,
      toolExecutionService,
      stateMachineDriver,
      terminationPolicy,
      reactJsonParser,
      questionEnhancer
    );

    // 设置字段值
    ReflectionTestUtils.setField(agentKernel, "systemPrompt", "You are an AI agent.");
    ReflectionTestUtils.setField(agentKernel, "nextStepPrompt", "What's next?");
    ReflectionTestUtils.setField(agentKernel, "defaultMaxSteps", 15);
    ReflectionTestUtils.setField(agentKernel, "defaultDuplicateThreshold", 3);
    ReflectionTestUtils.setField(agentKernel, "defaultEmptyThreshold", 2);
    ReflectionTestUtils.setField(agentKernel, "degradePrefix", "未查到相关数据，以下为直接回答：");

    // 默认行为
    when(stateMachineDriver.create(any())).thenReturn(stateMachine);
    when(stateMachineDriver.getCurrentState(stateMachine)).thenReturn(AgentState.THINKING);
    when(stateMachineDriver.isInFinalState(stateMachine)).thenReturn(false, true);

    // ToolFilteringService 默认返回工具清单提示
    when(toolFilteringService.buildToolCatalogPrompt(any())).thenReturn(
      "【可用工具清单】\n...\n【ReAct 协议要求】\n..."
    );

    // QuestionEnhancer 默认返回
    QuestionEnhancer.EnhancementResult enhancement = QuestionEnhancer.EnhancementResult.builder()
      .original("original question")
      .rephrased("enhanced question")
      .keywords(List.of("key"))
      .constraints(new HashMap<>())
      .build();
    when(questionEnhancer.enhance(any(), any())).thenReturn(enhancement);
    when(questionEnhancer.toSummaryMessage(any())).thenReturn("Enhanced summary");
  }

  @Test
  void shouldCompleteWithFinalAction() {
    // Arrange: THINK 返回 final action JSON
    String finalJson = """
      {
        "thought": "I can answer directly",
        "action": {
          "type": "final",
          "answer": "This is the final answer."
        }
      }
      """;

    AssistantMessage responseMessage = new AssistantMessage(finalJson);
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(responseMessage)));

    when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
    when(callResponseSpec.chatResponse()).thenReturn(chatResponse);

    // 状态机：THINKING -> 发送 THINK_DONE_NO_TOOLS -> FINISHED
    when(stateMachineDriver.getCurrentState(stateMachine))
      .thenReturn(AgentState.THINKING)
      .thenReturn(AgentState.FINISHED);
    when(stateMachineDriver.isInFinalState(stateMachine))
      .thenReturn(false)
      .thenReturn(true);

    // Act
    AgentResult result = agentKernel.execute("hello");

    // Assert
    assertThat(result.getAnswer()).isEqualTo("This is the final answer.");
    assertThat(result.isSuccess()).isTrue();

    // 验证未调用工具执行
    verify(toolExecutionService, never()).execute(any(), any());

    // 验证状态转移
    verify(stateMachineDriver).sendEvent(eq(stateMachine), eq(AgentEvent.START), any());
    verify(stateMachineDriver).sendEvent(eq(stateMachine), eq(AgentEvent.THINK_DONE_NO_TOOLS),
      any());
  }

  @Test
  void shouldExecuteToolAndReturnFinalAnswer() {
    // Arrange: 两轮 THINK/ACT
    // 第1轮 THINK：返回 tool action JSON
    String toolJson = """
      {
        "thought": "Need to retrieve information",
        "action": {
          "type": "tool",
          "name": "retrieve_knowledge",
          "args": {"query": "test query"}
        }
      }
      """;

    // 第2轮 THINK：返回 final action JSON
    String finalJson = """
      {
        "thought": "Based on retrieved info",
        "action": {
          "type": "final",
          "answer": "Final answer based on tool result"
        }
      }
      """;

    AssistantMessage thinkMessage1 = new AssistantMessage(toolJson);
    AssistantMessage thinkMessage2 = new AssistantMessage(finalJson);

    ChatResponse thinkResponse1 = new ChatResponse(List.of(new Generation(thinkMessage1)));
    ChatResponse thinkResponse2 = new ChatResponse(List.of(new Generation(thinkMessage2)));

    // 模拟两次 THINK 调用
    when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
    when(callResponseSpec.chatResponse())
      .thenReturn(thinkResponse1)  // 第1次 THINK
      .thenReturn(thinkResponse2); // 第2次 THINK

    // 状态机转移：THINKING -> ACTING -> THINKING -> FINISHED
    when(stateMachineDriver.getCurrentState(stateMachine))
      .thenReturn(AgentState.THINKING)  // 第1次 THINK
      .thenReturn(AgentState.ACTING)    // ACT
      .thenReturn(AgentState.THINKING)  // 第2次 THINK
      .thenReturn(AgentState.FINISHED); // 结束

    when(stateMachineDriver.isInFinalState(stateMachine))
      .thenReturn(false, false, false, true);

    // ACT 阶段：工具执行返回成功
    ToolExecutionService.ToolExecutionResult toolResult = ToolExecutionService.ToolExecutionResult.builder()
      .toolName("retrieve_knowledge")
      .arguments("{\"query\":\"test query\"}")
      .result("Found relevant documents")
      .success(true)
      .durationMs(100)
      .timestamp(Instant.now())
      .build();

    when(toolExecutionService.execute("retrieve_knowledge", "{\"query\":\"test query\"}"))
      .thenReturn(toolResult);

    StepRecord.ToolResultRecord resultRecord = StepRecord.ToolResultRecord.builder()
      .toolCallId("step-1")
      .toolName("retrieve_knowledge")
      .result("Found relevant documents")
      .success(true)
      .build();

    when(toolExecutionService.toResultRecord(anyString(), eq(toolResult)))
      .thenReturn(resultRecord);

    // Act
    AgentResult result = agentKernel.execute("test question");

    // Assert
    assertThat(result.getAnswer()).isEqualTo("Final answer based on tool result");
    assertThat(result.isSuccess()).isTrue();

    // 验证工具执行仅在 ACT 阶段调用
    verify(toolExecutionService, times(1)).execute("retrieve_knowledge",
      "{\"query\":\"test query\"}");

    // 验证状态转移
    verify(stateMachineDriver).sendEvent(eq(stateMachine), eq(AgentEvent.START), any());
    verify(stateMachineDriver).sendEvent(eq(stateMachine), eq(AgentEvent.THINK_DONE_WITH_TOOLS),
      any());
    verify(stateMachineDriver).sendEvent(eq(stateMachine), eq(AgentEvent.ACT_DONE), any());
    verify(stateMachineDriver).sendEvent(eq(stateMachine), eq(AgentEvent.THINK_DONE_NO_TOOLS),
      any());
  }

  @Test
  void shouldApplyDegradePrefixOnRagFailure() {
    // Arrange: RAG 工具失败，最终答案应带降级前缀
    String toolJson = """
      {
        "thought": "Try to retrieve",
        "action": {
          "type": "tool",
          "name": "retrieve_knowledge",
          "args": {"query": "test"}
        }
      }
      """;

    String finalJson = """
      {
        "thought": "No data found, answer directly",
        "action": {
          "type": "final",
          "answer": "Direct answer without RAG"
        }
      }
      """;

    AssistantMessage thinkMessage1 = new AssistantMessage(toolJson);
    AssistantMessage thinkMessage2 = new AssistantMessage(finalJson);

    ChatResponse thinkResponse1 = new ChatResponse(List.of(new Generation(thinkMessage1)));
    ChatResponse thinkResponse2 = new ChatResponse(List.of(new Generation(thinkMessage2)));

    when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
    when(callResponseSpec.chatResponse())
      .thenReturn(thinkResponse1)
      .thenReturn(thinkResponse2);

    when(stateMachineDriver.getCurrentState(stateMachine))
      .thenReturn(AgentState.THINKING)
      .thenReturn(AgentState.ACTING)
      .thenReturn(AgentState.THINKING)
      .thenReturn(AgentState.FINISHED);

    when(stateMachineDriver.isInFinalState(stateMachine))
      .thenReturn(false, false, false, true);

    // RAG 工具返回错误
    ToolExecutionService.ToolExecutionResult toolResult = ToolExecutionService.ToolExecutionResult.builder()
      .toolName("retrieve_knowledge")
      .arguments("{\"query\":\"test\"}")
      .result("Error retrieving knowledge: connection timeout")
      .success(false)
      .error("connection timeout")
      .durationMs(100)
      .timestamp(Instant.now())
      .build();

    when(toolExecutionService.execute("retrieve_knowledge", "{\"query\":\"test\"}"))
      .thenReturn(toolResult);

    StepRecord.ToolResultRecord resultRecord = StepRecord.ToolResultRecord.builder()
      .toolCallId("step-1")
      .toolName("retrieve_knowledge")
      .result("Error retrieving knowledge: connection timeout")
      .success(false)
      .error("connection timeout")
      .build();

    when(toolExecutionService.toResultRecord(anyString(), eq(toolResult)))
      .thenReturn(resultRecord);

    // Act
    AgentResult result = agentKernel.execute("test question");

    // Assert：最终答案应带降级前缀
    assertThat(result.getAnswer()).startsWith("未查到相关数据，以下为直接回答：");
    assertThat(result.getAnswer()).contains("Direct answer without RAG");
    assertThat(result.isSuccess()).isTrue();
  }
}
