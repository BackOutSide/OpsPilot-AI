package com.opspilot.model;

import lombok.Builder;

/**
 * Request to generate an automated operations report.
 *
 * @param reportType Type of report to generate: {@code "daily"} or {@code "weekly"}.
 * @param fromTime   Start of the report period in ISO-8601 format.
 * @param toTime     End of the report period in ISO-8601 format.
 */
@Builder
public record ReportRequest(String reportType, String fromTime, String toTime) {}
