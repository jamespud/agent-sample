package com.github.spud.sample.ai.agent.react.session.repo;

import com.github.spud.sample.ai.agent.react.session.ReactAgentSessionRecord;
import com.github.spud.sample.ai.agent.react.session.ReactAgentType;
import com.github.spud.sample.ai.agent.react.session.ReactSessionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Repository for react_agent_session table
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReactAgentSessionRepository {

  private final JdbcTemplate jdbcTemplate;

  private static final RowMapper<ReactAgentSessionRecord> ROW_MAPPER = new RowMapper<ReactAgentSessionRecord>() {
    @Override
    public ReactAgentSessionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
      return ReactAgentSessionRecord.builder()
        .conversationId(rs.getString("conversation_id"))
        .agentType(ReactAgentType.valueOf(rs.getString("agent_type")))
        .modelProvider(rs.getString("model_provider"))
        .systemPrompt(rs.getString("system_prompt"))
        .nextStepPrompt(rs.getString("next_step_prompt"))
        .maxSteps(rs.getInt("max_steps"))
        .duplicateThreshold(rs.getInt("duplicate_threshold"))
        .toolChoice(rs.getString("tool_choice"))
        .status(ReactSessionStatus.valueOf(rs.getString("status")))
        .version(rs.getInt("version"))
        .createdAt(rs.getTimestamp("created_at").toInstant())
        .updatedAt(rs.getTimestamp("updated_at").toInstant())
        .build();
    }
  };

  /**
   * Create new session with optional MCP servers
   */
  public void create(ReactAgentSessionRecord record, List<String> enabledMcpServers) {
    String sql =
      "INSERT INTO react_agent_session " +
        "(conversation_id, agent_type, model_provider, system_prompt, next_step_prompt, " +
        "max_steps, duplicate_threshold, tool_choice, status, version, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    jdbcTemplate.update(
      sql,
      record.getConversationId(),
      record.getAgentType().name(),
      record.getModelProvider(),
      record.getSystemPrompt(),
      record.getNextStepPrompt(),
      record.getMaxSteps(),
      record.getDuplicateThreshold(),
      record.getToolChoice(),
      record.getStatus().name(),
      record.getVersion(),
      Timestamp.from(record.getCreatedAt()),
      Timestamp.from(record.getUpdatedAt())
    );

    // Insert MCP servers if provided
    if (enabledMcpServers != null && !enabledMcpServers.isEmpty()) {
      String mcpSql =
        "INSERT INTO react_agent_session_mcp_server (conversation_id, server_id) VALUES (?, ?)";
      for (String serverId : enabledMcpServers) {
        jdbcTemplate.update(mcpSql, record.getConversationId(), serverId);
      }
    }

    log.info("Created session: conversationId={}, agentType={}",
      record.getConversationId(), record.getAgentType());
  }

  /**
   * Find session by conversationId
   */
  public Optional<ReactAgentSessionRecord> findByConversationId(String conversationId) {
    String sql = "SELECT * FROM react_agent_session WHERE conversation_id = ?";
    List<ReactAgentSessionRecord> results = jdbcTemplate.query(sql, ROW_MAPPER, conversationId);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  /**
   * List enabled MCP servers for a session
   */
  public List<String> listEnabledMcpServers(String conversationId) {
    String sql =
      "SELECT server_id FROM react_agent_session_mcp_server WHERE conversation_id = ?";
    return jdbcTemplate.queryForList(sql, String.class, conversationId);
  }

  /**
   * Try to bump version (optimistic locking for serialization)
   */
  public boolean tryBumpVersion(String conversationId, int expectedVersion) {
    String sql =
      "UPDATE react_agent_session " +
        "SET version = version + 1, updated_at = NOW() " +
        "WHERE conversation_id = ? AND version = ?";

    int updated = jdbcTemplate.update(sql, conversationId, expectedVersion);
    boolean success = updated > 0;

    if (!success) {
      log.warn("Version conflict: conversationId={}, expectedVersion={}",
        conversationId, expectedVersion);
    }

    return success;
  }
}
