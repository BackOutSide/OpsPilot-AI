package com.opspilot.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the {@link VectorStore} used for RAG-based knowledge retrieval.
 *
 * <p>When {@code spring.ai.vectorstore.milvus.client.host} is set to a non-empty value,
 * the Milvus auto-configuration (from {@code spring-ai-starter-vector-store-milvus})
 * registers a {@link org.springframework.ai.vectorstore.milvus.MilvusVectorStore} bean
 * automatically — no code is needed here.
 *
 * <p>This class provides a lightweight in-memory fallback so that the application
 * can start without a running Milvus instance during local development and testing.
 * Set {@code MILVUS_HOST} environment variable (or the property directly) to a real
 * Milvus host to switch to the production Milvus store.
 */
@Configuration
public class VectorStoreConfig {

    /**
     * In-memory vector store used when {@code spring.ai.vectorstore.milvus.client.host}
     * is empty or not set — i.e. when Milvus is not configured.
     *
     * <p>NOTE: data is lost on application restart. Use Milvus in production.
     */
    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(
            prefix = "spring.ai.vectorstore.milvus.client",
            name = "host",
            havingValue = "",
            matchIfMissing = true)
    public VectorStore inMemoryVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
