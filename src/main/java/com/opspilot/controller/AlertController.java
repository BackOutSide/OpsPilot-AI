package com.opspilot.controller;

import com.opspilot.model.AlertInfo;
import com.opspilot.model.ReportRequest;
import com.opspilot.service.AlertAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * REST controller for AIOps automation — alert analysis, log investigation,
 * and automated operations reporting.
 *
 * <p>All responses are streamed as Server-Sent Events so that the UI can display
 * the AI's analysis incrementally as it is generated.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/ops/alerts/analyze} — AI-powered alert root-cause analysis</li>
 *   <li>{@code GET  /api/ops/logs/investigate} — log investigation for a service</li>
 *   <li>{@code POST /api/ops/reports/generate} — automated operations report generation</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class AlertController {

    private final AlertAnalysisService alertAnalysisService;

    /**
     * Submit an alert for AI-driven root-cause analysis.
     *
     * <p>The LLM autonomously calls log-query and alert-history tools to gather
     * evidence before producing its analysis, remediation steps, and prevention
     * recommendations.
     *
     * <p>Example request body:
     * <pre>{@code
     * {
     *   "alertId"  : "ALT-20240115-001",
     *   "service"  : "payment-service",
     *   "severity" : "CRITICAL",
     *   "message"  : "Database connection pool exhausted — all 50 connections in use",
     *   "timestamp": "2024-01-15T08:42:00Z"
     * }
     * }</pre>
     *
     * @param alert the alert to analyse
     * @return streamed analysis content
     */
    @PostMapping(
            value = "/alerts/analyze",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyzeAlert(@RequestBody AlertInfo alert) {
        return alertAnalysisService.analyzeAlert(alert);
    }

    /**
     * Investigate application logs for a given service and time window.
     *
     * <p>The agent calls the log-query tool, analyses the result, and returns a
     * structured incident investigation report.
     *
     * @param serviceName name of the service to investigate
     * @param startTime   investigation window start in ISO-8601 format
     * @param endTime     investigation window end in ISO-8601 format
     * @return streamed log investigation report
     */
    @GetMapping(
            value = "/logs/investigate",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> investigateLogs(
            @RequestParam String serviceName,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        return alertAnalysisService.investigateLogs(serviceName, startTime, endTime);
    }

    /**
     * Generate an automated operations report for a given period.
     *
     * <p>The LLM uses the report-generation tool to collect metrics, then produces a
     * structured, executive-friendly narrative report.
     *
     * <p>Example request body:
     * <pre>{@code
     * {
     *   "reportType": "daily",
     *   "fromTime"  : "2024-01-15T00:00:00Z",
     *   "toTime"    : "2024-01-15T23:59:59Z"
     * }
     * }</pre>
     *
     * @param request report configuration
     * @return streamed report content
     */
    @PostMapping(
            value = "/reports/generate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateReport(@RequestBody ReportRequest request) {
        return alertAnalysisService.generateReport(request);
    }
}
