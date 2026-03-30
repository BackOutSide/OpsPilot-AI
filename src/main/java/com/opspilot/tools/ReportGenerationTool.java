package com.opspilot.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Agent tool for generating automated operations reports.
 *
 * <p>Produces structured report data (as text) that the LLM can summarise into a
 * human-readable narrative. In production, data would be fetched from monitoring
 * platforms, incident management systems, and CI/CD pipelines.
 */
@Component
public class ReportGenerationTool {

    /**
     * Collect raw metrics and incident data for a given period, ready for AI summarisation.
     *
     * @param reportType Type of report: {@code "daily"} or {@code "weekly"}.
     * @param fromTime   Period start in ISO-8601 format.
     * @param toTime     Period end in ISO-8601 format.
     * @return Structured raw data that can be summarised into a report.
     */
    @Tool(description = "Collect operations metrics and incident data for a reporting period. "
            + "Returns raw data that should be summarised into an executive-friendly narrative.")
    public String collectReportData(
            @ToolParam(description = "Report type: 'daily' or 'weekly'")
            String reportType,
            @ToolParam(description = "Report period start time in ISO-8601 format")
            String fromTime,
            @ToolParam(description = "Report period end time in ISO-8601 format")
            String toTime) {

        return String.format("""
                === Operations Report Data: %s | %s to %s ===

                INCIDENT SUMMARY
                ----------------
                Total incidents  : 12
                Critical (P1)    : 1  — payment-service DB pool exhausted (duration: 47 min)
                High     (P2)    : 3
                Medium   (P3)    : 6
                Low      (P4)    : 2
                MTTR             : 38 min (target: 30 min)
                SLO breaches     : 1 (payment-service availability: 99.6%% vs 99.9%% SLO)

                SYSTEM HEALTH
                -------------
                Overall availability : 99.87%%
                Services monitored   : 24
                Services degraded    : 1 (payment-service — recovered)
                Deployments          : 8  (7 successful, 1 rolled back)
                Change-failure rate  : 12.5%%

                TOP ERROR SERVICES (by error count)
                ------------------------------------
                1. payment-service  — 1,432 errors  (timeout + DB pool exhaustion)
                2. order-service    — 387 errors    (downstream payment timeout)
                3. auth-service     — 214 errors    (elevated 401 rate)

                CAPACITY & PERFORMANCE
                ----------------------
                Avg CPU utilisation  : 61%%
                Avg memory pressure  : 74%%
                P99 API latency      : 420 ms  (SLO: 500 ms ✓)
                Request throughput   : 2.4M req/period

                COST
                ----
                Cloud spend period   : $12,450
                vs prior period      : +3.2%% (within budget)
                """, reportType, fromTime, toTime);
    }

    /**
     * Generate a service-specific health summary.
     *
     * @param serviceName   Name of the service.
     * @param periodInHours Look-back window in hours.
     * @return Health summary for the requested service.
     */
    @Tool(description = "Generate a health summary for a specific service over a given look-back window.")
    public String getServiceHealthSummary(
            @ToolParam(description = "Name of the service to summarise")
            String serviceName,
            @ToolParam(description = "Look-back window in hours (e.g. 24 for the last day)")
            int periodInHours) {

        return String.format("""
                === Service Health Summary: %s | Last %d hours ===

                Availability     : 99.72%%
                Error rate       : 1.8%% (threshold: 1%%)  ⚠
                P50 latency      : 95 ms
                P95 latency      : 380 ms
                P99 latency      : 1,240 ms  ⚠ (SLO: 500 ms)
                Requests         : 450,000
                Successful       : 441,900  (98.2%%)
                Failed           : 8,100    (1.8%%)
                Restarts         : 3        (OOMKilled ×1, config-change ×2)
                Active incidents : 0        (all resolved)
                Last deployment  : %s       (successful)
                """, serviceName, periodInHours, Instant.now().minusSeconds(3600 * 6));
    }
}
