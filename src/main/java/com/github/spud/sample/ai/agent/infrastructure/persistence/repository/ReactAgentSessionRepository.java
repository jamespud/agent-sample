package com.github.spud.sample.ai.agent.infrastructure.persistence.repository;

import com.github.spud.sample.ai.agent.domain.react.session.ReActAgentSessionRecord;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReactAgentSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

public interface ReactAgentSessionRepository extends JpaRepository<ReactAgentSession, String>,
  JpaSpecificationExecutor<ReactAgentSession> {

  @Query("""
    INSERT INTO ReactAgentSession (conversationId, agentType, modelProvider, systemPrompt,
                                      nextStepPrompt, maxSteps, duplicateThreshold, toolChoice,
                                      status, version)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """)
  @Modifying
  void create();

  Optional<ReActAgentSessionRecord> findByConversationId(String conversationId);

  @NativeQuery("SELECT server_id FROM react_agent_session_mcp_server WHERE conversation_id = ?1")
  List<String> listEnabledMcpServers(String conversationId);

  @Query("UPDATE ReactAgentSession SET version = version + 1 WHERE conversationId = ?1 AND version = ?2")
  @Modifying
  boolean tryBumpVersion(String conversationId, int expectedVersion);

}