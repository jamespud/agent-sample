package com.github.spud.sample.ai.agent.infrastructure.persistence.repository;

import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.VectorStore;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VectorStoreRepository extends JpaRepository<VectorStore, UUID>,
  JpaSpecificationExecutor<VectorStore> {

}