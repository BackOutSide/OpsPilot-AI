package com.opspilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OpsPilot AI — Enterprise-oriented intelligent business agent system.
 *
 * <p>Combines RAG-based knowledge retrieval with AIOps automation, providing:
 * <ul>
 *   <li>Multi-turn conversational AI with streaming responses</li>
 *   <li>Business knowledge retrieval via vector-based RAG (Milvus + DashScope)</li>
 *   <li>Intelligent alert analysis and root-cause investigation</li>
 *   <li>Log-driven anomaly detection through agent tool calling</li>
 *   <li>Automated operations reporting</li>
 * </ul>
 */
@SpringBootApplication
public class OpsPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsPilotApplication.class, args);
    }
}
