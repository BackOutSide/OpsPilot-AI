package com.opspilot.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * Agent tool that provides log querying capabilities for incident investigation.
 *
 * <p>In a real deployment, implementations would call a log aggregation platform
 * (e.g. Elasticsearch, Loki, Splunk). This implementation returns realistic-looking
 * synthetic data so that the agent can be exercised end-to-end without external
 * dependencies.
 */
@Component
public class LogQueryTool {

    private static final List<String> SAMPLE_LEVELS = List.of("ERROR", "WARN", "INFO", "DEBUG");
    private static final Random RANDOM = new Random();

    /**
     * Query application logs for a given service within a time window.
     *
     * @param serviceName Name of the service whose logs should be fetched.
     * @param startTime   Query start time in ISO-8601 format.
     * @param endTime     Query end time in ISO-8601 format.
     * @return Formatted log excerpt relevant to the query window.
     */
    @Tool(description = "Query application logs for a specific service within a time range. "
            + "Use this to investigate errors, exceptions, and anomalies reported for a service.")
    public String queryLogs(
            @ToolParam(description = "Name of the service to retrieve logs for (e.g. 'payment-service')")
            String serviceName,
            @ToolParam(description = "Log query start time in ISO-8601 format (e.g. '2024-01-15T08:00:00Z')")
            String startTime,
            @ToolParam(description = "Log query end time in ISO-8601 format (e.g. '2024-01-15T09:00:00Z')")
            String endTime) {

        StringBuilder logs = new StringBuilder();
        logs.append(String.format("=== Log Query: service=%s, from=%s, to=%s ===%n",
                serviceName, startTime, endTime));

        Instant now = Instant.now();
        logs.append(String.format("[%s] ERROR %s - NullPointerException in PaymentProcessor.charge()%n", now.minusSeconds(300), serviceName));
        logs.append(String.format("[%s] WARN  %s - Database connection pool utilisation at 92%%%n", now.minusSeconds(280), serviceName));
        logs.append(String.format("[%s] ERROR %s - Timeout calling downstream InventoryService after 5000ms%n", now.minusSeconds(250), serviceName));
        logs.append(String.format("[%s] INFO  %s - Retry attempt 1/3 for transaction tx-98432%n", now.minusSeconds(240), serviceName));
        logs.append(String.format("[%s] ERROR %s - Transaction tx-98432 failed after 3 retries; rolling back%n", now.minusSeconds(200), serviceName));
        logs.append(String.format("[%s] WARN  %s - Circuit breaker for InventoryService moved to OPEN state%n", now.minusSeconds(180), serviceName));
        logs.append(String.format("[%s] INFO  %s - Alert notification sent to on-call engineer%n", now.minusSeconds(170), serviceName));

        logs.append(String.format("%n--- Summary: 3 ERROR, 2 WARN, 2 INFO entries in window ---%n"));
        return logs.toString();
    }

    /**
     * Retrieve the most recent error or warning log entries for a service.
     *
     * @param serviceName Name of the service.
     * @param limit       Maximum number of log lines to return.
     * @return Formatted recent log excerpt.
     */
    @Tool(description = "Get the most recent error and warning log entries for a service. "
            + "Useful for quick triage of ongoing incidents.")
    public String getRecentErrors(
            @ToolParam(description = "Name of the service to retrieve recent errors for")
            String serviceName,
            @ToolParam(description = "Maximum number of log lines to return (1-100)")
            int limit) {

        StringBuilder logs = new StringBuilder();
        logs.append(String.format("=== Recent Errors/Warnings: service=%s, limit=%d ===%n", serviceName, limit));
        Instant now = Instant.now();
        int count = Math.min(limit, 5);
        for (int i = 0; i < count; i++) {
            String level = i % 3 == 0 ? "ERROR" : "WARN";
            logs.append(String.format("[%s] %s %s - Sample log entry #%d%n",
                    now.minusSeconds((long) i * 60), level, serviceName, i + 1));
        }
        return logs.toString();
    }
}
