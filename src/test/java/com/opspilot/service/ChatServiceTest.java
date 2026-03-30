package com.opspilot.service;

import com.opspilot.model.ChatRequest;
import com.opspilot.model.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChatService}.
 *
 * <p>The {@link ChatClient} and its builder fluent API are mocked so that no real
 * DashScope connection is required.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatClient, MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build());
    }

    @Test
    void chat_returnsAssistantResponse() {
        // Arrange
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Hello, I can help with that.");

        ChatRequest request = new ChatRequest("conv-1", "Hello");

        // Act
        ChatResponse response = chatService.chat(request);

        // Assert
        assertThat(response.conversationId()).isEqualTo("conv-1");
        assertThat(response.response()).isEqualTo("Hello, I can help with that.");
    }

    @Test
    void streamChat_returnsTokenFlux() {
        // Arrange
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("Hello", ", ", "world", "!"));

        // Act
        Flux<String> result = chatService.streamChat("conv-2", "Hi there");

        // Assert
        StepVerifier.create(result)
                .expectNext("Hello")
                .expectNext(", ")
                .expectNext("world")
                .expectNext("!")
                .verifyComplete();
    }

    @Test
    void chat_propagatesConversationId() {
        // Arrange
        String conversationId = "session-abc-123";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Response");

        // Act
        ChatResponse response = chatService.chat(new ChatRequest(conversationId, "test"));

        // Assert
        assertThat(response.conversationId()).isEqualTo(conversationId);
    }
}
