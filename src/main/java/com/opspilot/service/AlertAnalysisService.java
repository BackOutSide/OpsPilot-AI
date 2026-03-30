package com.opspilot.service;

import com.opspilot.model.AlertInfo;
import com.opspilot.model.ReportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Service for AIOps automation: alert analysis and operations reporting.
 *
 * <p>Uses the {@code opsAgentChatClient} that has log-query, alert-query, and
 * report-generation tools registered, allowing the LLM to autonomously call them
 * via function/tool calling as it formulates its response.
 */
@Service
public class AlertAnalysisService {

    private final ChatClient opsAgentChatClient;

    public AlertAnalysisService(
            @Qualifier("opsAgentChatClient") ChatClient opsAgentChatClient) {
        this.opsAgentChatClient = opsAgentChatClient;
    }

    /**
     * Perform an AI-driven root-cause analysis of an operational alert.
     *
     * <p>The LLM is instructed to call the available log and alert tools as needed
     * in order to correlate evidence before presenting its findings.
     *
     * @param alert the alert to analyse
     * @return streaming analysis token-by-token so the caller can stream back to the client
     */
    public Flux<String> analyzeAlert(AlertInfo alert) {
        String prompt = String.format("""
                Analyse the following operational alert and provide:
                1. A concise root-cause analysis (2-3 sentences)
                2. Immediate remediation steps (numbered list)
                3. Preventive measures to avoid recurrence

                Alert details:
                - Alert ID : %s
                - Service  : %s
                - Severity : %s
                - Message  : %s
                - Time     : %s

                Use the available tools to query recent logs and correlated alerts \
                for the service before drawing conclusions.
                """,
                alert.alertId(),
                alert.service(),
                alert.severity(),
                alert.message(),
                alert.timestamp());

        return opsAgentChatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    /**
     * Generate an automated operations report for the requested period.
     *
     * <p>The LLM collects metrics via the report-generation tool and then writes a
     * structured, executive-friendly report.
     *
     * @param request report parameters (type and time range)
     * @return streaming report content
     */
    public Flux<String> generateReport(ReportRequest request) {
        String prompt = String.format("""
                Generate a %s operations report for the period from %s to %s.

                Steps:
                1. Use the collectReportData tool to gather raw metrics for the period.
                2. Summarise the data into a well-structured report with the following sections:
                   - Executive Summary (3-4 sentences)
                   - Incident Highlights (top issues with impact and resolution)
                   - System Health Overview (availability, performance, capacity)
                   - Deployment Activity
                   - Key Action Items for next period

                Write for a technical manager audience. Be concise but thorough.
                """,
                request.reportType(),
                request.fromTime(),
                request.toTime());

        return opsAgentChatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    /**
     * Investigate application logs for a service and provide an AI-written summary.
     *
     * @param serviceName name of the service to investigate
     * @param startTime   investigation start time (ISO-8601)
     * @param endTime     investigation end time (ISO-8601)
     * @return streaming investigation report
     */
    public Flux<String> investigateLogs(String serviceName, String startTime, String endTime) {
        String prompt = String.format("""
                Investigate the logs for service '%s' between %s and %s.

                1. Use the queryLogs tool to fetch relevant log entries.
                2. Identify any errors, exceptions, or anomalies.
                3. Determine the most likely root cause.
                4. Provide specific remediation recommendations.
                """, serviceName, startTime, endTime);

        return opsAgentChatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }
}
