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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.type.SqlTypes;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.content.Media;

@Getter
@Setter
@Entity
@Table(name = "chat_memory")
public class ChatMemory {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false, columnDefinition = "uuid default gen_random_uuid()")
  private UUID id;

  @Size(max = 255)
  @NotNull
  @Column(name = "conversation_id", nullable = false)
  private String conversationId;

  @Size(max = 50)
  @NotNull
  @Column(name = "message_type", nullable = false, length = 50)
  private String messageType;

  @Column(name = "content", length = Integer.MAX_VALUE)
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata")
  private Map<String, Object> metadata;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tool_calls", length = Integer.MAX_VALUE)
  private List<ToolCall> toolCalls;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "media", length = Integer.MAX_VALUE)
  private List<Media> media;

  @ColumnDefault("now()")
  @CreationTimestamp(source = SourceType.DB)
  @Column(name = "created_at", columnDefinition = "timestampz")
  private OffsetDateTime createdAt;

}