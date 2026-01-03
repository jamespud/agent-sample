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
import java.util.HashMap;
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
    AssistantMessage assistantMessage = new AssistantMessage("Some response text", Map.of(), List.of());
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
}

