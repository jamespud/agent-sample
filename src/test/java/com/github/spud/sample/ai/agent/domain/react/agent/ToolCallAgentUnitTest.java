package com.github.spud.sample.ai.agent.domain.react.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.spud.sample.ai.agent.domain.state.AgentState;
import com.github.spud.sample.ai.agent.domain.state.ToolChoice;
import com.github.spud.sample.ai.agent.domain.tools.ToolRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

/**
 * Unit tests for ToolCallAgent to isolate agent logic from external dependencies
 */
class ToolCallAgentUnitTest {

  private ChatClient chatClient;
  private ToolRegistry toolRegistry;
  private ChatClientRequestSpec requestSpec;
  private CallResponseSpec responseSpec;

  @BeforeEach
  void setUp() {
    chatClient = mock(ChatClient.class);
    toolRegistry = mock(ToolRegistry.class);
    requestSpec = mock(ChatClientRequestSpec.class);
    responseSpec = mock(CallResponseSpec.class);

    // Default mock chain - use Prompt instead of String
    when(chatClient.prompt((Prompt) any())).thenReturn(requestSpec);
    when(requestSpec.toolCallbacks((List) any())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(responseSpec);
  }

  @Test
  @DisplayName("Agent should initialize with IDLE state")
  void shouldInitializeWithIdleState() {
    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(3)
      .build();

    assertThat(agent.getState()).isEqualTo(AgentState.IDLE);
    assertThat(agent.getCurrentStep()).isEqualTo(0);
  }

  @Test
  @DisplayName("Agent should call terminate tool and finish successfully")
  void shouldCallTerminateAndFinish() {
    // Mock terminate tool callback
    org.springframework.ai.tool.definition.ToolDefinition toolDef =
      new org.springframework.ai.tool.definition.DefaultToolDefinition("terminate",
        "Terminate the agent execution", "{}");

    ToolCallback terminateCallback = mock(ToolCallback.class);
    when(terminateCallback.getToolDefinition()).thenReturn(toolDef);
    when(terminateCallback.call(any())).thenReturn("Task completed");

    // Mock toolRegistry to return terminate callback
    when(toolRegistry.getCallback("terminate"))
      .thenReturn(java.util.Optional.of(terminateCallback));

    // Mock LLM response with terminate tool call
    // ToolCall constructor: id, type, name, arguments
    AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), List.of(
      new AssistantMessage.ToolCall("call-1", "function", "terminate",
        "{\"answer\": \"Task completed\"}")
    ));

    Generation generation = new Generation(assistantMessage);
    ChatResponse chatResponse = new ChatResponse(List.of(generation));

    when(responseSpec.chatResponse()).thenReturn(chatResponse);
    when(toolRegistry.getAllCallbacks()).thenReturn(List.of());

    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(3)
      .toolChoice(ToolChoice.AUTO)
      .build();

    // Run agent with timeout
    String result = agent.run("Complete the task").block(Duration.ofSeconds(5));

    // Verify
    assertThat(result).contains("Task completed");
    assertThat(agent.getState()).isEqualTo(AgentState.IDLE); // Reset after run
    assertThat(agent.getFinalAnswer()).isNull(); // Reset after run
  }

  @Test
  @DisplayName("Agent should stop at maxSteps if no terminate")
  void shouldStopAtMaxSteps() {
    // Mock LLM response without terminate (just text)
    AssistantMessage assistantMessage = new AssistantMessage("Thinking...", Map.of(), List.of());
    Generation generation = new Generation(assistantMessage);
    ChatResponse chatResponse = new ChatResponse(List.of(generation));

    when(responseSpec.chatResponse()).thenReturn(chatResponse);
    when(toolRegistry.getAllCallbacks()).thenReturn(List.of());

    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(2)
      .toolChoice(ToolChoice.AUTO)
      .build();

    // Run agent with timeout
    String result = agent.run("Test request").block(Duration.ofSeconds(5));

    // Should reach maxSteps and return something
    assertThat(result).isNotNull();
    assertThat(result).contains("Terminated: Reached max steps (2)");
  }

  @Test
  @DisplayName("Agent think() should return false when toolChoice is NONE and no content")
  void thinkShouldReturnFalseForNoneWithNoContent() {
    // Mock empty response
    AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), List.of());
    Generation generation = new Generation(assistantMessage);
    ChatResponse chatResponse = new ChatResponse(List.of(generation));

    when(responseSpec.chatResponse()).thenReturn(chatResponse);

    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(1)
      .toolChoice(ToolChoice.NONE)
      .build();

    // Call think directly with timeout
    Boolean shouldAct = agent.think().block(Duration.ofSeconds(3));

    assertThat(shouldAct).isFalse();
  }

  @Test
  @DisplayName("Agent think() should return true when toolChoice is NONE and has content")
  void thinkShouldReturnTrueForNoneWithContent() {
    // Mock response with content
    AssistantMessage assistantMessage = new AssistantMessage("Some response text", Map.of(),
      List.of());
    Generation generation = new Generation(assistantMessage);
    ChatResponse chatResponse = new ChatResponse(List.of(generation));

    when(responseSpec.chatResponse()).thenReturn(chatResponse);

    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(1)
      .toolChoice(ToolChoice.NONE)
      .build();

    // Call think directly with timeout
    Boolean shouldAct = agent.think().block(Duration.ofSeconds(3));

    assertThat(shouldAct).isTrue();
  }

  @Test
  @DisplayName("ToolChoice.REQUIRED without tool_calls should inject correction and return false")
  void requiredWithoutToolCalls_shouldInjectCorrectionAndRetryThink() {
    // Mock LLM response without tool_calls
    AssistantMessage assistantMessage = new AssistantMessage("I'm thinking...", Map.of(),
      List.of());
    Generation generation = new Generation(assistantMessage);
    ChatResponse chatResponse = new ChatResponse(List.of(generation));

    when(responseSpec.chatResponse()).thenReturn(chatResponse);
    when(toolRegistry.getAllCallbacks()).thenReturn(List.of());

    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(3)
      .toolChoice(ToolChoice.REQUIRED)
      .build();

    // Call think directly
    Boolean shouldAct = agent.think().block(Duration.ofSeconds(3));

    // Should return false and inject correction prompt
    assertThat(shouldAct).isFalse();
    assertThat(agent.getMessages()).hasSizeGreaterThan(0);
    // Check last message is SystemMessage with correction
    assertThat(agent.getMessages().get(agent.getMessages().size() - 1))
      .isInstanceOf(org.springframework.ai.chat.messages.SystemMessage.class);
  }

  @Test
  @DisplayName("ToolChoice.REQUIRED then terminate on second call should finish with finalAnswer")
  void requiredThenTerminateOnSecondCall_shouldFinishWithFinalAnswer() {
    // First call: no tool_calls
    AssistantMessage assistantMsg1 = new AssistantMessage("Thinking...", Map.of(), List.of());
    Generation gen1 = new Generation(assistantMsg1);
    ChatResponse chatResp1 = new ChatResponse(List.of(gen1));

    // Mock terminate tool
    org.springframework.ai.tool.definition.ToolDefinition terminateToolDef =
      new org.springframework.ai.tool.definition.DefaultToolDefinition("terminate",
        "Terminate execution", "{}");
    ToolCallback terminateCallback = mock(ToolCallback.class);
    when(terminateCallback.getToolDefinition()).thenReturn(terminateToolDef);
    when(terminateCallback.call(any())).thenReturn("Final answer");
    when(toolRegistry.getCallback("terminate"))
      .thenReturn(java.util.Optional.of(terminateCallback));

    // Second call: with terminate tool_call
    AssistantMessage assistantMsg2 = new AssistantMessage("", Map.of(), List.of(
      new AssistantMessage.ToolCall("call-1", "function", "terminate",
        "{\"answer\": \"Task completed successfully\"}")
    ));
    Generation gen2 = new Generation(assistantMsg2);
    ChatResponse chatResp2 = new ChatResponse(List.of(gen2));

    // Return both responses in sequence
    when(responseSpec.chatResponse()).thenReturn(chatResp1, chatResp2);
    when(toolRegistry.getAllCallbacks()).thenReturn(List.of());

    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(3)
      .toolChoice(ToolChoice.REQUIRED)
      .build();

    // Run agent
    String result = agent.run("Test request").block(Duration.ofSeconds(5));

    // Verify result contains final answer
    assertThat(result).contains("Task completed successfully");
  }

  @Test
  @DisplayName("Multiple tool_calls including terminate should only execute terminate")
  void multipleToolCallsIncludingTerminate_shouldOnlyExecuteTerminate() {
    // Mock two tool callbacks
    org.springframework.ai.tool.definition.ToolDefinition terminateToolDef =
      new org.springframework.ai.tool.definition.DefaultToolDefinition("terminate",
        "Terminate execution", "{}");
    ToolCallback terminateCallback = mock(ToolCallback.class);
    when(terminateCallback.getToolDefinition()).thenReturn(terminateToolDef);
    when(terminateCallback.call(any())).thenReturn("Done");

    org.springframework.ai.tool.definition.ToolDefinition otherToolDef =
      new org.springframework.ai.tool.definition.DefaultToolDefinition("other_tool",
        "Other tool", "{}");
    ToolCallback otherCallback = mock(ToolCallback.class);
    when(otherCallback.getToolDefinition()).thenReturn(otherToolDef);
    when(otherCallback.call(any())).thenReturn("Other result");

    when(toolRegistry.getCallback("terminate"))
      .thenReturn(java.util.Optional.of(terminateCallback));
    when(toolRegistry.getCallback("other_tool"))
      .thenReturn(java.util.Optional.of(otherCallback));

    // LLM returns both tool calls
    AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), List.of(
      new AssistantMessage.ToolCall("call-1", "function", "other_tool", "{}"),
      new AssistantMessage.ToolCall("call-2", "function", "terminate",
        "{\"answer\": \"Final answer\"}")
    ));
    Generation generation = new Generation(assistantMessage);
    ChatResponse chatResponse = new ChatResponse(List.of(generation));

    when(responseSpec.chatResponse()).thenReturn(chatResponse);
    when(toolRegistry.getAllCallbacks()).thenReturn(List.of());

    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(2)
      .toolChoice(ToolChoice.AUTO)
      .build();

    String result = agent.run("Test").block(Duration.ofSeconds(5));

    // Verify terminate was called and other_tool was NOT called
    assertThat(result).contains("Final answer");
    // Verify only terminate callback was invoked (not other_tool)
    // Note: We can't directly verify mock invocations due to filterForTerminate, 
    // but we can check that only one ToolResponseMessage was added
    long toolResponseCount = agent.getMessages().stream()
      .filter(m -> m instanceof org.springframework.ai.chat.messages.ToolResponseMessage)
      .count();
    assertThat(toolResponseCount).isEqualTo(1);
  }

  @Test
  @DisplayName("Unknown tool call should append error tool response and continue")
  void unknownToolCall_shouldAppendErrorToolResponseAndContinue() {
    // LLM returns unknown tool
    AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), List.of(
      new AssistantMessage.ToolCall("call-1", "function", "unknown_tool", "{}")
    ));
    Generation generation = new Generation(assistantMessage);
    ChatResponse chatResponse = new ChatResponse(List.of(generation));

    when(responseSpec.chatResponse()).thenReturn(chatResponse);
    when(toolRegistry.getCallback("unknown_tool"))
      .thenReturn(java.util.Optional.empty());
    when(toolRegistry.getAllCallbacks()).thenReturn(List.of());

    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(2)
      .toolChoice(ToolChoice.AUTO)
      .build();

    String result = agent.run("Test").block(Duration.ofSeconds(5));

    // Should not throw exception and should contain error in result
    assertThat(result).isNotNull();
    assertThat(result).containsIgnoringCase("error");

    // Verify error ToolResponseMessage was added
    boolean hasErrorToolResponse = agent.getMessages().stream()
      .filter(m -> m instanceof org.springframework.ai.chat.messages.ToolResponseMessage)
      .anyMatch(m -> {
        org.springframework.ai.chat.messages.ToolResponseMessage trm =
          (org.springframework.ai.chat.messages.ToolResponseMessage) m;
        return trm.getResponses().stream()
          .anyMatch(
            r -> r.responseData().contains("Error") || r.responseData().contains("Unknown"));
      });
    assertThat(hasErrorToolResponse).isTrue();
  }

  @Test
  @DisplayName("Terminate with invalid JSON arguments should fallback and finish")
  void terminateWithInvalidJsonArguments_shouldFallbackAndFinish() {
    // Mock terminate callback
    org.springframework.ai.tool.definition.ToolDefinition terminateToolDef =
      new org.springframework.ai.tool.definition.DefaultToolDefinition("terminate",
        "Terminate execution", "{}");
    ToolCallback terminateCallback = mock(ToolCallback.class);
    when(terminateCallback.getToolDefinition()).thenReturn(terminateToolDef);
    when(terminateCallback.call(any())).thenReturn("Done");

    when(toolRegistry.getCallback("terminate"))
      .thenReturn(java.util.Optional.of(terminateCallback));

    // LLM returns terminate with invalid JSON
    AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), List.of(
      new AssistantMessage.ToolCall("call-1", "function", "terminate",
        "{invalid json")
    ));
    Generation generation = new Generation(assistantMessage);
    ChatResponse chatResponse = new ChatResponse(List.of(generation));

    when(responseSpec.chatResponse()).thenReturn(chatResponse);
    when(toolRegistry.getAllCallbacks()).thenReturn(List.of());

    ToolCallAgent agent = ToolCallAgent.builder()
      .name("test-agent")
      .chatClient(chatClient)
      .toolRegistry(toolRegistry)
      .messages(new ArrayList<>())
      .maxSteps(2)
      .toolChoice(ToolChoice.AUTO)
      .build();

    String result = agent.run("Test").block(Duration.ofSeconds(5));

    // Should finish with fallback message (not throw exception)
    assertThat(result).isNotNull();
    assertThat(result).containsAnyOf("Task completed", "error parsing");
  }
}


