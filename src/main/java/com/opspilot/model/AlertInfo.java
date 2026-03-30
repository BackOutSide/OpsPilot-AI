package com.opspilot.model;

import lombok.Builder;

/**
 * Represents an operational alert submitted for AI-driven analysis.
 *
 * @param alertId   Unique identifier for the alert (e.g. UUID or monitoring-system ID).
 * @param service   Name of the affected service or component.
 * @param severity  Alert severity level: {@code CRITICAL}, {@code HIGH}, {@code MEDIUM},
 *                  or {@code LOW}.
 * @param message   Human-readable alert message / description.
 * @param timestamp ISO-8601 timestamp of when the alert was triggered.
 */
@Builder
public record AlertInfo(
        String alertId,
        String service,
        String severity,
        String message,
        String timestamp) {}
