package com.github.spud.sample.ai.agent.kernel.protocol;

import lombok.Getter;

/**
 * ReAct JSON 解析异常
 */
@Getter
public class ReactJsonParseException extends Exception {

  private final String originalText;
  private final String reason;

  public ReactJsonParseException(String reason, String originalText) {
    super("Failed to parse ReAct JSON: " + reason);
    this.reason = reason;
    this.originalText = originalText;
  }

  public ReactJsonParseException(String reason, String originalText, Throwable cause) {
    super("Failed to parse ReAct JSON: " + reason, cause);
    this.reason = reason;
    this.originalText = originalText;
  }

}
