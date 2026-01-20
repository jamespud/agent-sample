package com.github.spud.sample.ai.agent.domain.agent;

/**
 * Exception type that indicates a fatal agent error which should abort the whole run.
 */
public class FatalAgentException extends RuntimeException {
  public FatalAgentException(String message) {
    super(message);
  }

  public FatalAgentException(String message, Throwable cause) {
    super(message, cause);
  }
}
