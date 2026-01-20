package com.github.spud.sample.ai.agent.it.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spud.sample.ai.agent.domain.session.ReActAgentType;
import com.github.spud.sample.ai.agent.domain.session.ReActSessionService;
import com.github.spud.sample.ai.agent.it.support.ContainersSupport;
import com.github.spud.sample.ai.agent.it.support.OpenAiMockSupport;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ExecutionException;

/**
 * Service-level integration tests for ReAct Agent (S1-S8 scenarios)
 * Tests Service API layer bypassing HTTP to focus on Agent business logic
 *
 * Note: Due to Spring AI ChatClient's automatic tool execution feature,
 * we use plain text mock responses. Tool_calls behavior is validated
 * in ToolCallAgentUnitTest with proper mocking.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
class ReActSessionServiceIT extends ContainersSupport {

  @Autowired
  private ReActSessionService sessionService;

  @BeforeEach
  void setUp() {
    OpenAiMockSupport.clearQueue();
  }

  @Test
  @DisplayName("S1: Standard completion with answer")
  void s1_shouldCompleteSuccessfully() throws ExecutionException, InterruptedException {
    MockResponse response = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Task completed successfully!")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(response);

    // Create agent first
    ReActSessionService.CreateAgentRequest createAgentRequest = ReActSessionService.CreateAgentRequest.builder()
        .name("Test Agent")
        .description("Test Description")
        .agentType(ReActAgentType.TOOLCALL)
        .maxSteps(2)
        .build();
    String agentId = sessionService.createAgent(createAgentRequest);

    ReActSessionService.CreateSessionRequest request = new ReActSessionService.CreateSessionRequest();
    request.setAgentId(agentId);

    String conversationId = sessionService.createSession(request);
    var result = sessionService.sendMessage(conversationId, "Simple task").get();

    assertThat(result).isNotNull();
    assertThat(result.getAnswer()).contains("completed successfully");
    assertThat(result.isFinished()).isTrue();
  }

  @Test
  @DisplayName("S2: Multi-step reasoning")
  void s2_shouldHandleMultipleSteps() throws ExecutionException, InterruptedException {
    MockResponse step1 = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Analyzing the question...")
      .withFinishReason("stop")
      .build();

    MockResponse step2 = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Based on my analysis, the answer is 42.")
      .withFinishReason("stop")
      .build();

    OpenAiMockSupport.enqueueResponse(step1);
    OpenAiMockSupport.enqueueResponse(step2);

    // Create agent first
    ReActSessionService.CreateAgentRequest createAgentRequest = ReActSessionService.CreateAgentRequest.builder()
        .name("Multi-step Agent")
        .description("Multi-step reasoning agent")
        .agentType(ReActAgentType.TOOLCALL)
        .maxSteps(3)
        .build();
    String agentId = sessionService.createAgent(createAgentRequest);

    ReActSessionService.CreateSessionRequest request = new ReActSessionService.CreateSessionRequest();
    request.setAgentId(agentId);

    String conversationId = sessionService.createSession(request);
    var result = sessionService.sendMessage(conversationId, "Complex question").get();

    assertThat(result).isNotNull();
    assertThat(result.isFinished()).isTrue();
  }

  @Test
  @DisplayName("S3: Reaches maxSteps limit")
  void s3_shouldStopAtMaxSteps() throws ExecutionException, InterruptedException {
    for (int i = 1; i <= 5; i++) {
      MockResponse step = new OpenAiMockSupport.ResponseBuilder()
        .withContent("Step " + i + " processing...")
        .withFinishReason("stop")
        .build();
      OpenAiMockSupport.enqueueResponse(step);
    }

    // Create agent first
    ReActSessionService.CreateAgentRequest createAgentRequest = ReActSessionService.CreateAgentRequest.builder()
        .name("Max Steps Agent")
        .description("Agent with max steps limit")
        .agentType(ReActAgentType.TOOLCALL)
        .maxSteps(3)
        .build();
    String agentId = sessionService.createAgent(createAgentRequest);

    ReActSessionService.CreateSessionRequest request = new ReActSessionService.CreateSessionRequest();
    request.setAgentId(agentId);

    String conversationId = sessionService.createSession(request);
    var result = sessionService.sendMessage(conversationId, "Long task").get();

    assertThat(result).isNotNull();
    assertThat(result.isFinished()).isTrue();
  }

  @Test
  @DisplayName("S4: Empty response handling")
  void s4_shouldHandleEmptyResponse() throws ExecutionException, InterruptedException {
    MockResponse response = new OpenAiMockSupport.ResponseBuilder()
      .withContent("")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(response);

    // Create agent first
    ReActSessionService.CreateAgentRequest createAgentRequest = ReActSessionService.CreateAgentRequest.builder()
        .name("Empty Response Agent")
        .description("Agent for empty response handling")
        .agentType(ReActAgentType.TOOLCALL)
        .maxSteps(2)
        .build();
    String agentId = sessionService.createAgent(createAgentRequest);

    ReActSessionService.CreateSessionRequest request = new ReActSessionService.CreateSessionRequest();
    request.setAgentId(agentId);

    String conversationId = sessionService.createSession(request);
    var result = sessionService.sendMessage(conversationId, "Empty test").get();

    assertThat(result).isNotNull();
    assertThat(result.isFinished()).isTrue();
  }

  @Test
  @DisplayName("S5: Long response content")
  void s5_shouldHandleLongContent() throws ExecutionException, InterruptedException {
    String longContent = "Answer: " + "A".repeat(500) + ". End.";
    MockResponse response = new OpenAiMockSupport.ResponseBuilder()
      .withContent(longContent)
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(response);

    // Create agent first
    ReActSessionService.CreateAgentRequest createAgentRequest = ReActSessionService.CreateAgentRequest.builder()
        .name("Long Response Agent")
        .description("Agent for long response handling")
        .agentType(ReActAgentType.TOOLCALL)
        .maxSteps(2)
        .build();
    String agentId = sessionService.createAgent(createAgentRequest);

    ReActSessionService.CreateSessionRequest request = new ReActSessionService.CreateSessionRequest();
    request.setAgentId(agentId);

    String conversationId = sessionService.createSession(request);
    var result = sessionService.sendMessage(conversationId, "Give details").get();

    assertThat(result).isNotNull();
    assertThat(result.getAnswer()).contains("Answer:");
    assertThat(result.getAnswer().length()).isGreaterThan(400);
    assertThat(result.isFinished()).isTrue();
  }

  @Test
  @DisplayName("S6: Session continuity across messages")
  void s6_shouldMaintainContext() throws ExecutionException, InterruptedException {
    MockResponse resp1 = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Hello! Ready to help.")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(resp1);

    // Create agent first
    ReActSessionService.CreateAgentRequest createAgentRequest = ReActSessionService.CreateAgentRequest.builder()
        .name("Context Agent")
        .description("Agent with context continuity")
        .agentType(ReActAgentType.TOOLCALL)
        .maxSteps(2)
        .build();
    String agentId = sessionService.createAgent(createAgentRequest);

    ReActSessionService.CreateSessionRequest request = new ReActSessionService.CreateSessionRequest();
    request.setAgentId(agentId);

    String conversationId = sessionService.createSession(request);
    var result1 = sessionService.sendMessage(conversationId, "Hi").get();

    assertThat(result1.getAnswer()).contains("Hello");

    MockResponse resp2 = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Yes, I remember our chat.")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(resp2);

    var result2 = sessionService.sendMessage(conversationId, "Remember?").get();

    assertThat(result2.getAnswer()).contains("remember");
  }

  @Test
  @DisplayName("S7: TOOLCALL agent type")
  void s7_shouldCreateToolCallAgent() throws ExecutionException, InterruptedException {
    MockResponse response = new OpenAiMockSupport.ResponseBuilder()
      .withContent("TOOLCALL agent ready.")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(response);

    // Create agent first
    ReActSessionService.CreateAgentRequest createAgentRequest = ReActSessionService.CreateAgentRequest.builder()
        .name("ToolCall Agent")
        .description("ToolCall type agent")
        .agentType(ReActAgentType.TOOLCALL)
        .maxSteps(2)
        .build();
    String agentId = sessionService.createAgent(createAgentRequest);

    ReActSessionService.CreateSessionRequest request = new ReActSessionService.CreateSessionRequest();
    request.setAgentId(agentId);

    String conversationId = sessionService.createSession(request);
    var result = sessionService.sendMessage(conversationId, "Test").get();

    assertThat(result).isNotNull();
    assertThat(result.getAnswer()).isNotEmpty();
  }

  @Test
  @DisplayName("S8: Multiple independent sessions")
  void s8_shouldIsolateSessions() throws ExecutionException, InterruptedException {
    // First session
    MockResponse respA = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Response for Session A")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(respA);

    // Create agent first
    ReActSessionService.CreateAgentRequest createAgentRequest1 = ReActSessionService.CreateAgentRequest.builder()
        .name("Session A Agent")
        .description("Agent for session A")
        .agentType(ReActAgentType.TOOLCALL)
        .maxSteps(2)
        .build();
    String agentId1 = sessionService.createAgent(createAgentRequest1);

    ReActSessionService.CreateSessionRequest req1 = new ReActSessionService.CreateSessionRequest();
    req1.setAgentId(agentId1);

    String convId1 = sessionService.createSession(req1);
    var result1 = sessionService.sendMessage(convId1, "Msg to A").get();

    assertThat(result1.getAnswer()).contains("Session A");

    // Second session (created and executed after first completes)
    MockResponse respB = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Response for Session B")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(respB);

    ReActSessionService.CreateAgentRequest createAgentRequest2 = ReActSessionService.CreateAgentRequest.builder()
        .name("Session B Agent")
        .description("Agent for session B")
        .agentType(ReActAgentType.TOOLCALL)
        .maxSteps(2)
        .build();
    String agentId2 = sessionService.createAgent(createAgentRequest2);

    ReActSessionService.CreateSessionRequest req2 = new ReActSessionService.CreateSessionRequest();
    req2.setAgentId(agentId2);

    String convId2 = sessionService.createSession(req2);
    var result2 = sessionService.sendMessage(convId2, "Msg to B").get();

    // Verify sessions are isolated
    assertThat(convId1).isNotEqualTo(convId2);
    assertThat(result2.getAnswer()).contains("Session B");
  }
}
