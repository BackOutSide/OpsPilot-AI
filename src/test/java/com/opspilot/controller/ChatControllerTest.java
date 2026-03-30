package com.opspilot.controller;

import com.opspilot.model.ChatRequest;
import com.opspilot.model.ChatResponse;
import com.opspilot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Slice tests for {@link ChatController} using WebFlux test slice.
 *
 * <p>Only the web layer is loaded; {@link ChatService} is provided as a mock.
 */
@WebFluxTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ChatService chatService;

    @Test
    void post_chat_returns200WithResponse() {
        // Arrange
        ChatRequest request = new ChatRequest("conv-1", "What is the SLO for payment-service?");
        ChatResponse expectedResponse = new ChatResponse("conv-1", "The SLO is 99.9% availability.");
        when(chatService.chat(any(ChatRequest.class))).thenReturn(expectedResponse);

        // Act & Assert
        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(ChatResponse.class)
                .value(response -> {
                    assertThat(response.conversationId()).isEqualTo("conv-1");
                    assertThat(response.response()).isEqualTo("The SLO is 99.9% availability.");
                });
    }

    @Test
    void get_stream_returnsEventStream() {
        // Arrange
        when(chatService.streamChat(anyString(), anyString()))
                .thenReturn(Flux.just("The ", "SLO ", "is 99.9%"));

        // Act & Assert
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/chat/stream")
                        .queryParam("conversationId", "conv-2")
                        .queryParam("message", "What is the SLO?")
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(String.class)
                .hasSize(3)
                .contains("The ", "SLO ", "is 99.9%");
    }

    @Test
    void post_chat_withMissingMessage_stillProcesses() {
        // Arrange
        ChatRequest request = new ChatRequest("conv-3", "");
        ChatResponse response = new ChatResponse("conv-3", "How can I help you?");
        when(chatService.chat(any(ChatRequest.class))).thenReturn(response);

        // Act & Assert
        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .value(r -> assertThat(r.conversationId()).isEqualTo("conv-3"));
    }
}
