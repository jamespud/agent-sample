package com.github.spud.sample.ai.agent.react.session;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * React agent message record (maps to react_agent_message table)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActAgentMessageRecord {

  private UUID id;
  private String conversationId;
  private Long seq;
  private ReActMessageType messageType;
  private String content;
  private String toolCallId;
  private String toolName;
  private String toolArguments;
  private Instant createdAt;
}
