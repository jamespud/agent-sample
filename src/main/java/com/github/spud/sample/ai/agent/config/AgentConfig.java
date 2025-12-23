package com.github.spud.sample.ai.agent.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AgentConfig {

  /**
   * Postgres 向量存储配置 使用 PgVectorStore 结合 OpenAI Embedding Model
   */
  @Bean
  public PgVectorStore vectorStore(JdbcTemplate jdbcTemplate,
    @Qualifier("agentEmbeddingModel") EmbeddingModel embeddingModel,
    PgVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
    ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
    BatchingStrategy batchingStrategy) {

    var initializeSchema = properties.isInitializeSchema();

    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
      .schemaName(properties.getSchemaName())
      .idType(properties.getIdType())
      .vectorTableName(properties.getTableName())
      .vectorTableValidationsEnabled(properties.isSchemaValidation())
      .dimensions(properties.getDimensions())
      .distanceType(properties.getDistanceType())
      .removeExistingVectorStoreTable(properties.isRemoveExistingVectorStoreTable())
      .indexType(properties.getIndexType())
      .initializeSchema(initializeSchema)
      .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
      .customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
      .batchingStrategy(batchingStrategy)
      .maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
      .build();
  }
}
