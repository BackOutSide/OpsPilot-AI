package org.example.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 轻量会话压缩服务
 * 保留最近几轮原始消息，将更早历史合并进滚动摘要
 * 以降低长对话场景下的上下文膨胀
 */
@Service
public class ConversationCompressionService {

    private static final int MAX_RECENT_MESSAGE_PAIRS = 4;
    private static final int MAX_SUMMARY_LENGTH = 1200;
    private static final int MAX_TURN_PREVIEW_LENGTH = 140;
    private static final String SUMMARY_SEPARATOR = "\n[历史摘要已滚动压缩]\n";

    public CompressionResult compress(List<Map<String, String>> messageHistory, String existingSummary) {
        if (messageHistory == null || messageHistory.isEmpty()) {
            return CompressionResult.noop(defaultSummary(existingSummary), List.of());
        }

        int maxRecentMessages = MAX_RECENT_MESSAGE_PAIRS * 2;
        if (messageHistory.size() <= maxRecentMessages) {
            return CompressionResult.noop(defaultSummary(existingSummary), new ArrayList<>(messageHistory));
        }

        int messagesToSummarize = messageHistory.size() - maxRecentMessages;
        if (messagesToSummarize % 2 != 0) {
            messagesToSummarize -= 1;
        }

        if (messagesToSummarize <= 0) {
            return CompressionResult.noop(defaultSummary(existingSummary), new ArrayList<>(messageHistory));
        }

        List<Map<String, String>> historyToSummarize = new ArrayList<>(messageHistory.subList(0, messagesToSummarize));
        List<Map<String, String>> recentMessages = new ArrayList<>(messageHistory.subList(messagesToSummarize, messageHistory.size()));

        String incrementalSummary = summarizeHistory(historyToSummarize);
        String mergedSummary = mergeSummary(existingSummary, incrementalSummary);

        return new CompressionResult(
                true,
                shrinkSummary(mergedSummary),
                recentMessages,
                messagesToSummarize / 2
        );
    }

    private String summarizeHistory(List<Map<String, String>> historyToSummarize) {
        StringBuilder builder = new StringBuilder("历史对话要点：");
        int turnIndex = 1;
        for (int i = 0; i < historyToSummarize.size(); i += 2) {
            Map<String, String> userMessage = historyToSummarize.get(i);
            Map<String, String> assistantMessage = i + 1 < historyToSummarize.size()
                    ? historyToSummarize.get(i + 1)
                    : Map.of("content", "");

            builder.append("\n- 第").append(turnIndex).append("轮：")
                    .append("用户关注 ")
                    .append(trimForSummary(userMessage.get("content")))
                    .append("；助手给出 ")
                    .append(trimForSummary(assistantMessage.get("content")));
            turnIndex++;
        }
        return builder.toString();
    }

    private String mergeSummary(String existingSummary, String incrementalSummary) {
        String safeExistingSummary = defaultSummary(existingSummary);
        if (safeExistingSummary.isBlank()) {
            return incrementalSummary;
        }
        return safeExistingSummary + SUMMARY_SEPARATOR + incrementalSummary;
    }

    private String shrinkSummary(String summary) {
        if (summary == null || summary.length() <= MAX_SUMMARY_LENGTH) {
            return defaultSummary(summary);
        }

        String marker = "\n...[更早历史已进一步压缩]...\n";
        int headLength = Math.min(360, (MAX_SUMMARY_LENGTH - marker.length()) / 2);
        int tailLength = Math.max(0, MAX_SUMMARY_LENGTH - marker.length() - headLength);
        return summary.substring(0, headLength) + marker + summary.substring(summary.length() - tailLength);
    }

    private String trimForSummary(String content) {
        if (content == null || content.isBlank()) {
            return "无内容";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_TURN_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TURN_PREVIEW_LENGTH) + "...";
    }

    private String defaultSummary(String summary) {
        return summary == null ? "" : summary;
    }

    public record CompressionResult(
            boolean compressed,
            String summary,
            List<Map<String, String>> recentMessages,
            int compressedPairs
    ) {
        public static CompressionResult noop(String summary, List<Map<String, String>> recentMessages) {
            return new CompressionResult(false, summary, recentMessages, 0);
        }
    }
}
