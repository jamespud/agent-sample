package com.github.spud.sample.ai.agent.infrastructure.persistence.repository;

import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;

public interface ReActAgentMessageRepository extends JpaRepository<ReActAgentMessage, UUID>,
  JpaSpecificationExecutor<ReActAgentMessage> {

  default List<ReActAgentMessage> listMessages(String conversationId) {
    return findAllByConversationIdOrderBySeqAsc(conversationId, Limit.of(10));
  }

  List<ReActAgentMessage> findAllByConversationIdOrderBySeqAsc(String conversationId, Limit limit);

  @Modifying
  default void appendMessages(String conversationId, List<ReActAgentMessage> records) {
    this.saveAll(records);
  }
}