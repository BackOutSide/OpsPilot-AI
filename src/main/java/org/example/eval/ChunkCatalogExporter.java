package org.example.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.dto.DocumentChunk;
import org.example.service.DocumentChunkService;
import org.example.util.DocumentIndexUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 导出知识库 chunk 清单，帮助人工标注 chunk 级评测样本。
 */
@Component
public class ChunkCatalogExporter {

    private final DocumentChunkService documentChunkService;
    private final ObjectMapper objectMapper;

    public ChunkCatalogExporter(DocumentChunkService documentChunkService) {
        this.documentChunkService = documentChunkService;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public int export(Path docsDir, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());

        List<ChunkCatalogEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(docsDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(path -> entries.addAll(buildEntriesForFile(path)));
        }

        objectMapper.writeValue(outputFile.toFile(), entries);
        return entries.size();
    }

    private List<ChunkCatalogEntry> buildEntriesForFile(Path path) {
        try {
            String content = Files.readString(path);
            List<DocumentChunk> chunks = documentChunkService.chunkDocument(content, path.toString());
            List<ChunkCatalogEntry> entries = new ArrayList<>();
            String normalized = DocumentIndexUtils.normalizeSourcePath(path.toString());
            String sourceFileName = path.getFileName().toString();

            for (DocumentChunk chunk : chunks) {
                ChunkCatalogEntry entry = new ChunkCatalogEntry();
                entry.setSource(sourceFileName);
                entry.setNormalizedSource(normalized);
                entry.setChunkIndex(chunk.getChunkIndex());
                entry.setChunkKey(sourceFileName + "#" + chunk.getChunkIndex());
                entry.setChunkId(DocumentIndexUtils.buildChunkId(normalized, chunk.getChunkIndex()));
                entry.setTitle(chunk.getTitle());
                entry.setPreview(buildPreview(chunk.getContent()));
                entry.setContentLength(chunk.getContent() == null ? 0 : chunk.getContent().length());
                entries.add(entry);
            }
            return entries;
        } catch (IOException e) {
            throw new RuntimeException("导出 chunk 清单失败: " + path, e);
        }
    }

    private String buildPreview(String content) {
        if (content == null) {
            return "";
        }
        String compact = content.replace("\r", " ").replace("\n", " ").trim();
        return compact.length() <= 120 ? compact : compact.substring(0, 120) + "...";
    }

    public static class ChunkCatalogEntry {
        private String source;
        private String normalizedSource;
        private int chunkIndex;
        private String chunkKey;
        private String chunkId;
        private String title;
        private int contentLength;
        private String preview;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getNormalizedSource() {
            return normalizedSource;
        }

        public void setNormalizedSource(String normalizedSource) {
            this.normalizedSource = normalizedSource;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public String getChunkKey() {
            return chunkKey;
        }

        public void setChunkKey(String chunkKey) {
            this.chunkKey = chunkKey;
        }

        public String getChunkId() {
            return chunkId;
        }

        public void setChunkId(String chunkId) {
            this.chunkId = chunkId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getContentLength() {
            return contentLength;
        }

        public void setContentLength(int contentLength) {
            this.contentLength = contentLength;
        }

        public String getPreview() {
            return preview;
        }

        public void setPreview(String preview) {
            this.preview = preview;
        }
    }
}
