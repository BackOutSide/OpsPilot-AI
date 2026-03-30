package com.opspilot.model;

import lombok.Builder;

/**
 * Request payload for chat interactions.
 *
 * @param conversationId Unique identifier for a conversation session. A new UUID should be
 *                       supplied by the client for the first message; subsequent messages
 *                       in the same conversation must reuse the same ID so that the chat
 *                       memory advisor can maintain context.
 * @param message        The user's message text.
 */
@Builder
public record ChatRequest(String conversationId, String message) {}
