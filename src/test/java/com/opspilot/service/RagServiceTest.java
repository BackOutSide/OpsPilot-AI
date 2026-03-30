package com.opspilot.service;

import com.opspilot.model.KnowledgeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RagService}.
 */
@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(chatClient, vectorStore);
    }

    @Test
    void ingestDocument_storesDocumentAndReturnsId() {
        // Arrange
        KnowledgeDocument doc = new KnowledgeDocument(
                "Payment Runbook", "Step 1: Check logs...", "confluence");
        doNothing().when(vectorStore).add(any());

        // Act
        String docId = ragService.ingestDocument(doc);

        // Assert
        assertThat(docId).isNotBlank();
        verify(vectorStore, times(1)).add(argThat(docs ->
                !((List<?>) docs).isEmpty()));
    }

    @Test
    void ingestDocument_embeddedDocumentContainsTitle() {
        // Arrange
        KnowledgeDocument doc = new KnowledgeDocument("Runbook", "Content here", "wiki");
        doNothing().when(vectorStore).add(any());

        // Act
        ragService.ingestDocument(doc);

        // Assert - capture the document passed to vectorStore
        verify(vectorStore).add(argThat(docs -> {
            List<Document> documents = (List<Document>) docs;
            return documents.size() == 1
                    && "Runbook".equals(documents.get(0).getMetadata().get("title"));
        }));
    }

    @Test
    void query_returnsAnswerFromLlm() {
        // Arrange
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("To roll back, run: kubectl rollout undo");

        // Act
        String answer = ragService.query("How do I roll back the payment service?");

        // Assert
        assertThat(answer).contains("kubectl rollout undo");
    }

    @Test
    void streamQuery_returnsTokenFlux() {
        // Arrange
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("Step ", "1: ", "Check logs"));

        // Act & Assert
        StepVerifier.create(ragService.streamQuery("What are the steps?"))
                .expectNext("Step ")
                .expectNext("1: ")
                .expectNext("Check logs")
                .verifyComplete();
    }
}
