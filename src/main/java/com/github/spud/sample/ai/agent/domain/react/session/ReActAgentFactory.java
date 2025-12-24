package com.github.spud.sample.ai.agent.domain.react.session;

import com.github.spud.sample.ai.agent.domain.react.agent.ReActAgent;
import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * Factory for creating React Agent instances
 */
public interface ReActAgentFactory {

  /**
   * Create a new agent instance based on session configuration
   *
   * @param session         session configuration record
   * @param historyMessages conversation history to initialize agent with
   * @return new agent instance
   */
  ReActAgent create(ReActAgentSessionRecord session, List<Message> historyMessages);
}
