package com.opspilot.model;

import lombok.Builder;

/**
 * Response payload for a (non-streaming) chat request.
 *
 * @param conversationId The conversation session ID echoed back for client tracking.
 * @param response       The assistant's reply.
 */
@Builder
public record ChatResponse(String conversationId, String response) {}
