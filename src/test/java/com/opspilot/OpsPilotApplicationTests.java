package com.opspilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Smoke test: verifies that the Spring application context loads without errors.
 *
 * <p>AI model beans are mocked so that no real DashScope API key is needed,
 * and the in-memory vector store is used via the {@code test} profile.
 */
@SpringBootTest
@ActiveProfiles("test")
class OpsPilotApplicationTests {

    @MockBean
    ChatModel chatModel;

    @MockBean
    EmbeddingModel embeddingModel;

    @Test
    void contextLoads() {
        // If the context starts without an exception, the test passes.
    }
}
