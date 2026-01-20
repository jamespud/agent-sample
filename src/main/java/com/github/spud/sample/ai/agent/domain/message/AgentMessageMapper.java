package com.github.spud.sample.ai.agent.domain.message;

import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentMessage;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between domain messages, persistence entities, and Spring AI messages
 */
@Component
public class AgentMessageMapper {

  /**
   * Convert domain message to persistence entity
   */
  public ReActAgentMessage toEntity(AgentMessage domain, ReActAgentSession session) {
    ReActAgentMessage entity = new ReActAgentMessage();
    entity.setConversation(session);
    entity.setMessageType(domain.getMessageType());
    entity.setContent(domain.getContent() != null ? domain.getContent() : "");
    entity.setMetadata(domain.getMetadata() != null ? new HashMap<>(domain.getMetadata()) : new HashMap<>());
    entity.setToolCallId(domain.getToolCallId());
    entity.setToolName(domain.getToolName());
    entity.setToolArguments(domain.getToolArguments());
    entity.setToolCalls(domain.getToolCalls() != null ? new ArrayList<>(domain.getToolCalls()) : null);
    entity.setSeq(domain.getSeq());
    return entity;
  }

  /**
   * Convert persistence entity to domain message
   */
  public AgentMessage toDomain(ReActAgentMessage entity) {
    return AgentMessage.builder()
      .messageType(entity.getMessageType())
      .content(entity.getContent())
      .metadata(entity.getMetadata() != null ? new HashMap<>(entity.getMetadata()) : new HashMap<>())
      .toolCallId(entity.getToolCallId())
      .toolName(entity.getToolName())
      .toolArguments(entity.getToolArguments())
      .toolCalls(entity.getToolCalls() != null ? new ArrayList<>(entity.getToolCalls()) : null)
      .seq(entity.getSeq())
      .build();
  }

  /**
   * Convert entity list to domain list
   */
  public List<AgentMessage> toDomainList(List<ReActAgentMessage> entities) {
    List<AgentMessage> domains = new ArrayList<>(entities.size());
    for (ReActAgentMessage entity : entities) {
      domains.add(toDomain(entity));
    }
    return domains;
  }

  /**
   * Convert domain messages to Spring AI messages (for agent execution)
   */
  public List<AbstractMessage> toSpringMessages(List<AgentMessage> domains) {
    List<AbstractMessage> messages = new ArrayList<>(domains.size());
    for (AgentMessage domain : domains) {
      messages.add(toSpringMessage(domain));
    }
    return messages;
  }

  /**
   * Convert single domain message to Spring AI message
   */
  public AbstractMessage toSpringMessage(AgentMessage domain) {
    Map<String, Object> metadata = domain.getMetadata() != null ? domain.getMetadata() : new HashMap<>();
    
    switch (domain.getMessageType()) {
      case USER:
        return new UserMessage(domain.getContent());
      
      case ASSISTANT:
        // For assistant messages with tool calls, we need to reconstruct them
        // For now, create simple assistant message
        return new AssistantMessage(domain.getContent(), metadata);
      
      case SYSTEM:
        return new SystemMessage(domain.getContent());
      
      case TOOL:
        // Tool response messages need special handling
        if (domain.getToolCallId() != null && domain.getToolName() != null) {
          ToolResponseMessage.ToolResponse toolResponse = 
            new ToolResponseMessage.ToolResponse(
              domain.getToolCallId(), 
              domain.getToolName(), 
              domain.getContent()
            );
          return new ToolResponseMessage(List.of(toolResponse));
        }
        // Fallback to system message if tool metadata incomplete
        return new SystemMessage(domain.getContent());
      
      default:
        // Default to system message for unknown types
        return new SystemMessage(domain.getContent());
    }
  }

  /**
   * Convert Spring AI message to domain (for storing new messages from agent)
   */
  public AgentMessage fromSpringMessage(AbstractMessage springMessage) {
    AgentMessage.AgentMessageBuilder builder = AgentMessage.builder()
      .messageType(springMessage.getMessageType())
      .content(springMessage.getText())
      .metadata(springMessage.getMetadata() != null ? new HashMap<>(springMessage.getMetadata()) : new HashMap<>());

    // Extract tool-related fields if present
    if (springMessage instanceof AssistantMessage assistantMsg) {
      // Capture tool calls from AssistantMessage
      List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
      if (toolCalls != null && !toolCalls.isEmpty()) {
        List<Map<String, Object>> toolCallsList = new ArrayList<>();
        for (AssistantMessage.ToolCall tc : toolCalls) {
          Map<String, Object> tcMap = new HashMap<>();
          tcMap.put("id", tc.id());
          tcMap.put("name", tc.name());
          tcMap.put("arguments", tc.arguments());
          toolCallsList.add(tcMap);
        }
        builder.toolCalls(toolCallsList);
      }
    } else if (springMessage instanceof ToolResponseMessage toolMsg) {
      // Extract tool response details
      if (!toolMsg.getResponses().isEmpty()) {
        ToolResponseMessage.ToolResponse response = toolMsg.getResponses().get(0);
        builder.toolCallId(response.id())
          .toolName(response.name())
          .content(response.responseData());
      }
    }

    return builder.build();
  }
}
