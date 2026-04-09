package org.example.service.retrieval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.config.RagRetrievalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * bge-rerank-base HTTP 客户端
 * 将 rerank 模型服务化，主应用只关注候选文档的打分与排序结果。
 */
@Service
public class BgeRerankClient {

    private static final Logger logger = LoggerFactory.getLogger(BgeRerankClient.class);

    private final RagRetrievalProperties ragRetrievalProperties;

    public BgeRerankClient(RagRetrievalProperties ragRetrievalProperties) {
        this.ragRetrievalProperties = ragRetrievalProperties;
    }

    public boolean isEnabled() {
        RagRetrievalProperties.Rerank rerank = ragRetrievalProperties.getRerank();
        return rerank.isEnabled() && rerank.getEndpoint() != null && !rerank.getEndpoint().isBlank();
    }

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
        if (!isEnabled() || candidates.isEmpty()) {
            return fallback(candidates, topK);
        }

        try {
            RestClient client = buildClient();
            RerankRequest request = new RerankRequest();
            request.setQuery(query);
            request.setTopN(Math.min(topK, candidates.size()));

            List<String> documents = new ArrayList<>();
            for (RetrievedChunk candidate : candidates) {
                String docText = candidate.getTitle() == null || candidate.getTitle().isBlank()
                        ? candidate.getContent()
                        : candidate.getTitle() + "\n" + candidate.getContent();
                documents.add(docText);
            }
            request.setDocuments(documents);

            RerankResponse response = client.post()
                    .uri(URI.create(ragRetrievalProperties.getRerank().getEndpoint()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RerankResponse.class);

            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                logger.warn("bge-rerank-base 返回空结果，回退到 RRF 排序");
                return fallback(candidates, topK);
            }

            List<RetrievedChunk> reranked = new ArrayList<>();
            for (RerankItem item : response.getResults()) {
                if (item.getIndex() < 0 || item.getIndex() >= candidates.size()) {
                    continue;
                }
                RetrievedChunk chunk = candidates.get(item.getIndex());
                chunk.setRerankScore(item.getScore());
                reranked.add(chunk);
            }

            reranked.sort(Comparator.comparingDouble(RetrievedChunk::getRerankScore).reversed());
            if (reranked.size() > topK) {
                return new ArrayList<>(reranked.subList(0, topK));
            }
            return reranked;
        } catch (Exception e) {
            logger.warn("调用 bge-rerank-base 失败，回退到 RRF 排序: {}", e.getMessage());
            return fallback(candidates, topK);
        }
    }

    private List<RetrievedChunk> fallback(List<RetrievedChunk> candidates, int topK) {
        List<RetrievedChunk> fallback = new ArrayList<>(candidates);
        fallback.sort(Comparator.comparingDouble(RetrievedChunk::getFusionScore).reversed());
        if (fallback.size() > topK) {
            return new ArrayList<>(fallback.subList(0, topK));
        }
        return fallback;
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = (int) Math.min(Integer.MAX_VALUE, ragRetrievalProperties.getRerank().getTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public static class RerankRequest {
        private String query;

        @JsonProperty("documents")
        private List<String> documents;

        @JsonProperty("top_n")
        private int topN;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public List<String> getDocuments() {
            return documents;
        }

        public void setDocuments(List<String> documents) {
            this.documents = documents;
        }

        public int getTopN() {
            return topN;
        }

        public void setTopN(int topN) {
            this.topN = topN;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RerankResponse {
        private List<RerankItem> results;

        public List<RerankItem> getResults() {
            return results;
        }

        public void setResults(List<RerankItem> results) {
            this.results = results;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RerankItem {
        private int index;
        private double score;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }
}
