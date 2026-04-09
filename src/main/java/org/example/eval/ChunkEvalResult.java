package org.example.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条 chunk 级评测结果
 */
public class ChunkEvalResult {

    private String sampleId;
    private String query;
    private String expectedSource;
    private List<Integer> expectedChunkIndexes = new ArrayList<>();
    private List<String> retrievedChunkKeys = new ArrayList<>();
    private boolean hitAt1;
    private boolean hitAt3;
    private int firstHitRank = -1;

    public String getSampleId() {
        return sampleId;
    }

    public void setSampleId(String sampleId) {
        this.sampleId = sampleId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getExpectedSource() {
        return expectedSource;
    }

    public void setExpectedSource(String expectedSource) {
        this.expectedSource = expectedSource;
    }

    public List<Integer> getExpectedChunkIndexes() {
        return expectedChunkIndexes;
    }

    public void setExpectedChunkIndexes(List<Integer> expectedChunkIndexes) {
        this.expectedChunkIndexes = expectedChunkIndexes;
    }

    public List<String> getRetrievedChunkKeys() {
        return retrievedChunkKeys;
    }

    public void setRetrievedChunkKeys(List<String> retrievedChunkKeys) {
        this.retrievedChunkKeys = retrievedChunkKeys;
    }

    public boolean isHitAt1() {
        return hitAt1;
    }

    public void setHitAt1(boolean hitAt1) {
        this.hitAt1 = hitAt1;
    }

    public boolean isHitAt3() {
        return hitAt3;
    }

    public void setHitAt3(boolean hitAt3) {
        this.hitAt3 = hitAt3;
    }

    public int getFirstHitRank() {
        return firstHitRank;
    }

    public void setFirstHitRank(int firstHitRank) {
        this.firstHitRank = firstHitRank;
    }
}
