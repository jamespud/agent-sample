package com.github.spud.sample.ai.agent.infrastructure.persistence.repository;

import com.github.spud.sample.ai.agent.application.config.MessageHistoryProperties;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;

public interface ReActAgentMessageRepository extends JpaRepository<ReActAgentMessage, UUID>,
  JpaSpecificationExecutor<ReActAgentMessage> {

  List<ReActAgentMessage> findAllByConversationIdOrderBySeqAsc(String conversationId, Limit limit);

  @Modifying
  default void appendMessages(String conversationId, List<ReActAgentMessage> records) {
    this.saveAll(records);
  }

  /**
   * Load messages with configurable window size
   * @param conversationId conversation ID
   * @param historyProperties configuration for message history limits
   * @return messages within configured limit
   */
  default List<ReActAgentMessage> listMessages(String conversationId, 
      MessageHistoryProperties historyProperties) {
    return findAllByConversationIdOrderBySeqAsc(conversationId, 
      Limit.of(historyProperties.getMaxMessages()));
  }

  /**
   * Load messages with default window (for backward compatibility in tests)
   * Uses hardcoded limit of 10; prefer listMessages(conversationId, historyProperties) in production
   * @param conversationId conversation ID
   * @return messages within default limit
   */
  default List<ReActAgentMessage> listMessages(String conversationId) {
    return findAllByConversationIdOrderBySeqAsc(conversationId, Limit.of(10));
  }
}