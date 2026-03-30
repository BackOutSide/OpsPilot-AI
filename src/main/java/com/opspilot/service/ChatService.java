package com.opspilot.service;

import com.opspilot.model.ChatRequest;
import com.opspilot.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Service for multi-turn conversational AI interactions.
 *
 * <p>Wraps the Spring AI {@link ChatClient} and adds per-conversation
 * {@link MessageChatMemoryAdvisor} so that each session retains its own context.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    /**
     * Handle a single (non-streaming) chat turn.
     *
     * @param request the user's chat request including the conversation ID
     * @return the assistant's reply wrapped in a {@link ChatResponse}
     */
    public ChatResponse chat(ChatRequest request) {
        String response = chatClient.prompt()
                .user(request.message())
                .advisors(memoryAdvisor(request.conversationId()))
                .call()
                .content();
        return ChatResponse.builder()
                .conversationId(request.conversationId())
                .response(response)
                .build();
    }

    /**
     * Stream a chat turn as a sequence of token chunks (Server-Sent Events).
     *
     * @param conversationId conversation session identifier
     * @param message        the user's message
     * @return a {@link Flux} of response token strings
     */
    public Flux<String> streamChat(String conversationId, String message) {
        return chatClient.prompt()
                .user(message)
                .advisors(memoryAdvisor(conversationId))
                .stream()
                .content();
    }

    /**
     * Build a {@link MessageChatMemoryAdvisor} bound to the given conversation ID.
     * A new instance is created per request so each conversation maintains isolation.
     */
    private MessageChatMemoryAdvisor memoryAdvisor(String conversationId) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(conversationId)
                .build();
    }
}
