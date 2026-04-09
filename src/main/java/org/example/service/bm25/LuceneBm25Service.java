package org.example.service.bm25;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.example.config.RagRetrievalProperties;
import org.example.dto.DocumentChunk;
import org.example.util.DocumentIndexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Lucene 的 BM25 检索服务
 * 使用本地倒排索引提供关键词/实体命中的稀疏召回能力。
 */
@Service
public class LuceneBm25Service {

    private static final Logger logger = LoggerFactory.getLogger(LuceneBm25Service.class);

    private final RagRetrievalProperties ragRetrievalProperties;

    public LuceneBm25Service(RagRetrievalProperties ragRetrievalProperties) {
        this.ragRetrievalProperties = ragRetrievalProperties;
    }

    public boolean isEnabled() {
        return ragRetrievalProperties.getBm25().isEnabled();
    }

    public void indexDocumentChunks(String filePath, List<DocumentChunk> chunks) {
        if (!isEnabled()) {
            return;
        }

        String normalizedPath = DocumentIndexUtils.normalizeSourcePath(filePath);
        try (Directory directory = openDirectory();
             Analyzer analyzer = createAnalyzer();
             IndexWriter writer = createIndexWriter(directory, analyzer)) {

            deleteBySourceInternal(writer, normalizedPath);

            for (DocumentChunk chunk : chunks) {
                String chunkId = DocumentIndexUtils.buildChunkId(normalizedPath, chunk.getChunkIndex());
                writer.addDocument(buildDocument(normalizedPath, chunkId, chunk));
            }

            writer.commit();
            logger.info("Lucene BM25 索引更新完成: source={}, chunks={}", normalizedPath, chunks.size());
        } catch (Exception e) {
            throw new RuntimeException("Lucene BM25 索引失败: " + e.getMessage(), e);
        }
    }

    public void deleteBySource(String filePath) {
        if (!isEnabled()) {
            return;
        }

        String normalizedPath = DocumentIndexUtils.normalizeSourcePath(filePath);
        try (Directory directory = openDirectory();
             Analyzer analyzer = createAnalyzer();
             IndexWriter writer = createIndexWriter(directory, analyzer)) {
            deleteBySourceInternal(writer, normalizedPath);
            writer.commit();
        } catch (Exception e) {
            logger.warn("删除 Lucene 索引失败: source={}, error={}", normalizedPath, e.getMessage());
        }
    }

    public List<Bm25Hit> search(String queryText, int topN) {
        if (!isEnabled()) {
            return List.of();
        }

        Path indexPath = Paths.get(ragRetrievalProperties.getBm25().getIndexPath()).normalize();
        if (!Files.exists(indexPath)) {
            logger.info("Lucene 索引目录尚不存在，跳过 BM25 检索: {}", indexPath);
            return List.of();
        }

        try (Directory directory = openDirectory()) {
            if (!DirectoryReader.indexExists(directory)) {
                logger.info("Lucene 索引为空，跳过 BM25 检索");
                return List.of();
            }

            try (DirectoryReader reader = DirectoryReader.open(directory);
                 Analyzer analyzer = createAnalyzer()) {
                IndexSearcher searcher = new IndexSearcher(reader);
                searcher.setSimilarity(new BM25Similarity());

                Query query = buildSearchQuery(queryText, analyzer);
                TopDocs topDocs = searcher.search(query, topN);

                List<Bm25Hit> hits = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    Bm25Hit hit = new Bm25Hit();
                    hit.setChunkId(doc.get("chunkId"));
                    hit.setSource(doc.get("source"));
                    hit.setTitle(doc.get("title"));
                    hit.setContent(doc.get("content"));
                    hit.setChunkIndex(parseInt(doc.get("chunkIndex")));
                    hit.setScore(scoreDoc.score);
                    hits.add(hit);
                }

                logger.info("BM25 检索完成: query={}, hits={}", queryText, hits.size());
                return hits;
            }
        } catch (Exception e) {
            throw new RuntimeException("Lucene BM25 检索失败: " + e.getMessage(), e);
        }
    }

    private Query buildSearchQuery(String queryText, Analyzer analyzer) throws Exception {
        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{"title", "content"},
                analyzer
        );
        parser.setDefaultOperator(MultiFieldQueryParser.Operator.OR);

        Query parsedQuery = parser.parse(MultiFieldQueryParser.escape(queryText));
        Query titleBoostQuery = new BoostQuery(parsedQuery, 2.0f);

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(parsedQuery, BooleanClause.Occur.SHOULD);
        builder.add(titleBoostQuery, BooleanClause.Occur.SHOULD);

        for (String token : splitQueryTokens(queryText)) {
            builder.add(new BoostQuery(new TermQuery(new Term("title", token)), 3.0f), BooleanClause.Occur.SHOULD);
            builder.add(new BoostQuery(new TermQuery(new Term("content", token)), 1.2f), BooleanClause.Occur.SHOULD);
        }

        return builder.build();
    }

    private List<String> splitQueryTokens(String queryText) {
        String[] raw = queryText.split("[\\s,，。;；:：/\\\\|]+");
        List<String> tokens = new ArrayList<>();
        for (String token : raw) {
            String trimmed = token.trim();
            if (trimmed.length() >= 2) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private Document buildDocument(String normalizedPath, String chunkId, DocumentChunk chunk) {
        Document document = new Document();
        document.add(new StringField("chunkId", chunkId, Field.Store.YES));
        document.add(new StringField("source", normalizedPath, Field.Store.YES));
        document.add(new StoredField("chunkIndex", chunk.getChunkIndex()));

        String title = chunk.getTitle() == null ? "" : chunk.getTitle();
        document.add(new TextField("title", title, Field.Store.YES));
        document.add(new TextField("content", chunk.getContent(), Field.Store.YES));
        return document;
    }

    private void deleteBySourceInternal(IndexWriter writer, String normalizedPath) throws IOException {
        writer.deleteDocuments(new Term("source", normalizedPath));
    }

    private Directory openDirectory() throws IOException {
        Path indexPath = Paths.get(ragRetrievalProperties.getBm25().getIndexPath()).normalize();
        Files.createDirectories(indexPath);
        return FSDirectory.open(indexPath);
    }

    private Analyzer createAnalyzer() {
        return new SmartChineseAnalyzer();
    }

    private IndexWriter createIndexWriter(Directory directory, Analyzer analyzer) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(new BM25Similarity());
        return new IndexWriter(directory, config);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignore) {
            return 0;
        }
    }

    public static class Bm25Hit {
        private String chunkId;
        private String source;
        private String title;
        private String content;
        private int chunkIndex;
        private float score;

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
    }
}
