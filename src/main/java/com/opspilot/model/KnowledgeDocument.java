package com.opspilot.model;

import lombok.Builder;

/**
 * A document to be ingested into the knowledge base for RAG-based retrieval.
 *
 * @param title   Short title or heading of the document.
 * @param content Full text content that will be split, embedded, and stored in Milvus.
 * @param source  Origin of the document (e.g. "confluence", "runbook", "incident-report").
 */
@Builder
public record KnowledgeDocument(String title, String content, String source) {}
