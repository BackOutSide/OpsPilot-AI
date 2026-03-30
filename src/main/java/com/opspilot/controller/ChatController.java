package com.opspilot.controller;

import com.opspilot.model.ChatRequest;
import com.opspilot.model.ChatResponse;
import com.opspilot.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for multi-turn conversational AI.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/chat} — synchronous single-response chat</li>
 *   <li>{@code GET  /api/chat/stream} — Server-Sent Events streaming chat</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Single-response (non-streaming) chat endpoint.
     *
     * <p>Example request body:
     * <pre>{@code
     * {
     *   "conversationId": "550e8400-e29b-41d4-a716-446655440000",
     *   "message": "What is the deployment procedure for the payment service?"
     * }
     * }</pre>
     *
     * @param request the chat request
     * @return the assistant's response
     */
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        return Mono.fromCallable(() -> chatService.chat(request));
    }

    /**
     * Streaming chat endpoint using Server-Sent Events.
     *
     * <p>Each SSE event contains one token fragment from the model's response.
     * The client should concatenate all events to reconstruct the full reply.
     *
     * @param conversationId the conversation session identifier
     * @param message        the user's message text
     * @return a stream of token strings
     */
    @GetMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestParam String conversationId,
            @RequestParam String message) {
        return chatService.streamChat(conversationId, message);
    }
}
