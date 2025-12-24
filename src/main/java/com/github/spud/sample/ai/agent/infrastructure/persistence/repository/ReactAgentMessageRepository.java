package com.github.spud.sample.ai.agent.infrastructure.persistence.repository;

import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReactAgentMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReactAgentMessageRepository extends JpaRepository<ReactAgentMessage, UUID>,
  JpaSpecificationExecutor<ReactAgentMessage> {


}