package com.github.spud.sample.ai.agent.react.session;

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
public class ReactAgentSessionRecord {

  private String conversationId;
  private ReactAgentType agentType;
  private String modelProvider;
  private String systemPrompt;
  private String nextStepPrompt;
  private Integer maxSteps;
  private Integer duplicateThreshold;
  private String toolChoice;
  private ReactSessionStatus status;
  private Integer version;
  private Instant createdAt;
  private Instant updatedAt;
}
