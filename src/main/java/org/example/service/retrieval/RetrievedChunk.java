package org.example.service.retrieval;

/**
 * 统一候选分片对象
 * 用于承接 dense retrieval、BM25 retrieval、RRF 融合和 rerank 的共享数据结构。
 */
public class RetrievedChunk {

    private String chunkId;
    private String source;
    private String title;
    private String content;
    private String metadata;
    private int chunkIndex;

    private float denseScore;
    private float bm25Score;
    private double fusionScore;
    private double rerankScore;

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public float getDenseScore() {
        return denseScore;
    }

    public void setDenseScore(float denseScore) {
        this.denseScore = denseScore;
    }

    public float getBm25Score() {
        return bm25Score;
    }

    public void setBm25Score(float bm25Score) {
        this.bm25Score = bm25Score;
    }

    public double getFusionScore() {
        return fusionScore;
    }

    public void setFusionScore(double fusionScore) {
        this.fusionScore = fusionScore;
    }

    public double getRerankScore() {
        return rerankScore;
    }

    public void setRerankScore(double rerankScore) {
        this.rerankScore = rerankScore;
    }
}
