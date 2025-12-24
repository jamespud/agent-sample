package com.github.spud.sample.ai.agent.react;

import com.github.spud.sample.ai.agent.domain.react.agent.ReActAgent;
import com.github.spud.sample.ai.agent.domain.react.session.ReActAgentFactory;
import com.github.spud.sample.ai.agent.domain.react.session.ReActAgentSessionRecord;
import java.util.Collections;
import java.util.List;
import lombok.experimental.SuperBuilder;
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
public class ReActAgentTestConfig {

  @Bean
  @Primary
  public ReActAgentFactory testReactAgentFactory() {
    return new FakeReActAgentFactory();
  }

  /**
   * Fake agent factory that returns agents with predictable behavior
   */
  static class FakeReActAgentFactory implements ReActAgentFactory {

    @Override
    public ReActAgent create(ReActAgentSessionRecord session, List<Message> historyMessages) {
      log.debug("Creating fake agent for testing: conversationId={}", session.getConversationId());
      return new FakeReactAgent.FakeReactAgentBuilderImpl().messages(historyMessages).build();
    }
  }

  /**
   * Fake agent that appends predictable messages to history
   */
  @SuperBuilder
  static class FakeReactAgent extends ReActAgent {

    private List<Message> messages;

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

    @Override
    protected Mono<Boolean> think() {
      return null;
    }

    @Override
    protected Mono<String> act() {
      return null;
    }

    @Override
    protected Mono<String> step() {
      return null;
    }
  }
}
