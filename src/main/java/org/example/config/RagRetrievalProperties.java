package org.example.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 检索链路配置
 * 用于管理混合召回、RRF 融合和 rerank 的可调参数。
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagRetrievalProperties {

    /**
     * 粗召回候选数，Milvus 和 BM25 都会按这个数量召回。
     */
    private int recallTopN = 10;

    /**
     * 精排后保留的最终结果数。
     */
    private int finalTopK = 3;

    /**
     * Reciprocal Rank Fusion 的平滑参数。
     */
    private int rrfK = 60;

    private Bm25 bm25 = new Bm25();

    private Rerank rerank = new Rerank();

    @Getter
    @Setter
    public static class Bm25 {
        /**
         * 是否启用 Lucene BM25 稀疏检索。
         */
        private boolean enabled = true;

        /**
         * 本地 Lucene 索引目录。
         */
        private String indexPath = "./data/lucene-index";
    }

    @Getter
    @Setter
    public static class Rerank {
        /**
         * 是否启用 bge-rerank-base 精排。
         */
        private boolean enabled = false;

        /**
         * rerank 服务地址，例如 http://localhost:8001/rerank
         */
        private String endpoint = "";

        /**
         * 调用 rerank 服务的超时时间。
         */
        private long timeoutMs = 10000L;
    }
}
