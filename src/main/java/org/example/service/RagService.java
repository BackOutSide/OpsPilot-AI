package org.example.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import io.reactivex.Flowable;
import org.example.config.RagRetrievalProperties;
import org.example.service.bm25.LuceneBm25Service;
import org.example.service.retrieval.BgeRerankClient;
import org.example.service.retrieval.RetrievedChunk;
import org.example.service.retrieval.RrfFusionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG (Retrieval-Augmented Generation) 服务
 * 结合向量检索和大语言模型生成答案
 */
@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private LuceneBm25Service luceneBm25Service;

    @Autowired
    private RrfFusionService rrfFusionService;

    @Autowired
    private BgeRerankClient bgeRerankClient;

    @Autowired
    private RagRetrievalProperties ragRetrievalProperties;

    @org.springframework.beans.factory.annotation.Value("${dashscope.api.key}")
    private String apiKey;

    @org.springframework.beans.factory.annotation.Value("${rag.model:qwen3-30b-a3b-thinking-2507}")
    private String model;

    private Generation generation;

    public enum RetrievalMode {
        DENSE_ONLY,
        BM25_ONLY,
        HYBRID_RRF,
        HYBRID_RRF_RERANK
    }

    public record RetrievalDebugResult(
            RetrievalMode mode,
            List<RetrievedChunk> finalChunks,
            List<RetrievedChunk> recallChunks
    ) {
    }

    @PostConstruct
    public void init() {
        // 设置 API Key 和 Base URL
        Constants.apiKey = apiKey;
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
        
        // 创建 Generation 实例
        generation = new Generation();
        
        logger.info("RAG 服务初始化完成，model: {}, recallTopN: {}, finalTopK: {}",
                model, ragRetrievalProperties.getRecallTopN(), ragRetrievalProperties.getFinalTopK());
    }

    /**
     * 流式处理用户问题（不带历史消息）
     * 
     * @param question 用户问题
     * @param callback 流式回调接口
     */
    public void queryStream(String question, StreamCallback callback) {
        queryStream(question, new ArrayList<>(), callback);
    }

    /**
     * 流式处理用户问题（带历史消息）
     * 
     * @param question 用户问题
     * @param history 历史消息列表，格式：[{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}]
     * @param callback 流式回调接口
     */
    public void queryStream(String question, List<Map<String, String>> history, StreamCallback callback) {
        try {
            logger.info("收到 RAG 流式查询: {}", question);

            // 1. 混合召回：Milvus dense retrieval + Lucene BM25 retrieval
            List<RetrievedChunk> searchResults = retrieveRelevantChunks(question);

            // 发送检索结果
            callback.onSearchResults(searchResults);

            if (searchResults.isEmpty()) {
                logger.warn("未找到相关文档");
                callback.onComplete("抱歉，我在知识库中没有找到相关信息来回答您的问题。", "");
                return;
            }

            // 2. 构建上下文和提示词
            String context = buildContext(searchResults);
            String prompt = buildPrompt(question, context);

            // 3. 流式调用大语言模型（传入历史消息）
            generateAnswerStream(prompt, history, callback);

        } catch (Exception e) {
            logger.error("RAG 流式查询失败", e);
            callback.onError(e);
        }
    }

    /**
     * 构建上下文
     */
    private String buildContext(List<RetrievedChunk> searchResults) {
        StringBuilder context = new StringBuilder();
        
        for (int i = 0; i < searchResults.size(); i++) {
            RetrievedChunk result = searchResults.get(i);
            context.append("【参考资料 ").append(i + 1).append("】\n");
            if (result.getTitle() != null && !result.getTitle().isBlank()) {
                context.append("标题: ").append(result.getTitle()).append("\n");
            }
            context.append(result.getContent()).append("\n\n");
        }
        
        return context.toString();
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(String question, String context) {
        return String.format(
            "你是一个专业的AI助手。请根据以下参考资料回答用户的问题。\n\n" +
            "参考资料：\n%s\n" +
            "用户问题：%s\n\n" +
            "请基于上述参考资料给出准确、详细的回答。如果参考资料中没有相关信息，请明确说明。",
            context, question
        );
    }

    private List<RetrievedChunk> retrieveRelevantChunks(String question) {
        int recallTopN = ragRetrievalProperties.getRecallTopN();
        int finalTopK = ragRetrievalProperties.getFinalTopK();

        List<RetrievedChunk> denseHits = safeDenseRetrieve(question, recallTopN);
        List<LuceneBm25Service.Bm25Hit> bm25Hits = safeBm25Retrieve(question, recallTopN);

        List<RetrievedChunk> fused = fuseWithFallback(denseHits, bm25Hits);
        List<RetrievedChunk> reranked = safeRerank(question, fused, finalTopK * 2);

        reranked.sort(resolveFinalComparator());
        List<RetrievedChunk> finalResults = deduplicateAndLimit(reranked, finalTopK);

        logger.info("混合检索完成: dense={}, bm25={}, fused={}, final={}",
                denseHits.size(), bm25Hits.size(), fused.size(), finalResults.size());
        return finalResults;
    }

    private List<RetrievedChunk> safeDenseRetrieve(String question, int recallTopN) {
        try {
            return vectorSearchService.searchSimilarChunks(question, recallTopN);
        } catch (Exception e) {
            logger.warn("Dense retrieval 失败，降级为无 dense 候选: {}", e.getMessage());
            return List.of();
        }
    }

    private List<LuceneBm25Service.Bm25Hit> safeBm25Retrieve(String question, int recallTopN) {
        try {
            return luceneBm25Service.search(question, recallTopN);
        } catch (Exception e) {
            logger.warn("BM25 retrieval 失败，降级为无 sparse 候选: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RetrievedChunk> fuseWithFallback(List<RetrievedChunk> denseHits,
                                                  List<LuceneBm25Service.Bm25Hit> bm25Hits) {
        if (!denseHits.isEmpty() || !bm25Hits.isEmpty()) {
            try {
                return rrfFusionService.fuse(denseHits, bm25Hits);
            } catch (Exception e) {
                logger.warn("RRF 融合失败，按单路候选回退: {}", e.getMessage());
            }
        }

        if (!denseHits.isEmpty()) {
            return new ArrayList<>(denseHits);
        }

        if (!bm25Hits.isEmpty()) {
            List<RetrievedChunk> sparseOnly = new ArrayList<>();
            for (LuceneBm25Service.Bm25Hit hit : bm25Hits) {
                RetrievedChunk chunk = new RetrievedChunk();
                chunk.setChunkId(hit.getChunkId());
                chunk.setSource(hit.getSource());
                chunk.setTitle(hit.getTitle());
                chunk.setContent(hit.getContent());
                chunk.setChunkIndex(hit.getChunkIndex());
                chunk.setBm25Score(hit.getScore());
                chunk.setFusionScore(hit.getScore());
                sparseOnly.add(chunk);
            }
            return sparseOnly;
        }

        return List.of();
    }

    private List<RetrievedChunk> safeRerank(String question, List<RetrievedChunk> fused, int finalTopK) {
        try {
            return bgeRerankClient.rerank(question, fused, finalTopK);
        } catch (Exception e) {
            logger.warn("Rerank 失败，降级为融合结果排序: {}", e.getMessage());
            List<RetrievedChunk> fallback = new ArrayList<>(fused);
            fallback.sort(Comparator.comparingDouble(RetrievedChunk::getFusionScore).reversed());
            if (fallback.size() > finalTopK) {
                return new ArrayList<>(fallback.subList(0, finalTopK));
            }
            return fallback;
        }
    }

    /**
     * 暴露给本地调试/离线评测使用的检索入口
     * 不触发大模型生成，仅返回最终参与生成的检索结果。
     */
    public List<RetrievedChunk> retrieveForDebug(String question) {
        return retrieveRelevantChunks(question);
    }

    public RetrievalDebugResult retrieveForDebug(String question, RetrievalMode mode) {
        int recallTopN = ragRetrievalProperties.getRecallTopN();
        int finalTopK = ragRetrievalProperties.getFinalTopK();

        return switch (mode) {
            case DENSE_ONLY -> {
                List<RetrievedChunk> denseHits = safeDenseRetrieve(question, recallTopN);
                yield new RetrievalDebugResult(
                        mode,
                        deduplicateAndLimit(denseHits, finalTopK),
                        deduplicateAndLimit(denseHits, recallTopN)
                );
            }
            case BM25_ONLY -> {
                List<RetrievedChunk> bm25Chunks = convertBm25Hits(safeBm25Retrieve(question, recallTopN));
                yield new RetrievalDebugResult(
                        mode,
                        deduplicateAndLimit(bm25Chunks, finalTopK),
                        deduplicateAndLimit(bm25Chunks, recallTopN)
                );
            }
            case HYBRID_RRF -> {
                List<RetrievedChunk> denseHits = safeDenseRetrieve(question, recallTopN);
                List<LuceneBm25Service.Bm25Hit> bm25Hits = safeBm25Retrieve(question, recallTopN);
                List<RetrievedChunk> fused = fuseWithFallback(denseHits, bm25Hits);
                yield new RetrievalDebugResult(
                        mode,
                        deduplicateAndLimit(fused, finalTopK),
                        deduplicateAndLimit(fused, recallTopN)
                );
            }
            case HYBRID_RRF_RERANK -> {
                List<RetrievedChunk> denseHits = safeDenseRetrieve(question, recallTopN);
                List<LuceneBm25Service.Bm25Hit> bm25Hits = safeBm25Retrieve(question, recallTopN);
                List<RetrievedChunk> fused = fuseWithFallback(denseHits, bm25Hits);
                List<RetrievedChunk> reranked = safeRerank(question, fused, finalTopK * 2);
                reranked.sort(resolveFinalComparator());
                yield new RetrievalDebugResult(
                        mode,
                        deduplicateAndLimit(reranked, finalTopK),
                        deduplicateAndLimit(fused, recallTopN)
                );
            }
        };
    }

    private List<RetrievedChunk> deduplicateAndLimit(List<RetrievedChunk> chunks, int limit) {
        Map<String, RetrievedChunk> uniqueChunks = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            String key = chunk.getSource() + "#" + chunk.getChunkIndex();
            uniqueChunks.putIfAbsent(key, chunk);
            if (uniqueChunks.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(uniqueChunks.values());
    }

    private List<RetrievedChunk> convertBm25Hits(List<LuceneBm25Service.Bm25Hit> bm25Hits) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (LuceneBm25Service.Bm25Hit hit : bm25Hits) {
            RetrievedChunk chunk = new RetrievedChunk();
            chunk.setChunkId(hit.getChunkId());
            chunk.setSource(hit.getSource());
            chunk.setTitle(hit.getTitle());
            chunk.setContent(hit.getContent());
            chunk.setChunkIndex(hit.getChunkIndex());
            chunk.setBm25Score(hit.getScore());
            chunk.setFusionScore(hit.getScore());
            chunks.add(chunk);
        }
        return chunks;
    }

    private Comparator<RetrievedChunk> resolveFinalComparator() {
        if (bgeRerankClient.isEnabled()) {
            return Comparator.comparingDouble(RetrievedChunk::getRerankScore).reversed();
        }
        return Comparator.comparingDouble(RetrievedChunk::getFusionScore).reversed();
    }

    /**
     * 生成答案（流式）
     * 
     * @param prompt 当前问题的提示词
     * @param history 历史消息列表
     * @param callback 流式回调接口
     */
    private void generateAnswerStream(String prompt, List<Map<String, String>> history, StreamCallback callback) 
            throws NoApiKeyException, ApiException, InputRequiredException {
        
        // 构建消息列表：历史消息 + 当前问题
        List<Message> messages = new ArrayList<>();
        
        // 添加历史消息
        for (Map<String, String> historyMsg : history) {
            String role = historyMsg.get("role"); 
            String content = historyMsg.get("content");
            
            if ("user".equals(role)) {
                messages.add(Message.builder()
                        .role(Role.USER.getValue())
                        .content(content)
                        .build());
            } else if ("assistant".equals(role)) {
                messages.add(Message.builder()
                        .role(Role.ASSISTANT.getValue())
                        .content(content)
                        .build());
            }
        }
        
        // 添加当前用户问题
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();
        messages.add(userMsg);
        
        logger.debug("发送给AI模型的消息数量: {}（包含 {} 条历史消息）", 
            messages.size(), history.size());

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .incrementalOutput(true)
                .resultFormat("message")
                .messages(messages)
                .build();

        logger.info("开始调用AI模型流式接口...");
        
        Flowable<GenerationResult> result = generation.streamCall(param);
        
        StringBuilder reasoningContent = new StringBuilder();
        StringBuilder finalContent = new StringBuilder();
        
        logger.info("开始接收AI模型流式响应...");

        result.blockingForEach(message -> {
            if (message.getOutput() != null && 
                message.getOutput().getChoices() != null && 
                !message.getOutput().getChoices().isEmpty()) {
                
                // 获取消息内容
                // 注意：qwen3-30b-a3b-thinking-2507 模型会在 content 中返回完整内容
                // reasoning 部分可能需要通过特殊方式提取或者直接包含在 content 中
                String content = message.getOutput().getChoices().get(0).getMessage().getContent();

                if (content != null && !content.isEmpty()) {
                    logger.debug("收到AI模型内容块: {}", content);
                    
                    // 对于 thinking 模型，content 可能包含思考过程和最终答案
                    // 这里我们将所有内容都作为答案返回
                    finalContent.append(content);
                    callback.onContentChunk(content);
                    
                    logger.debug("已调用 onContentChunk 回调");
                } else {
                    logger.debug("收到空内容块，跳过");
                }
            }
        });
        
        logger.info("AI模型流式响应完成，总内容长度: {}", finalContent.length());

        callback.onComplete(finalContent.toString(), reasoningContent.toString());
        logger.info("已调用 onComplete 回调");
    }

    /**
     * 流式回调接口
     */
    public interface StreamCallback {
        void onSearchResults(List<RetrievedChunk> results);
        void onReasoningChunk(String chunk);
        void onContentChunk(String chunk);
        void onComplete(String fullContent, String fullReasoning);
        void onError(Exception e);
    }
}
