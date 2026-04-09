package org.example.service.retrieval;

import org.example.config.RagRetrievalProperties;
import org.example.service.bm25.LuceneBm25Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF 融合服务
 * 将 dense retrieval 和 BM25 retrieval 的排序结果做 rank-level 融合，
 * 避免直接混加不同量纲分数。
 */
@Service
public class RrfFusionService {

    private static final Logger logger = LoggerFactory.getLogger(RrfFusionService.class);

    private final RagRetrievalProperties ragRetrievalProperties;

    public RrfFusionService(RagRetrievalProperties ragRetrievalProperties) {
        this.ragRetrievalProperties = ragRetrievalProperties;
    }

    public List<RetrievedChunk> fuse(List<RetrievedChunk> denseHits, List<LuceneBm25Service.Bm25Hit> bm25Hits) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();

        addDenseHits(merged, denseHits);
        addBm25Hits(merged, bm25Hits);

        List<RetrievedChunk> fused = new ArrayList<>(merged.values());
        fused.sort(Comparator.comparingDouble(RetrievedChunk::getFusionScore).reversed());

        logger.info("RRF 融合完成: denseHits={}, bm25Hits={}, fused={}",
                denseHits.size(), bm25Hits.size(), fused.size());
        return fused;
    }

    private void addDenseHits(Map<String, RetrievedChunk> merged, List<RetrievedChunk> denseHits) {
        int k = ragRetrievalProperties.getRrfK();
        for (int i = 0; i < denseHits.size(); i++) {
            RetrievedChunk hit = denseHits.get(i);
            RetrievedChunk mergedChunk = merged.computeIfAbsent(hit.getChunkId(), key -> copyOf(hit));
            mergedChunk.setDenseScore(hit.getDenseScore());
            mergedChunk.setFusionScore(mergedChunk.getFusionScore() + reciprocalRank(i, k));
        }
    }

    private void addBm25Hits(Map<String, RetrievedChunk> merged, List<LuceneBm25Service.Bm25Hit> bm25Hits) {
        int k = ragRetrievalProperties.getRrfK();
        for (int i = 0; i < bm25Hits.size(); i++) {
            LuceneBm25Service.Bm25Hit hit = bm25Hits.get(i);
            RetrievedChunk mergedChunk = merged.computeIfAbsent(hit.getChunkId(), key -> {
                RetrievedChunk chunk = new RetrievedChunk();
                chunk.setChunkId(hit.getChunkId());
                chunk.setSource(hit.getSource());
                chunk.setTitle(hit.getTitle());
                chunk.setContent(hit.getContent());
                chunk.setChunkIndex(hit.getChunkIndex());
                return chunk;
            });
            mergedChunk.setBm25Score(hit.getScore());
            mergedChunk.setFusionScore(mergedChunk.getFusionScore() + reciprocalRank(i, k));
        }
    }

    private double reciprocalRank(int rankIndex, int k) {
        return 1.0d / (k + rankIndex + 1);
    }

    private RetrievedChunk copyOf(RetrievedChunk source) {
        RetrievedChunk copy = new RetrievedChunk();
        copy.setChunkId(source.getChunkId());
        copy.setSource(source.getSource());
        copy.setTitle(source.getTitle());
        copy.setContent(source.getContent());
        copy.setMetadata(source.getMetadata());
        copy.setChunkIndex(source.getChunkIndex());
        copy.setDenseScore(source.getDenseScore());
        copy.setBm25Score(source.getBm25Score());
        copy.setFusionScore(source.getFusionScore());
        copy.setRerankScore(source.getRerankScore());
        return copy;
    }
}
