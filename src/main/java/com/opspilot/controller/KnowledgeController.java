package com.opspilot.controller;

import com.opspilot.model.KnowledgeDocument;
import com.opspilot.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST controller for knowledge-base management and RAG-based Q&amp;A.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/knowledge/documents} — ingest a document into the vector store</li>
 *   <li>{@code POST /api/knowledge/query}     — synchronous RAG Q&amp;A</li>
 *   <li>{@code GET  /api/knowledge/query/stream} — streaming RAG Q&amp;A</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final RagService ragService;

    /**
     * Ingest a document into the knowledge base for future RAG retrieval.
     *
     * <p>Example request body:
     * <pre>{@code
     * {
     *   "title": "Payment Service Runbook",
     *   "content": "The payment service handles ...",
     *   "source": "confluence"
     * }
     * }</pre>
     *
     * @param document the knowledge document to ingest
     * @return a map containing the generated document {@code id} and {@code status}
     */
    @PostMapping(
            value = "/documents",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> ingestDocument(@RequestBody KnowledgeDocument document) {
        return Mono.fromCallable(() -> {
            String docId = ragService.ingestDocument(document);
            return Map.of("id", docId, "status", "indexed");
        });
    }

    /**
     * Synchronous RAG-based question answering.
     *
     * <p>Retrieves relevant document chunks from the vector store and passes them as context
     * to the LLM before generating the answer.
     *
     * <p>Example request body:
     * <pre>{@code
     * {
     *   "question": "How do I roll back the payment service?"
     * }
     * }</pre>
     *
     * @param body request body containing the {@code question} field
     * @return a map with the {@code answer} field
     */
    @PostMapping(
            value = "/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> query(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            String answer = ragService.query(body.get("question"));
            return Map.of("answer", answer);
        });
    }

    /**
     * Streaming RAG-based question answering via Server-Sent Events.
     *
     * @param question the question to answer (query parameter)
     * @return streamed answer tokens
     */
    @GetMapping(
            value = "/query/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamQuery(@RequestParam String question) {
        return ragService.streamQuery(question);
    }
}
