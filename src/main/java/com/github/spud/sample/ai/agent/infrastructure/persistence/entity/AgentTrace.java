package com.github.spud.sample.ai.agent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "agent_trace")
public class AgentTrace {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Size(max = 255)
  @NotNull
  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @NotNull
  @Column(name = "step_number", nullable = false)
  private Integer stepNumber;

  @Size(max = 50)
  @NotNull
  @Column(name = "state", nullable = false, length = 50)
  private String state;

  @Size(max = 50)
  @Column(name = "event", length = 50)
  private String event;

  @Column(name = "prompt_summary", length = Integer.MAX_VALUE)
  private String promptSummary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tool_calls")
  private Map<String, Object> toolCalls;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tool_results")
  private Map<String, Object> toolResults;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "retrieval_docs")
  private Map<String, Object> retrievalDocs;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Column(name = "error", length = Integer.MAX_VALUE)
  private String error;

  @ColumnDefault("now()")
  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;


}