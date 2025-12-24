package com.github.spud.sample.ai.agent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "react_agent_message")
public class ReactAgentMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "conversation_id", nullable = false)
  private ReactAgentSession conversation;

  @NotNull
  @ColumnDefault("nextval('react_agent_message_seq_seq')")
  @Column(name = "seq", nullable = false)
  private Long seq;

  @Size(max = 20)
  @NotNull
  @Column(name = "message_type", nullable = false, length = 20)
  private String messageType;

  @Column(name = "content", length = Integer.MAX_VALUE)
  private String content;

  @Size(max = 255)
  @Column(name = "tool_call_id")
  private String toolCallId;

  @Size(max = 255)
  @Column(name = "tool_name")
  private String toolName;

  @Column(name = "tool_arguments", length = Integer.MAX_VALUE)
  private String toolArguments;

  @ColumnDefault("now()")
  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;


}