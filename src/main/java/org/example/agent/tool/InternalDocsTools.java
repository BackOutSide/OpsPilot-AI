package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部文档查询工具
 * 使用 RAG (Retrieval-Augmented Generation) 从内部知识库检索相关文档
 */
@Component
public class InternalDocsTools {

    private static final int MAX_SNIPPET_LENGTH = 220;
    
    private static final Logger logger = LoggerFactory.getLogger(InternalDocsTools.class);
    
    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_QUERY_INTERNAL_DOCS = "queryInternalDocs";
    
    private final VectorSearchService vectorSearchService;
    
    @Value("${rag.top-k:3}")
    private int topK = 3; // 默认值
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 构造函数注入依赖
     * Spring 会自动注入 VectorSearchService
     */
    @Autowired
    public InternalDocsTools(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }
    
    /**
     * 查询内部文档工具
     *
     * @param query 搜索查询，描述您要查找的信息
     * @return JSON 格式的搜索结果，包含相关文档内容、相似度分数和元数据
     */
    @Tool(description = "Use this tool to search internal documentation and knowledge base for relevant information. " +
            "It performs RAG (Retrieval-Augmented Generation) to find similar documents and extract processing steps. " +
            "This is useful when you need to understand internal procedures, best practices, or step-by-step guides " +
            "stored in the company's documentation. " +
            "The result is a structured JSON payload with concise snippets rather than full raw documents.")
    public String queryInternalDocs(
            @ToolParam(description = "Search query describing what information you are looking for") 
            String query) {
        

        try {
            // 使用向量搜索服务检索相关文档
            List<VectorSearchService.SearchResult> searchResults = 
                    vectorSearchService.searchSimilarDocuments(query, topK);

            InternalDocsOutput output = new InternalDocsOutput();
            output.setQuery(query);
            output.setTopK(topK);
            output.setReturned(searchResults.size());
            
            if (searchResults.isEmpty()) {
                output.setStatus("no_results");
                output.setMessage("No relevant documents found in the knowledge base.");
                output.setResults(List.of());
                return objectMapper.writeValueAsString(output);
            }

            List<StructuredDocResult> structuredResults = new ArrayList<>();
            for (VectorSearchService.SearchResult searchResult : searchResults) {
                StructuredDocResult structuredResult = new StructuredDocResult();
                structuredResult.setId(searchResult.getId());
                structuredResult.setTitle(blankToDefault(searchResult.getTitle(), "Untitled Document"));
                structuredResult.setSource(blankToDefault(searchResult.getSource(), "unknown"));
                structuredResult.setChunkIndex(searchResult.getChunkIndex());
                structuredResult.setScore(roundScore(searchResult.getScore()));
                structuredResult.setSnippet(buildSnippet(searchResult.getContent()));
                structuredResults.add(structuredResult);
            }

            output.setStatus("success");
            output.setMessage(String.format("Found %d relevant document chunks.", structuredResults.size()));
            output.setResults(structuredResults);

            return objectMapper.writeValueAsString(output);
            
        } catch (Exception e) {
            logger.error("[工具错误] queryInternalDocs 执行失败", e);
            InternalDocsOutput output = new InternalDocsOutput();
            output.setStatus("error");
            output.setQuery(query);
            output.setTopK(topK);
            output.setReturned(0);
            output.setMessage(String.format("Failed to query internal docs: %s", e.getMessage()));
            output.setResults(List.of());
            try {
                return objectMapper.writeValueAsString(output);
            } catch (Exception serializationException) {
                return String.format("{\"status\":\"error\",\"message\":\"Failed to query internal docs: %s\"}",
                        e.getMessage());
            }
        }
    }

    private String buildSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET_LENGTH) + "...";
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private float roundScore(float score) {
        return Math.round(score * 10000f) / 10000f;
    }

    public static class InternalDocsOutput {
        private String status;
        private String query;
        private int topK;
        private int returned;
        private String message;
        private List<StructuredDocResult> results;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getReturned() {
            return returned;
        }

        public void setReturned(int returned) {
            this.returned = returned;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<StructuredDocResult> getResults() {
            return results;
        }

        public void setResults(List<StructuredDocResult> results) {
            this.results = results;
        }
    }

    public static class StructuredDocResult {
        private String id;
        private String title;
        private String source;
        private int chunkIndex;
        private float score;
        private String snippet;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public float getScore() {
            return score;
        }

        public void setScore(float score) {
            this.score = score;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }
    }
}
