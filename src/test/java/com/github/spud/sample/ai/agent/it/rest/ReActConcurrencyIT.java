package com.github.spud.sample.ai.agent.it.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.spud.sample.ai.agent.domain.session.ReActAgentType;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentMessageRepository;
import com.github.spud.sample.ai.agent.interfaces.rest.ReActAgentController;
import com.github.spud.sample.ai.agent.it.support.ContainersSupport;
import com.github.spud.sample.ai.agent.it.support.OpenAiMockSupport;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for ReAct Agent concurrency handling
 * Covers scenario S8: Concurrent requests to same conversationId
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30000")
@ActiveProfiles("it")
class ReActConcurrencyIT extends ContainersSupport {

  static {
    OpenAiMockSupport.getServer();
  }

  @Autowired
  private WebTestClient webTestClient;

  @Autowired
  private ReActAgentMessageRepository messageRepository;

  @BeforeEach
  void setUp() {
    OpenAiMockSupport.clearQueue();
  }

  @Test
  @DisplayName("S8: Concurrent requests to same session should handle version conflict")
  void s8_concurrentRequestsVersionConflict() {
    // Enqueue enough responses for both concurrent requests
    for (int i = 0; i < 6; i++) {
      String terminateArgs = "{\"answer\": \"Concurrent answer " + i + "\"}";
      MockResponse response = new OpenAiMockSupport.ResponseBuilder()
        .withToolCall("call-" + i, "terminate", terminateArgs)
        .build();
      OpenAiMockSupport.enqueueResponse(response);
    }

    // Create session
    String conversationId = createToolCallSession(3);

    // Concurrent message sends
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    CompletableFuture<Void> request1 = CompletableFuture.runAsync(() -> {
      try {
        sendMessageExpectingStatus(conversationId, "Request 1", HttpStatus.OK);
        successCount.incrementAndGet();
      } catch (AssertionError e) {
        if (e.getMessage().contains("409") || e.getMessage().contains("CONFLICT")) {
          conflictCount.incrementAndGet();
        } else {
          throw e;
        }
      }
    });

    CompletableFuture<Void> request2 = CompletableFuture.runAsync(() -> {
      try {
        sendMessageExpectingStatus(conversationId, "Request 2", HttpStatus.OK);
        successCount.incrementAndGet();
      } catch (AssertionError e) {
        if (e.getMessage().contains("409") || e.getMessage().contains("CONFLICT")) {
          conflictCount.incrementAndGet();
        } else {
          throw e;
        }
      }
    });

    // Wait for both to complete
    CompletableFuture.allOf(request1, request2).join();

    // Invariant: at least one should succeed, conflicts are acceptable
    assertThat(successCount.get() + conflictCount.get()).isEqualTo(2);
    assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

    // Verify DB consistency: messages should be in correct order
    await().atMost(Duration.ofSeconds(5))
      .untilAsserted(() -> {
        var messages = messageRepository.listMessages(conversationId);
        assertThat(messages).isNotEmpty();
        // Should have USER + ASSISTANT + TOOL at minimum
        assertThat(messages.size()).isGreaterThanOrEqualTo(3);
      });
  }

  private String createToolCallSession(int maxSteps) {
    // Step 1: Create agent
    ReActAgentController.CreateAgentRequestDto createAgentRequest = new ReActAgentController.CreateAgentRequestDto();
    createAgentRequest.setName("Concurrent Test Agent");
    createAgentRequest.setDescription("Agent for concurrent testing");
    createAgentRequest.setAgentType(ReActAgentType.TOOLCALL);
    createAgentRequest.setMaxSteps(maxSteps);

    ReActAgentController.CreateAgentResponse agentResp = webTestClient.post()
      .uri("/agent/react/agent/new")
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

    ReActAgentController.CreateSessionResponse response = webTestClient.post()
      .uri("/agent/react/session/new")
      .bodyValue(createSessionRequest)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody();

    assertThat(response).isNotNull();
    return response.getConversationId();
  }

  private void sendMessageExpectingStatus(String conversationId, String content,
    HttpStatus expectedStatus) {
    ReActAgentController.SendMessageRequestDto request = new ReActAgentController.SendMessageRequestDto();
    request.setContent(content);

    webTestClient.post()
      .uri("/agent/react/session/{id}/messages", Map.of("id", conversationId))
      .bodyValue(request)
      .exchange()
      .expectStatus().isEqualTo(expectedStatus);
  }
}
