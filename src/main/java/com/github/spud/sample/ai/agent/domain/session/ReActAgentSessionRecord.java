package com.github.spud.sample.ai.agent.domain.session;

import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentSession;
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
  private String agentId;
  private ReActAgentType agentType;
  private String modelProvider;
  private String systemPrompt;
  private String nextStepPrompt;
  private Integer maxSteps;
  private Integer duplicateThreshold;
  private String toolChoice;
  private ReActSessionStatus status;
  private Integer version;
  private java.util.List<String> enabledToolsSnapshot;

  public ReActAgentSession toEntity() {
    ReActAgentSession entity = new ReActAgentSession();
    entity.setConversationId(this.conversationId);
    entity.setAgentId(this.agentId);
    entity.setAgentType(this.agentType);
    entity.setModelProvider(this.modelProvider);
    entity.setSystemPrompt(this.systemPrompt);
    entity.setNextStepPrompt(this.nextStepPrompt);
    entity.setMaxSteps(this.maxSteps);
    entity.setDuplicateThreshold(this.duplicateThreshold);
    entity.setToolChoice(this.toolChoice);
    entity.setStatus(this.status);
    entity.setVersion(this.version);
    entity.setEnabledToolsSnapshot(this.enabledToolsSnapshot);
    return entity;
  }
}
