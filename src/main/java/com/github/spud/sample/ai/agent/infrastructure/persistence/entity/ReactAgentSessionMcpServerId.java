package com.github.spud.sample.ai.agent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class ReactAgentSessionMcpServerId implements Serializable {

  private static final long serialVersionUID = 6150634035390066104L;
  @Size(max = 255)
  @NotNull
  @Column(name = "conversation_id", nullable = false)
  private String conversationId;

  @Size(max = 255)
  @NotNull
  @Column(name = "server_id", nullable = false)
  private String serverId;


}