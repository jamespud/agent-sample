package com.github.spud.sample.ai.agent.react;

import reactor.core.publisher.Mono;

/**
 * React Agent interface for session-based execution
 */
public interface ReactAgent {

  /**
   * Run the agent with a user request
   *
   * @param request user request text
   * @return answer wrapped in Mono
   */
  Mono<String> run(String request);
}
