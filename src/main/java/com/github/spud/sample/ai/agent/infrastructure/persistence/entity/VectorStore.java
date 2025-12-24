package com.github.spud.sample.ai.agent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
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
@Table(name = "vector_store")
public class VectorStore {

  @Id
  @ColumnDefault("gen_random_uuid()")
  @Column(name = "id", nullable = false)
  private UUID id;

  @NotNull
  @Column(name = "content", nullable = false, length = Integer.MAX_VALUE)
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata")
  private Map<String, Object> metadata;

  @Column(name = "embedding", columnDefinition = "vector(1536)")
  private List<Float> embedding;

  @ColumnDefault("now()")
  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;


}