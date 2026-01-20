package com.github.spud.sample.ai.agent.infrastructure.persistence.repository;

import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

public interface ReActAgentSessionRepository extends JpaRepository<ReActAgentSession, String>,
  JpaSpecificationExecutor<ReActAgentSession> {

  @Modifying
  default void create(ReActAgentSession record, List<String> enabledMcpServers) {
    this.save(record);
    if (enabledMcpServers != null && !enabledMcpServers.isEmpty()) {
      for (String serverId : enabledMcpServers) {
        this.enableMcpServer(record.getConversationId(), serverId);
      }
    }
  }

  @Modifying
  @NativeQuery("INSERT INTO react_agent_session_mcp_server (conversation_id, server_id) VALUES (?1, ?2)")
  void enableMcpServer(String conversationId, String serverId);

  @Query("SELECT r FROM ReActAgentSession r WHERE r.conversationId = :conversationId")
  Optional<ReActAgentSession> findByConversationId(String conversationId);

  @NativeQuery("SELECT server_id FROM react_agent_session_mcp_server WHERE conversation_id = ?1")
  List<String> listEnabledMcpServers(String conversationId);

  @Modifying
  @Query("UPDATE ReActAgentSession SET version = version + 1 WHERE conversationId = :conversationId AND version = :expectedVersion")
  Integer tryBumpVersion(String conversationId, int expectedVersion);

  @Query("SELECT distinct r.conversationId FROM ReActAgentSession r")
  List<String> listAllConversationIds();

}