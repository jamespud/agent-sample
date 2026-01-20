package com.github.spud.sample.ai.agent.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for message history loading behavior
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agent.message-history")
public class MessageHistoryProperties {

  /**
   * Maximum number of historical messages to load per conversation
   * Prevents loading entire conversation history which can be memory/performance intensive
   * Default: 10 (reasonable balance between context and performance)
   */
  private int maxMessages = 10;

  /**
   * Optional: Maximum tokens/characters to load (budget-based trimming)
   * Set to -1 to disable (only use message count limit)
   * Default: -1 (disabled, use only count-based limit)
   */
  private int maxTokenBudget = -1;
}
