package com.github.spud.sample.ai.agent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.springframework.ai.chat.messages.MessageType;

@Getter
@Setter
@Entity
@Table(name = "react_agent_message")
public class ReActAgentMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "conversation_id", insertable = false, updatable = false, nullable = false)
  private String conversationId;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "conversation_id", nullable = false)
  private ReActAgentSession conversation;

  @ColumnDefault("nextval('react_agent_message_seq')")
  @Column(name = "seq", nullable = false, insertable = false, updatable = false)
  private Long seq;

  @NotNull
  @Column(name = "message_type", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private MessageType messageType;

  @NotNull
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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tool_calls", columnDefinition = "jsonb")
  private List<Map<String, Object>> toolCalls;

  @ColumnDefault("now()")
  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  public ReActAgentMessage() {
  }
}
