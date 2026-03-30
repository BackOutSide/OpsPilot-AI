package com.opspilot.config;

import com.opspilot.tools.AlertQueryTool;
import com.opspilot.tools.LogQueryTool;
import com.opspilot.tools.ReportGenerationTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Spring AI {@link ChatClient} beans used throughout OpsPilot AI.
 *
 * <p>Two clients are provided:
 * <ol>
 *   <li>{@code chatClient} — General-purpose conversational client with short-term memory.
 *       Used for multi-turn Q&amp;A and RAG-enriched answers.</li>
 *   <li>{@code opsAgentChatClient} — AIOps agent client with the same memory setup plus
 *       all registered tool functions (log querying, alert history, report generation)
 *       exposed to the LLM for function-calling.</li>
 * </ol>
 */
@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            You are OpsPilot AI, an enterprise-grade intelligent assistant specialising in \
            IT Operations and Maintenance (O&M).

            Your responsibilities include:
            - Answering business and technical questions using the provided knowledge base context
            - Analysing operational alerts and recommending remediation actions
            - Investigating application logs to identify root causes of incidents
            - Generating clear, executive-friendly operations reports
            - Guiding on-call engineers through incident response procedures

            Always be concise, precise, and actionable. When you are uncertain, say so clearly \
            and suggest how to gather more information.
            """;

    /**
     * In-memory short-term conversation store backed by {@link MessageWindowChatMemory}.
     * Retains up to 20 total messages (user + assistant combined) per conversation.
     * Replace with a persistent store (e.g. Redis-backed) for multi-node deployments.
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    /**
     * General-purpose chat client for conversational Q&amp;A and RAG-augmented answers.
     * The {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor} is
     * added per-request with the caller-supplied conversation ID so each session maintains
     * independent context.
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    /**
     * AIOps agent client pre-loaded with all operational tools.
     * The LLM may autonomously call these tools to gather data before formulating its answer.
     */
    @Bean
    public ChatClient opsAgentChatClient(
            ChatModel chatModel,
            LogQueryTool logQueryTool,
            AlertQueryTool alertQueryTool,
            ReportGenerationTool reportGenerationTool) {

        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(logQueryTool, alertQueryTool, reportGenerationTool)
                .build();
    }
}
