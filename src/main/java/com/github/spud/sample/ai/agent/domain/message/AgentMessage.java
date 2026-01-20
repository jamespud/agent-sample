package com.github.spud.sample.ai.agent.domain.message;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.chat.messages.MessageType;

/**
 * Domain model for agent messages (conversion layer between Spring AI Message and persistence entity)
 * 
 * This serves as the single source of truth for message content, based on Spring AI Message structure
 * with additional fields needed for agent execution tracking.
 */
@Data
@Builder
public class AgentMessage {

  private MessageType messageType;
  
  private String content;
  
  private Map<String, Object> metadata;
  
  // Tool execution tracking fields
  private String toolCallId;
  
  private String toolName;
  
  private String toolArguments;

  // For AssistantMessage: array of tool calls (each call has name, id, arguments)
  private List<Map<String, Object>> toolCalls;
  
  // Ordering and timestamp
  private Long seq;
  
  private OffsetDateTime createdAt;
}
