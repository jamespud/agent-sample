package com.github.spud.sample.ai.agent.domain.session;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable snapshot of agent definition at session creation time
 * Captured to provide consistent configuration for entire conversation
 * Prevents agent config drift affecting ongoing conversations
 */
@Value
@Builder
public class AgentDefinitionSnapshot {

  /**
   * Agent configuration ID
   */
  private String agentId;

  /**
   * Agent type (TOOLCALL, MCP)
   */
  private ReActAgentType agentType;

  /**
   * System prompt for agent
   */
  private String systemPrompt;

  /**
   * Next step prompt for agent
   */
  private String nextStepPrompt;

  /**
   * Maximum steps the agent can take
   */
  private Integer maxSteps;

  /**
   * Model provider (openai, ollama, etc)
   */
  private String modelProvider;

  /**
   * Tools enabled for this agent
   */
  private List<String> enabledTools;

  /**
   * Timestamp when snapshot was captured
   */
  private OffsetDateTime capturedAt;
}
