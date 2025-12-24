package com.github.spud.sample.ai.agent.domain.react.session;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * React agent session record (maps to react_agent_session table)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActAgentSessionRecord {

  private String conversationId;
  private ReActAgentType agentType;
  private String modelProvider;
  private String systemPrompt;
  private String nextStepPrompt;
  private Integer maxSteps;
  private Integer duplicateThreshold;
  private String toolChoice;
  private ReActSessionStatus status;
  private Integer version;
  private Instant createdAt;
  private Instant updatedAt;
}
