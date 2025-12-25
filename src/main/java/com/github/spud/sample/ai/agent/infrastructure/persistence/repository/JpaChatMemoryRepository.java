package com.github.spud.sample.ai.agent.infrastructure.persistence.repository;

import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ChatMemory;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface JpaChatMemoryRepository extends JpaRepository<ChatMemory, UUID>,
  JpaSpecificationExecutor<ChatMemory>, ChatMemoryRepository {

  @Query("select distinct cm.conversationId from ChatMemory cm")
  @Nonnull
  List<String> findConversationIds();

  @Query("select cm from ChatMemory cm where cm.conversationId = :conversationId order by cm.id asc")
  @Nonnull
  List<Message> findByConversationId(@Nonnull String conversationId);

  @Transactional
  default void saveAll(String conversationId, List<Message> messages) {
    List<ChatMemory> list = messages.stream()
      .filter(message -> message instanceof ChatMemory)
      .map(message -> (ChatMemory) message)
      .toList();
    saveAll(list);
  }

  @Modifying
  @Transactional
  @Query("delete from ChatMemory cm where cm.conversationId = :conversationId")
  void deleteByConversationId(@Nonnull String conversationId);

}