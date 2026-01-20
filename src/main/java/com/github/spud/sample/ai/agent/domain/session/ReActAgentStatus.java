package com.github.spud.sample.ai.agent.domain.session;

/**
 * Agent lifecycle status enumeration
 * Controls whether an agent can be used to create new sessions
 */
public enum ReActAgentStatus {
  /**
   * Agent is active and can be used to create new sessions
   */
  ACTIVE,

  /**
   * Agent is disabled and cannot create new sessions
   * Existing sessions continue to work
   */
  DISABLED,

  /**
   * Agent is archived and deprecated
   * Should not be used
   */
  ARCHIVED
}
