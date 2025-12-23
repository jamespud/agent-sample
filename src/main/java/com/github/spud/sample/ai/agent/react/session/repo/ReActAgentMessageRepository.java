package com.github.spud.sample.ai.agent.react.session.repo;

import com.github.spud.sample.ai.agent.react.session.ReActAgentMessageRecord;
import com.github.spud.sample.ai.agent.react.session.ReActMessageType;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Repository for react_agent_message table
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReActAgentMessageRepository {

  private final JdbcTemplate jdbcTemplate;

  private static final RowMapper<ReActAgentMessageRecord> ROW_MAPPER = (rs, rowNum) -> ReActAgentMessageRecord.builder()
    .id(UUID.fromString(rs.getString("id")))
    .conversationId(rs.getString("conversation_id"))
    .seq(rs.getLong("seq"))
    .messageType(ReActMessageType.valueOf(rs.getString("message_type")))
    .content(rs.getString("content"))
    .toolCallId(rs.getString("tool_call_id"))
    .toolName(rs.getString("tool_name"))
    .toolArguments(rs.getString("tool_arguments"))
    .createdAt(rs.getTimestamp("created_at").toInstant())
    .build();

  /**
   * List all messages for a conversation (ordered by seq)
   */
  public List<ReActAgentMessageRecord> listMessages(String conversationId) {
    String sql =
      "SELECT * FROM react_agent_message " +
        "WHERE conversation_id = ? " +
        "ORDER BY seq ASC";
    return jdbcTemplate.query(sql, ROW_MAPPER, conversationId);
  }

  /**
   * Append messages to conversation
   */
  public void appendMessages(String conversationId, List<ReActAgentMessageRecord> records) {
    if (records.isEmpty()) {
      return;
    }

    String sql =
      "INSERT INTO react_agent_message " +
        "(conversation_id, message_type, content, tool_call_id, tool_name, tool_arguments, created_at) "
        +
        "VALUES (?, ?, ?, ?, ?, ?, ?)";

    for (ReActAgentMessageRecord record : records) {
      jdbcTemplate.update(
        sql,
        conversationId,
        record.getMessageType().name(),
        record.getContent(),
        record.getToolCallId(),
        record.getToolName(),
        record.getToolArguments(),
        Timestamp.from(
          record.getCreatedAt() != null ? record.getCreatedAt() : java.time.Instant.now())
      );
    }

    log.debug("Appended {} messages to conversationId={}", records.size(), conversationId);
  }
}
