package com.github.spud.sample.ai.agent.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spud.sample.ai.agent.domain.react.session.ReActAgentType;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentMessageRepository;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentSessionRepository;
import com.github.spud.sample.ai.agent.interfaces.rest.ReActAgentController;
import com.github.spud.sample.ai.agent.react.ReActAgentTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration test for ReactAgentController
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(ReActAgentTestConfig.class)
class ReActAgentControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  @Autowired
  private ReActAgentSessionRepository sessionRepository;

  @Autowired
  private ReActAgentMessageRepository messageRepository;

  @Test
  void shouldCreateToolCallSessionAndSendMessage() {
    // Create session
    String requestBody = """
      {
        "agentType": "TOOLCALL",
        "modelProvider": "openai",
        "maxSteps": 10
      }
      """;

    String conversationId = webTestClient.post()
      .uri("/agent/react/session/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(requestBody)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody()
      .getConversationId();

    // Verify session exists in DB
    var session = sessionRepository.findByConversationId(conversationId);
    assertThat(session).isPresent();
    assertThat(session.get().getAgentType()).isEqualTo(ReActAgentType.TOOLCALL);

    // Send message
    String messageBody = """
      {
        "content": "Hello agent"
      }
      """;

    webTestClient.post()
      .uri("/agent/react/session/" + conversationId + "/messages")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(messageBody)
      .exchange()
      .expectStatus().isOk()
      .expectBody()
      .jsonPath("$.conversationId").isEqualTo(conversationId)
      .jsonPath("$.answer").isNotEmpty()
      .jsonPath("$.finished").isBoolean()
      .jsonPath("$.appendedMessages").isArray();

    // Verify messages in DB
    var messages = messageRepository.listMessages(conversationId);
    assertThat(messages).isNotEmpty();
    assertThat(messages).anyMatch(m -> m.getMessageType() == MessageType.USER);
    assertThat(messages).anyMatch(m -> m.getMessageType() == MessageType.ASSISTANT);
  }

  @Test
  void shouldCreateMcpSessionWithEnabledServers() {
    // Create MCP session with enabled servers
    String requestBody = """
      {
        "agentType": "MCP",
        "enabledMcpServers": ["server1", "server2"]
      }
      """;

    String conversationId = webTestClient.post()
      .uri("/agent/react/session/new")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(requestBody)
      .exchange()
      .expectStatus().isOk()
      .expectBody(ReActAgentController.CreateSessionResponse.class)
      .returnResult()
      .getResponseBody()
      .getConversationId();

    // Verify MCP servers in DB
    List<String> mcpServers = sessionRepository.listEnabledMcpServers(conversationId);
    assertThat(mcpServers).hasSize(2);
    assertThat(mcpServers).containsExactlyInAnyOrder("server1", "server2");
  }

  @Test
  void shouldReturn404ForNonExistentSession() {
    String messageBody = """
      {
        "content": "Hello"
      }
      """;

    webTestClient.post()
      .uri("/agent/react/session/non-existent-id/messages")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(messageBody)
      .exchange()
      .expectStatus().isNotFound();
  }
}
