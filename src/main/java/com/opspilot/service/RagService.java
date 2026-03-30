package com.opspilot.service;

import com.opspilot.model.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for RAG (Retrieval-Augmented Generation) based knowledge Q&amp;A.
 *
 * <p>Handles two concerns:
 * <ol>
 *   <li>Document ingestion — splits, embeds, and stores knowledge documents in the
 *       configured {@link VectorStore} (Milvus in production).</li>
 *   <li>Question answering — uses {@link QuestionAnswerAdvisor} to retrieve relevant
 *       document chunks and inject them as context before passing the query to the LLM.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    /**
     * Ingest a knowledge document into the vector store.
     *
     * <p>The document content is embedded using the configured {@link EmbeddingModel}
     * (DashScope text-embedding) and stored for future retrieval.
     *
     * @param knowledgeDocument the document to index
     * @return the generated document ID
     */
    public String ingestDocument(KnowledgeDocument knowledgeDocument) {
        String docId = UUID.randomUUID().toString();
        Document doc = new Document(
                knowledgeDocument.content(),
                Map.of(
                        "id", docId,
                        "title", knowledgeDocument.title(),
                        "source", knowledgeDocument.source()));
        vectorStore.add(List.of(doc));
        log.info("Ingested document: id={}, title={}, source={}",
                docId, knowledgeDocument.title(), knowledgeDocument.source());
        return docId;
    }

    /**
     * Answer a question using RAG: retrieve relevant context from the vector store and
     * pass it to the LLM.
     *
     * @param question the user's question
     * @return the LLM's answer, grounded in the retrieved knowledge
     */
    public String query(String question) {
        return chatClient.prompt()
                .user(question)
                .advisors(new QuestionAnswerAdvisor(vectorStore))
                .call()
                .content();
    }

    /**
     * Stream a RAG-based answer token by token.
     *
     * @param question the user's question
     * @return a {@link Flux} of answer token strings
     */
    public Flux<String> streamQuery(String question) {
        return chatClient.prompt()
                .user(question)
                .advisors(new QuestionAnswerAdvisor(vectorStore))
                .stream()
                .content();
    }
}
