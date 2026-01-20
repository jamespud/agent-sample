package com.github.spud.sample.ai.agent.domain.session;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable snapshot of a session including agent definition and message history
 * Used for API responses and replaying conversations
 */
@Value
@Builder
public class SessionSnapshot {

  /**
   * Unique conversation identifier
   */
  private String conversationId;

  /**
   * Agent definition snapshot captured at session creation
   */
  private AgentDefinitionSnapshot agentSnapshot;

  /**
   * Session status (ACTIVE, FINISHED, ERROR)
   */
  private String status;

  /**
   * Total messages in conversation
   */
  private Integer messageCount;

  /**
   * Session creation timestamp
   */
  private OffsetDateTime createdAt;

  /**
   * Last activity timestamp
   */
  private OffsetDateTime updatedAt;

  /**
   * Whether session is currently in progress
   */
  private boolean active;
}
