package com.github.spud.sample.ai.agent.react.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Mapper between DB records and Spring AI Messages
 */
@Component
public class ReActMessageMapper {

  /**
   * Convert DB records to Spring AI messages
   */
  public List<Message> toMessages(List<ReActAgentMessageRecord> records) {
    List<Message> messages = new ArrayList<>();
    for (ReActAgentMessageRecord record : records) {
      Message message = toMessage(record);
      if (message != null) {
        messages.add(message);
      }
    }
    return messages;
  }

  /**
   * Convert single DB record to Spring AI message
   */
  public Message toMessage(ReActAgentMessageRecord record) {
    return switch (record.getMessageType()) {
      case SYSTEM -> new SystemMessage(record.getContent() != null ? record.getContent() : "");
      case USER -> new UserMessage(record.getContent() != null ? record.getContent() : "");
      case ASSISTANT ->
        new AssistantMessage(record.getContent() != null ? record.getContent() : "");
      case TOOL -> {
        // Replay TOOL as SystemMessage with observational format
        String toolObservation = String.format(
          "[TOOL] %s (id=%s) -> %s",
          record.getToolName() != null ? record.getToolName() : "unknown",
          record.getToolCallId() != null ? record.getToolCallId() : "unknown",
          record.getContent() != null ? record.getContent() : ""
        );
        yield new SystemMessage(toolObservation);
      }
    };
  }

  /**
   * Convert Spring AI messages to DB records (for appending after agent run)
   */
  public List<ReActAgentMessageRecord> toRecords(
    String conversationId,
    List<Message> messages
  ) {
    List<ReActAgentMessageRecord> records = new ArrayList<>();
    for (Message message : messages) {
      records.addAll(toRecordsForMessage(conversationId, message));
    }
    return records;
  }

  private List<ReActAgentMessageRecord> toRecordsForMessage(
    String conversationId,
    Message message
  ) {
    ReActAgentMessageRecord.ReActAgentMessageRecordBuilder builder =
      ReActAgentMessageRecord.builder().conversationId(conversationId);

    switch (message.getMessageType()) {
      case SYSTEM:
        return Collections.singletonList(
          builder
            .messageType(ReActMessageType.SYSTEM)
            .content(message.getText())
            .build()
        );

      case USER:
        return Collections.singletonList(
          builder
            .messageType(ReActMessageType.USER)
            .content(message.getText())
            .build()
        );

      case ASSISTANT:
        return Collections.singletonList(
          builder
            .messageType(ReActMessageType.ASSISTANT)
            .content(message.getText())
            .build()
        );

      case TOOL:
        // ToolResponseMessage may contain multiple tool responses
        if (message instanceof ToolResponseMessage) {
          ToolResponseMessage toolMsg = (ToolResponseMessage) message;
          List<ReActAgentMessageRecord> toolRecords = new ArrayList<>();
          for (ToolResponseMessage.ToolResponse response : toolMsg.getResponses()) {
            toolRecords.add(
              builder
                .messageType(ReActMessageType.TOOL)
                .toolCallId(response.id())
                .toolName(response.name())
                .content(response.responseData())
                .build()
            );
          }
          return toolRecords;
        }
        // Fallback for unexpected tool message format
        return Collections.singletonList(
          builder
            .messageType(ReActMessageType.TOOL)
            .content(message.getText())
            .build()
        );

      default:
        return Collections.emptyList();
    }
  }
}
