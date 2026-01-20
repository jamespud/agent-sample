package com.github.spud.sample.ai.agent.it.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spud.sample.ai.agent.domain.session.ReActAgentType;
import com.github.spud.sample.ai.agent.domain.session.ReActSessionService.SendMessageResponse;
import com.github.spud.sample.ai.agent.interfaces.rest.ReActAgentController;
import com.github.spud.sample.ai.agent.it.support.ContainersSupport;
import com.github.spud.sample.ai.agent.it.support.OpenAiMockSupport;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * HTTP REST API integration tests for ReAct Agent (S1-S8 scenarios)
 * Tests complete flow: HTTP Request → Controller → Service → Agent → DB
 *
 * Uses WebTestClient for reactive endpoint testing with MockWebServer
 * for deterministic LLM responses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ReActRestControllerIT extends ContainersSupport {

  @Autowired
  private WebTestClient webClient;

  @BeforeEach
  void setUp() {
    OpenAiMockSupport.clearQueue();
  }

  @Test
  @DisplayName("S1-HTTP: Create session and send message")
  void s1_shouldCreateSessionAndSendMessage() {
    // Mock LLM response
    MockResponse response = new OpenAiMockSupport.ResponseBuilder()
      .withContent("HTTP test completed successfully!")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(response);

    // Step 1: Create agent
    ReActAgentController.CreateAgentRequestDto createAgentRequest = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequest.setName("Test Agent");
    createAgentRequest.setDescription("Test Description");
    createAgentRequest.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequest.setMaxSteps(2);

    ReActAgentController.CreateAgentResponse agentResp = webClient.post()
      .uri("/agent/react/agent/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createAgentRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateAgentResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(agentResp).isNotNull();
    assertThat(agentResp.getAgentId()).isNotEmpty();

    // Step 2: Create session
    ReActAgentController.CreateSessionRequestDto createSessionRequest = new ReActAgentController.CreateSessionRequestDto();
    createSessionRequest.setAgentId(agentResp.getAgentId());

    ReActAgentController.CreateSessionResponse sessionResp = webClient.post()
      .uri("/agent/react/session/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createSessionRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(sessionResp).isNotNull();
    assertThat(sessionResp.getConversationId()).isNotEmpty();

    // Step 3: Send message
    ReActAgentController.SendMessageRequestDto messageRequest = new ReActAgentController.SendMessageRequestDto();
    messageRequest.setContent("Test HTTP API");

    SendMessageResponse messageResp = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionResp.getConversationId())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(messageRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(messageResp).isNotNull();
    assertThat(messageResp.getAnswer()).contains("HTTP test completed");
    assertThat(messageResp.isFinished()).isTrue();
  }

  @Test
  @DisplayName("S2-HTTP: Multi-step conversation via REST")
  void s2_shouldHandleMultiStepConversation() {
    // Mock responses for multiple steps
    MockResponse step1 = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Processing your request...")
      .withFinishReason("stop")
      .build();
    MockResponse step2 = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Here's the final answer: 42")
      .withFinishReason("stop")
      .build();

    OpenAiMockSupport.enqueueResponse(step1);
    OpenAiMockSupport.enqueueResponse(step2);

    // Step 1: Create agent
    ReActAgentController.CreateAgentRequestDto createAgentRequest = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequest.setName("Multi-step Agent");
    createAgentRequest.setDescription("Multi-step reasoning agent");
    createAgentRequest.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequest.setMaxSteps(3);

    ReActAgentController.CreateAgentResponse agentResp = webClient.post()
      .uri("/agent/react/agent/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createAgentRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateAgentResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(agentResp).isNotNull();
    assertThat(agentResp.getAgentId()).isNotEmpty();

    // Step 2: Create session
    ReActAgentController.CreateSessionRequestDto createSessionRequest = new ReActAgentController.CreateSessionRequestDto();
    createSessionRequest.setAgentId(agentResp.getAgentId());

    ReActAgentController.CreateSessionResponse sessionResp = webClient.post()
      .uri("/agent/react/session/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createSessionRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    // Send message
    ReActAgentController.SendMessageRequestDto messageRequest = new ReActAgentController.SendMessageRequestDto();
    messageRequest.setContent("Complex question");

    SendMessageResponse response = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionResp.getConversationId())
      .bodyValue(messageRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(response.isFinished()).isTrue();
  }

  @Test
  @DisplayName("S3-HTTP: Session not found returns 404")
  void s3_shouldReturn404ForNonExistentSession() {
    ReActAgentController.SendMessageRequestDto messageRequest = new ReActAgentController.SendMessageRequestDto();
    messageRequest.setContent("Test message");

    webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", "non-existent-id")
      .bodyValue(messageRequest)
      .exchange()
      .expectStatus().isNotFound();
  }

  @Test
  @DisplayName("S4-HTTP: Empty message handling")
  void s4_shouldHandleEmptyMessage() {
    MockResponse response = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Empty input received")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(response);

    // Step 1: Create agent
    ReActAgentController.CreateAgentRequestDto createAgentRequest = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequest.setName("Empty Message Agent");
    createAgentRequest.setDescription("Agent for handling empty messages");
    createAgentRequest.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequest.setMaxSteps(2);

    ReActAgentController.CreateAgentResponse agentResp = webClient.post()
      .uri("/agent/react/agent/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createAgentRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateAgentResponse.class)
      .returnResult()
      .getResponseBody();

    // Step 2: Create session
    ReActAgentController.CreateSessionRequestDto createSessionRequest = new ReActAgentController.CreateSessionRequestDto();
    createSessionRequest.setAgentId(agentResp.getAgentId());

    ReActAgentController.CreateSessionResponse sessionResp = webClient.post()
      .uri("/agent/react/session/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createSessionRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    // Send empty message
    ReActAgentController.SendMessageRequestDto messageRequest = new ReActAgentController.SendMessageRequestDto();
    messageRequest.setContent("");

    SendMessageResponse messageResp = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionResp.getConversationId())
      .bodyValue(messageRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(messageResp).isNotNull();
    assertThat(messageResp.isFinished()).isTrue();
  }

  @Test
  @DisplayName("S5-HTTP: Long content response")
  void s5_shouldHandleLongResponse() {
    String longContent = "Answer: " + "X".repeat(1000) + " End.";
    MockResponse response = new OpenAiMockSupport.ResponseBuilder()
      .withContent(longContent)
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(response);

    // Step 1: Create agent
    ReActAgentController.CreateAgentRequestDto createAgentRequest = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequest.setName("Long Content Agent");
    createAgentRequest.setDescription("Agent for handling long content");
    createAgentRequest.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequest.setMaxSteps(2);

    ReActAgentController.CreateAgentResponse agentResp = webClient.post()
      .uri("/agent/react/agent/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createAgentRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateAgentResponse.class)
      .returnResult()
      .getResponseBody();

    // Step 2: Create session
    ReActAgentController.CreateSessionRequestDto createSessionRequest = new ReActAgentController.CreateSessionRequestDto();
    createSessionRequest.setAgentId(agentResp.getAgentId());

    ReActAgentController.CreateSessionResponse sessionResp = webClient.post()
      .uri("/agent/react/session/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createSessionRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    ReActAgentController.SendMessageRequestDto messageRequest = new ReActAgentController.SendMessageRequestDto();
    messageRequest.setContent("Give me details");

    SendMessageResponse messageResp = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionResp.getConversationId())
      .bodyValue(messageRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(messageResp.getAnswer()).contains("Answer:");
    assertThat(messageResp.getAnswer().length()).isGreaterThan(900);
  }

  @Test
  @DisplayName("S6-HTTP: Multiple messages in same session")
  void s6_shouldMaintainSessionContext() {
    // First message
    MockResponse resp1 = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Hello! I'm Agent A.")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(resp1);

    // Step 1: Create agent
    ReActAgentController.CreateAgentRequestDto createAgentRequest = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequest.setName("Context Agent");
    createAgentRequest.setDescription("Agent for maintaining context");
    createAgentRequest.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequest.setMaxSteps(2);

    ReActAgentController.CreateAgentResponse agentResp = webClient.post()
      .uri("/agent/react/agent/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createAgentRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateAgentResponse.class)
      .returnResult()
      .getResponseBody();

    // Step 2: Create session
    ReActAgentController.CreateSessionRequestDto createSessionRequest = new ReActAgentController.CreateSessionRequestDto();
    createSessionRequest.setAgentId(agentResp.getAgentId());

    ReActAgentController.CreateSessionResponse sessionResp = webClient.post()
      .uri("/agent/react/session/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createSessionRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    ReActAgentController.SendMessageRequestDto msg1 = new ReActAgentController.SendMessageRequestDto();
    msg1.setContent("Hi");

    SendMessageResponse resp1Result = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionResp.getConversationId())
      .bodyValue(msg1)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(resp1Result.getAnswer()).contains("Agent A");

    // Second message in same session
    MockResponse resp2 = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Yes, we talked before.")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(resp2);

    ReActAgentController.SendMessageRequestDto msg2 = new ReActAgentController.SendMessageRequestDto();
    msg2.setContent("Remember me?");

    SendMessageResponse resp2Result = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionResp.getConversationId())
      .bodyValue(msg2)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(resp2Result.getAnswer()).contains("talked before");
  }

  @Test
  @DisplayName("S7-HTTP: Different agent types")
  void s7_shouldCreateDifferentAgentTypes() {
    MockResponse response = new OpenAiMockSupport.ResponseBuilder()
      .withContent("TOOLCALL agent initialized.")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(response);

    // Step 1: Create agent
    ReActAgentController.CreateAgentRequestDto createAgentRequest = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequest.setName("Tool Call Agent");
    createAgentRequest.setDescription("Tool Call type agent");
    createAgentRequest.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequest.setMaxSteps(2);

    ReActAgentController.CreateAgentResponse agentResp = webClient.post()
      .uri("/agent/react/agent/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createAgentRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateAgentResponse.class)
      .returnResult()
      .getResponseBody();

    // Step 2: Create session
    ReActAgentController.CreateSessionRequestDto createSessionRequest = new ReActAgentController.CreateSessionRequestDto();
    createSessionRequest.setAgentId(agentResp.getAgentId());

    ReActAgentController.CreateSessionResponse sessionResp = webClient.post()
      .uri("/agent/react/session/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createSessionRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    ReActAgentController.SendMessageRequestDto messageRequest = new ReActAgentController.SendMessageRequestDto();
    messageRequest.setContent("Test agent type");

    SendMessageResponse response2 = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionResp.getConversationId())
      .bodyValue(messageRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(response2).isNotNull();
    assertThat(response2.getAnswer()).contains("TOOLCALL");
  }

  @Test
  @DisplayName("S8-HTTP: Concurrent independent sessions via REST")
  void s8_shouldIsolateMultipleSessions() {
    // Session A
    MockResponse respA = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Response for Session A")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(respA);

    // Step 1: Create agent for Session A
    ReActAgentController.CreateAgentRequestDto createAgentRequestA = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequestA.setName("Session A Agent");
    createAgentRequestA.setDescription("Agent for Session A");
    createAgentRequestA.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequestA.setMaxSteps(2);

    ReActAgentController.CreateAgentResponse agentRespA = webClient.post()
      .uri("/agent/react/agent/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createAgentRequestA)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateAgentResponse.class)
      .returnResult()
      .getResponseBody();

    // Step 2: Create session for Session A
    ReActAgentController.CreateSessionRequestDto reqA = new ReActAgentController.CreateSessionRequestDto();
    reqA.setAgentId(agentRespA.getAgentId());

    ReActAgentController.CreateSessionResponse sessionRespA = webClient.post()
      .uri("/agent/react/session/new")
      .bodyValue(reqA)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    ReActAgentController.SendMessageRequestDto msgA = new ReActAgentController.SendMessageRequestDto();
    msgA.setContent("Message to A");

    SendMessageResponse resultA = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionRespA.getConversationId())
      .bodyValue(msgA)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(resultA.getAnswer()).contains("Session A");

    // Session B (after A completes)
    MockResponse respB = new OpenAiMockSupport.ResponseBuilder()
      .withContent("Response for Session B")
      .withFinishReason("stop")
      .build();
    OpenAiMockSupport.enqueueResponse(respB);

    // Step 1: Create agent for Session B
    ReActAgentController.CreateAgentRequestDto createAgentRequestB = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequestB.setName("Session B Agent");
    createAgentRequestB.setDescription("Agent for Session B");
    createAgentRequestB.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequestB.setMaxSteps(2);

    ReActAgentController.CreateAgentResponse agentRespB = webClient.post()
      .uri("/agent/react/agent/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createAgentRequestB)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateAgentResponse.class)
      .returnResult()
      .getResponseBody();

    // Step 2: Create session for Session B
    ReActAgentController.CreateSessionRequestDto reqB = new ReActAgentController.CreateSessionRequestDto();
    reqB.setAgentId(agentRespB.getAgentId());

    ReActAgentController.CreateSessionResponse sessionRespB = webClient.post()
      .uri("/agent/react/session/new")
      .bodyValue(reqB)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    ReActAgentController.SendMessageRequestDto msgB = new ReActAgentController.SendMessageRequestDto();
    msgB.setContent("Message to B");

    SendMessageResponse resultB = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionRespB.getConversationId())
      .bodyValue(msgB)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    // Verify isolation
    assertThat(sessionRespA.getConversationId()).isNotEqualTo(sessionRespB.getConversationId());
    assertThat(resultB.getAnswer()).contains("Session B");
  }

  @Test
  @DisplayName("S9-HTTP: Invalid request payload handling")
  void s9_shouldHandleInvalidPayload() {
    // Try to create agent with invalid parameters
    ReActAgentController.CreateAgentRequestDto createAgentRequest = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequest.setName(""); // Empty name should be invalid
    createAgentRequest.setDescription("Test Description");
    createAgentRequest.setAgentType(ReActAgentType.TOOLCALL);

    webClient.post()
      .uri("/agent/react/agent/new")
      .bodyValue(createAgentRequest)
      .exchange()
      .expectStatus().is4xxClientError(); // Should return 400 or similar
  }

  @Test
  @DisplayName("S10-HTTP: Custom maxSteps configuration")
  void s10_shouldRespectCustomMaxSteps() {
    // Enqueue enough responses to exceed normal limit
    for (int i = 0; i < 10; i++) {
      MockResponse step = new OpenAiMockSupport.ResponseBuilder()
        .withContent("Step " + (i + 1))
        .withFinishReason("stop")
        .build();
      OpenAiMockSupport.enqueueResponse(step);
    }

    // Step 1: Create agent with custom max steps
    ReActAgentController.CreateAgentRequestDto createAgentRequest = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequest.setName("Custom Max Steps Agent");
    createAgentRequest.setDescription("Agent with custom max steps");
    createAgentRequest.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequest.setMaxSteps(10); // Custom high limit

    ReActAgentController.CreateAgentResponse agentResp = webClient.post()
      .uri("/agent/react/agent/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createAgentRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateAgentResponse.class)
      .returnResult()
      .getResponseBody();

    // Step 2: Create session
    ReActAgentController.CreateSessionRequestDto createSessionRequest = new ReActAgentController.CreateSessionRequestDto();
    createSessionRequest.setAgentId(agentResp.getAgentId());

    ReActAgentController.CreateSessionResponse sessionResp = webClient.post()
      .uri("/agent/react/session/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(createSessionRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    ReActAgentController.SendMessageRequestDto messageRequest = new ReActAgentController.SendMessageRequestDto();
    messageRequest.setContent("Long task");

    SendMessageResponse response = webClient.post()
      .uri("/agent/react/session/{conversationId}/messages", sessionResp.getConversationId())
      .bodyValue(messageRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(SendMessageResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(response).isNotNull();
    assertThat(response.isFinished()).isTrue();
  }
}
