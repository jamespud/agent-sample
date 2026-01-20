package com.github.spud.sample.ai.agent.infrastructure.persistence.repository;

import com.github.spud.sample.ai.agent.domain.session.ReActAgentStatus;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for React Agent configuration persistence
 */
public interface ReActAgentConfigRepository extends JpaRepository<ReActAgentConfig, String> {

  @Query("SELECT a FROM ReActAgentConfig a WHERE a.agentId = :agentId")
  Optional<ReActAgentConfig> findByAgentId(String agentId);

  /**
   * Find first active agent (optimization to avoid full table scan)
   * Uses derived query for standard JPA/JPQL compatibility (no LIMIT)
   * Ordered by creation timestamp to get the first created agent
   * @return First active agent if available
   */
  Optional<ReActAgentConfig> findFirstByStatusOrderByCreatedAtAsc(ReActAgentStatus status);
}
