package org.example.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk 级检索评测样本
 * expectedChunkIndexes 支持一个 query 对应多个可接受 chunk。
 */
public class ChunkEvalSample {

    private String id;
    private String query;
    private String expectedSource;
    private List<Integer> expectedChunkIndexes = new ArrayList<>();
    private String note;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
