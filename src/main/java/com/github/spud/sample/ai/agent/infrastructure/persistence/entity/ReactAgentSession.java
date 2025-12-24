package com.github.spud.sample.ai.agent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "react_agent_session")
public class ReactAgentSession {

  @Id
  @Size(max = 255)
  @Column(name = "conversation_id", nullable = false)
  private String conversationId;

  @Size(max = 20)
  @NotNull
  @Column(name = "agent_type", nullable = false, length = 20)
  private String agentType;

  @Size(max = 50)
  @NotNull
  @Column(name = "model_provider", nullable = false, length = 50)
  private String modelProvider;

  @NotNull
  @Column(name = "system_prompt", nullable = false, length = Integer.MAX_VALUE)
  private String systemPrompt;

  @Column(name = "next_step_prompt", length = Integer.MAX_VALUE)
  private String nextStepPrompt;

  @NotNull
  @Column(name = "max_steps", nullable = false)
  private Integer maxSteps;

  @NotNull
  @Column(name = "duplicate_threshold", nullable = false)
  private Integer duplicateThreshold;

  @Size(max = 20)
  @NotNull
  @Column(name = "tool_choice", nullable = false, length = 20)
  private String toolChoice;

  @Size(max = 20)
  @NotNull
  @ColumnDefault("'ACTIVE'")
  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @NotNull
  @ColumnDefault("0")
  @Column(name = "version", nullable = false)
  private Integer version;

  @ColumnDefault("now()")
  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @ColumnDefault("now()")
  @UpdateTimestamp
  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;


}