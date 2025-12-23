package com.github.spud.sample.ai.agent.react;

import com.github.spud.sample.ai.agent.react.session.ReactAgentFactory;
import com.github.spud.sample.ai.agent.react.session.ReactAgentSessionRecord;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Test configuration providing a fake ReactAgentFactory for testing
 */
@Slf4j
@TestConfiguration
public class ReactAgentTestConfig {

  @Bean
  @Primary
  public ReactAgentFactory testReactAgentFactory() {
    return new FakeReactAgentFactory();
  }

  /**
   * Fake agent factory that returns agents with predictable behavior
   */
  static class FakeReactAgentFactory implements ReactAgentFactory {

    @Override
    public ReactAgent create(ReactAgentSessionRecord session, List<Message> historyMessages) {
      log.debug("Creating fake agent for testing: conversationId={}", session.getConversationId());
      return new FakeReactAgent(historyMessages);
    }
  }

  /**
   * Fake agent that appends predictable messages to history
   */
  static class FakeReactAgent implements ReactAgent {

    private final List<Message> messages;

    FakeReactAgent(List<Message> messages) {
      this.messages = messages;
    }

    @Override
    public Mono<String> run(String request) {
      // Append USER message
      messages.add(new UserMessage(request));

      // Append ASSISTANT message
      messages.add(new AssistantMessage("Test response to: " + request));

      // Append TOOL message (simulating terminate tool)
      ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
        "test-tool-id",
        "terminate",
        "final"
      );
      messages.add(new ToolResponseMessage(Collections.singletonList(toolResponse)));

      return Mono.just("final");
    }
  }
}
