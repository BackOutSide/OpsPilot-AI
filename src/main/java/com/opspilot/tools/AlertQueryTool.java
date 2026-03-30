package com.opspilot.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Agent tool for querying alert history and correlated incident data.
 *
 * <p>Returns synthetic but realistic alert data for end-to-end agent testing.
 * In production this would integrate with Alertmanager, PagerDuty, or a similar platform.
 */
@Component
public class AlertQueryTool {

    /**
     * Retrieve historical alerts for a service within a time range.
     *
     * @param serviceName Name of the service.
     * @param startTime   Range start in ISO-8601 format.
     * @param endTime     Range end in ISO-8601 format.
     * @param severity    Optional filter: {@code CRITICAL}, {@code HIGH}, {@code MEDIUM},
     *                    {@code LOW}, or {@code ALL}.
     * @return Formatted list of matching historical alerts.
     */
    @Tool(description = "Retrieve historical alerts for a service within a specific time range. "
            + "Useful for identifying recurring patterns and related incidents.")
    public String getAlertHistory(
            @ToolParam(description = "Service name to query alerts for")
            String serviceName,
            @ToolParam(description = "Start of the query window in ISO-8601 format")
            String startTime,
            @ToolParam(description = "End of the query window in ISO-8601 format")
            String endTime,
            @ToolParam(description = "Severity filter: CRITICAL, HIGH, MEDIUM, LOW, or ALL")
            String severity) {

        Instant now = Instant.now();
        StringBuilder result = new StringBuilder();
        result.append(String.format("=== Alert History: service=%s, severity=%s ===%n", serviceName, severity));
        result.append(String.format("[%s] CRITICAL %s - Pod crash-looping: OOMKilled (3 times in 10 min)%n", now.minusSeconds(600), serviceName));
        result.append(String.format("[%s] HIGH     %s - P95 latency exceeded SLO: 2300ms > 500ms threshold%n", now.minusSeconds(500), serviceName));
        result.append(String.format("[%s] HIGH     %s - Error rate spike: 8.4%% > 1%% threshold%n", now.minusSeconds(350), serviceName));
        result.append(String.format("[%s] MEDIUM   %s - CPU throttling detected on 2/3 replicas%n", now.minusSeconds(200), serviceName));
        result.append(String.format("%nTotal: 4 alerts (1 CRITICAL, 2 HIGH, 1 MEDIUM) in window%n"));
        return result.toString();
    }

    /**
     * Get active (unresolved) alerts across all services or for a specific one.
     *
     * @param serviceName Service name filter, or {@code "ALL"} for all services.
     * @return Summary of currently active alerts.
     */
    @Tool(description = "Get currently active (unresolved) alerts. "
            + "Pass 'ALL' as serviceName to see alerts across all services.")
    public String getActiveAlerts(
            @ToolParam(description = "Service name to filter alerts, or 'ALL' for all services")
            String serviceName) {

        Instant now = Instant.now();
        boolean showAll = "ALL".equalsIgnoreCase(serviceName);
        StringBuilder result = new StringBuilder();
        result.append(String.format("=== Active Alerts [%s] ===%n", showAll ? "ALL SERVICES" : serviceName));
        result.append(String.format("[%s] CRITICAL payment-service - Database connection pool exhausted%n", now.minusSeconds(120)));
        if (showAll) {
            result.append(String.format("[%s] HIGH     order-service   - Downstream timeout from inventory-service%n", now.minusSeconds(90)));
            result.append(String.format("[%s] MEDIUM   auth-service    - Elevated 401 rate: 3.1%% of requests%n", now.minusSeconds(45)));
        }
        result.append(String.format("%nTotal active: %d alert(s)%n", showAll ? 3 : 1));
        return result.toString();
    }

    /**
     * Retrieve correlated alerts that occurred around the same time as a given alert.
     *
     * @param alertId         The reference alert ID.
     * @param correlationWindow Time window in minutes for correlation search.
     * @return List of potentially correlated alerts.
     */
    @Tool(description = "Find alerts correlated with a specific alert ID — alerts that fired "
            + "within the same time window and may share a root cause.")
    public String getCorrelatedAlerts(
            @ToolParam(description = "The reference alert ID to find correlations for")
            String alertId,
            @ToolParam(description = "Time window in minutes around the reference alert to search (e.g. 15)")
            int correlationWindow) {

        Instant now = Instant.now();
        StringBuilder result = new StringBuilder();
        result.append(String.format("=== Correlated Alerts: alertId=%s, window=±%dmin ===%n", alertId, correlationWindow));
        result.append(String.format("[%s] CRITICAL payment-service  - DB pool exhausted (reference alert)%n", now.minusSeconds(300)));
        result.append(String.format("[%s] HIGH     order-service    - Timeout calling payment-service%n", now.minusSeconds(290)));
        result.append(String.format("[%s] HIGH     checkout-service - Payment callback failed%n", now.minusSeconds(280)));
        result.append(String.format("[%s] MEDIUM   metrics-service  - Anomaly detected in transaction throughput%n", now.minusSeconds(260)));
        result.append(String.format("%nCorrelation analysis suggests payment-service DB issue is the root cause.%n"));
        return result.toString();
    }
}
