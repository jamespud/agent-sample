package com.github.spud.sample.ai.agent.infrastructure.persistence.entity;

import com.github.spud.sample.ai.agent.domain.session.ReActAgentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

/**
 * Agent configuration entity for agent lifecycle management
 * Separates agent definition from session (conversation thread)
 */
@Getter
@Setter
@Entity
@Table(name = "react_agent")
public class ReActAgentConfig {

  @Id
  @Size(max = 36)
  @Column(name = "agent_id", nullable = false, length = 36)
  private String agentId;

  @NotNull
  @Column(name = "name", nullable = false, length = 100)
  @Size(max = 100)
  private String name;

  @NotNull
  @Size(max = 255)
  @Column(name = "description", nullable = false)
  private String description;

  @NotNull
  @Column(name = "agent_type", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private ReActAgentType agentType;

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

  @NotNull
  @ColumnDefault("'ACTIVE'")
  @Column(name = "status", nullable = false, length = 20)
  private String status = "ACTIVE";

  @Column(name = "enabled_tools", columnDefinition = "jsonb")
  private String enabledTools;

  @ColumnDefault("now()")
  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @ColumnDefault("now()")
  @UpdateTimestamp
  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;
}
