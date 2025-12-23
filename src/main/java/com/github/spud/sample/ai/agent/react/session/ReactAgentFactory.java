package com.github.spud.sample.ai.agent.react.session;

import com.github.spud.sample.ai.agent.react.ReactAgent;
import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * Factory for creating React Agent instances
 */
public interface ReactAgentFactory {

  /**
   * Create a new agent instance based on session configuration
   *
   * @param session         session configuration record
   * @param historyMessages conversation history to initialize agent with
   * @return new agent instance
   */
  ReactAgent create(ReactAgentSessionRecord session, List<Message> historyMessages);
}
