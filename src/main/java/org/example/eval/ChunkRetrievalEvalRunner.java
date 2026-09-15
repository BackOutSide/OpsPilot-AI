package org.example.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.Main;
import org.example.service.RagService;
import org.example.service.VectorIndexService;
import org.example.service.retrieval.RetrievedChunk;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chunk 级离线评测 Runner
 *
 * 使用方式：
 * mvn exec:java -Dexec.mainClass="org.example.eval.ChunkRetrievalEvalRunner" -Dexec.args="--samples=eval/chunk_eval_samples.jsonl --docs=aiops-docs --reindex=true"
 */
public class ChunkRetrievalEvalRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        EvalArguments evalArguments = EvalArguments.parse(args);

        ConfigurableApplicationContext context = new SpringApplicationBuilder(Main.class)
                .web(WebApplicationType.NONE)
                .properties(Map.of(
                        "spring.ai.mcp.client.enabled", "false",
                        "spring.autoconfigure.exclude",
                        "org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration",
                        "spring.main.banner-mode", "off"
                ))
                .run();

        try {
            RagService ragService = context.getBean(RagService.class);
            VectorIndexService vectorIndexService = context.getBean(VectorIndexService.class);
            ChunkCatalogExporter chunkCatalogExporter = context.getBean(ChunkCatalogExporter.class);

            Path docsDir = Paths.get(evalArguments.docsDir).normalize();
            Path samplesFile = Paths.get(evalArguments.samplesFile).normalize();
            Path catalogFile = Paths.get(evalArguments.chunkCatalogFile).normalize();
            Path resultsFile = Paths.get(evalArguments.resultsFile).normalize();

            int exported = chunkCatalogExporter.export(docsDir, catalogFile);
            System.out.printf(Locale.ROOT, "Exported chunk catalog: %d entries -> %s%n", exported, catalogFile);

            if (evalArguments.reindex) {
                VectorIndexService.IndexingResult indexingResult = vectorIndexService.indexDirectory(docsDir.toString());
                System.out.printf(Locale.ROOT,
                        "Indexed docs: total=%d success=%d fail=%d%n",
                        indexingResult.getTotalFiles(),
                        indexingResult.getSuccessCount(),
                        indexingResult.getFailCount());
            }

            List<ChunkEvalSample> samples = loadSamples(samplesFile);
            if (samples.isEmpty()) {
                throw new IllegalStateException("No evaluation samples found in " + samplesFile);
            }

            Files.createDirectories(resultsFile.getParent());
            List<EvalSummary> summaries = new ArrayList<>();
            for (RagService.RetrievalMode mode : evalArguments.modes()) {
                Path detailFile = detailFileFor(resultsFile, mode);
                summaries.add(evaluateMode(samples, ragService, mode, detailFile));
            }

            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(resultsFile.toFile(), summaries);

            printOverallTable(summaries);
            printCategoryTable(summaries);
            System.out.printf(Locale.ROOT, "Summary file: %s%n", resultsFile);
        } finally {
            context.close();
        }
    }

    private static List<ChunkEvalSample> loadSamples(Path samplesFile) throws IOException {
        if (!Files.exists(samplesFile)) {
            throw new IllegalArgumentException("Samples file does not exist: " + samplesFile);
        }

        List<ChunkEvalSample> samples = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(samplesFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                samples.add(OBJECT_MAPPER.readValue(trimmed, ChunkEvalSample.class));
            }
        }
        return samples;
    }

    private static EvalSummary evaluateMode(List<ChunkEvalSample> samples,
                                            RagService ragService,
                                            RagService.RetrievalMode mode,
                                            Path detailFile) throws IOException {
        List<ChunkEvalResult> results = new ArrayList<>();
        EvalSummary summary = new EvalSummary();
        summary.mode = mode.name();
        summary.samples = samples.size();

        for (ChunkEvalSample sample : samples) {
            ChunkEvalResult result = evaluateSample(sample, ragService, mode);
            results.add(result);

            if (result.isHitAt1()) {
                summary.hitAt1Count++;
            }
            if (result.isHitAt3()) {
                summary.hitAt3Count++;
                summary.firstHitRankSum += result.getFirstHitRank();
            }
            if (result.isRecallAt10()) {
                summary.recallAt10Count++;
            }
            if (result.getFirstHitRank() > 0) {
                summary.mrrAt3Sum += 1.0d / result.getFirstHitRank();
            }

            String category = categoryOf(sample.getId());
            CategorySummary categorySummary = summary.categoryHitAt3.computeIfAbsent(category, ignored -> new CategorySummary());
            categorySummary.samples++;
            if (result.isHitAt3()) {
                categorySummary.hitAt3Count++;
            }
        }

        summary.noHitCount = summary.samples - summary.hitAt3Count;
        summary.hitAt1 = ratio(summary.hitAt1Count, summary.samples);
        summary.hitAt3 = ratio(summary.hitAt3Count, summary.samples);
        summary.recallAt10 = ratio(summary.recallAt10Count, summary.samples);
        summary.mrrAt3 = summary.samples == 0 ? 0.0d : summary.mrrAt3Sum / summary.samples;
        summary.avgFirstHitRank = summary.hitAt3Count == 0 ? 0.0d : summary.firstHitRankSum / summary.hitAt3Count;
        summary.detailFile = detailFile.toString();
        summary.categoryHitAt3.values().forEach(CategorySummary::finish);

        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(detailFile.toFile(), results);
        return summary;
    }

    private static ChunkEvalResult evaluateSample(ChunkEvalSample sample,
                                                  RagService ragService,
                                                  RagService.RetrievalMode mode) {
        RagService.RetrievalDebugResult debugResult = ragService.retrieveForDebug(sample.getQuery(), mode);
        List<RetrievedChunk> retrievedChunks = debugResult.finalChunks();
        List<RetrievedChunk> recallChunks = debugResult.recallChunks();

        ChunkEvalResult result = new ChunkEvalResult();
        result.setMode(mode.name());
        result.setSampleId(sample.getId());
        result.setQuery(sample.getQuery());
        result.setExpectedSource(sample.getExpectedSource());
        result.setExpectedChunkIndexes(sample.getExpectedChunkIndexes());
        result.setRetrievedChunkKeys(
                retrievedChunks.stream()
                        .map(ChunkRetrievalEvalRunner::chunkKey)
                        .collect(Collectors.toList())
        );
        result.setRecallChunkKeys(
                recallChunks.stream()
                        .map(ChunkRetrievalEvalRunner::chunkKey)
                        .collect(Collectors.toList())
        );

        int firstHitRank = -1;
        for (int i = 0; i < retrievedChunks.size(); i++) {
            RetrievedChunk chunk = retrievedChunks.get(i);
            if (isExpectedChunk(chunk, sample)) {
                firstHitRank = i + 1;
                break;
            }
        }

        result.setFirstHitRank(firstHitRank);
        result.setHitAt1(firstHitRank == 1);
        result.setHitAt3(firstHitRank > 0 && firstHitRank <= 3);
        result.setRecallAt10(recallChunks.stream().anyMatch(chunk -> isExpectedChunk(chunk, sample)));
        return result;
    }

    private static boolean isExpectedChunk(RetrievedChunk chunk, ChunkEvalSample sample) {
        String actualSource = fileNameOnly(chunk.getSource());
        if (!actualSource.equals(sample.getExpectedSource())) {
            return false;
        }
        return sample.getExpectedChunkIndexes() != null && sample.getExpectedChunkIndexes().contains(chunk.getChunkIndex());
    }

    private static String chunkKey(RetrievedChunk chunk) {
        return fileNameOnly(chunk.getSource()) + "#" + chunk.getChunkIndex();
    }

    private static String fileNameOnly(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        Path path = Paths.get(source);
        Path fileName = path.getFileName();
        return fileName == null ? source : fileName.toString();
    }

    private static Path detailFileFor(Path resultsFile, RagService.RetrievalMode mode) {
        String fileName = "chunk_eval_results_" + mode.name().toLowerCase(Locale.ROOT) + ".json";
        Path parent = resultsFile.getParent();
        return parent == null ? Paths.get(fileName) : parent.resolve(fileName);
    }

    private static String categoryOf(String sampleId) {
        if (sampleId == null) {
            return "unknown";
        }
        int dashIndex = sampleId.indexOf('-');
        return dashIndex <= 0 ? "unknown" : sampleId.substring(0, dashIndex);
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0d : (double) numerator / denominator;
    }

    private static void printOverallTable(List<EvalSummary> summaries) {
        System.out.println("========== Retrieval Ablation Summary ==========");
        System.out.println("| Mode | Samples | Hit@1 | Hit@3 | Recall@10 | MRR@3 | AvgFirstHitRank | NoHit |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|");
        for (EvalSummary summary : summaries) {
            System.out.printf(Locale.ROOT,
                    "| %s | %d | %.4f | %.4f | %.4f | %.4f | %.2f | %d |%n",
                    summary.mode,
                    summary.samples,
                    summary.hitAt1,
                    summary.hitAt3,
                    summary.recallAt10,
                    summary.mrrAt3,
                    summary.avgFirstHitRank,
                    summary.noHitCount);
        }
    }

    private static void printCategoryTable(List<EvalSummary> summaries) {
        System.out.println("========== Category Hit@3 ==========");
        System.out.println("| Mode | cpu | disk | memory | svc | slow |");
        System.out.println("|---|---:|---:|---:|---:|---:|");
        for (EvalSummary summary : summaries) {
            System.out.printf(Locale.ROOT,
                    "| %s | %.4f | %.4f | %.4f | %.4f | %.4f |%n",
                    summary.mode,
                    categoryHit(summary, "cpu"),
                    categoryHit(summary, "disk"),
                    categoryHit(summary, "memory"),
                    categoryHit(summary, "svc"),
                    categoryHit(summary, "slow"));
        }
    }

    private static double categoryHit(EvalSummary summary, String category) {
        CategorySummary categorySummary = summary.categoryHitAt3.get(category);
        return categorySummary == null ? 0.0d : categorySummary.hitAt3;
    }

    public static class EvalSummary {
        public String mode;
        public int samples;
        public int hitAt1Count;
        public int hitAt3Count;
        public int recallAt10Count;
        public int noHitCount;
        public double hitAt1;
        public double hitAt3;
        public double recallAt10;
        public double mrrAt3;
        public double avgFirstHitRank;
        public String detailFile;
        public Map<String, CategorySummary> categoryHitAt3 = new LinkedHashMap<>();

        private double mrrAt3Sum;
        private double firstHitRankSum;
    }

    public static class CategorySummary {
        public int samples;
        public int hitAt3Count;
        public double hitAt3;

        private void finish() {
            hitAt3 = ratio(hitAt3Count, samples);
        }
    }

    private static class EvalArguments {
        private String samplesFile = "eval/chunk_eval_samples.jsonl";
        private String docsDir = "aiops-docs";
        private String chunkCatalogFile = "eval/chunk_catalog.json";
        private String resultsFile = "eval/chunk_eval_ablation_summary.json";
        private boolean reindex = true;
        private String mode = "ALL";

        static EvalArguments parse(String[] args) {
            EvalArguments parsed = new EvalArguments();
            Arrays.stream(args).forEach(arg -> {
                if (arg.startsWith("--samples=")) {
                    parsed.samplesFile = arg.substring("--samples=".length());
                } else if (arg.startsWith("--docs=")) {
                    parsed.docsDir = arg.substring("--docs=".length());
                } else if (arg.startsWith("--catalog=")) {
                    parsed.chunkCatalogFile = arg.substring("--catalog=".length());
                } else if (arg.startsWith("--results=")) {
                    parsed.resultsFile = arg.substring("--results=".length());
                } else if (arg.startsWith("--reindex=")) {
                    parsed.reindex = Boolean.parseBoolean(arg.substring("--reindex=".length()));
                } else if (arg.startsWith("--mode=")) {
                    parsed.mode = arg.substring("--mode=".length());
                }
            });
            return parsed;
        }

        List<RagService.RetrievalMode> modes() {
            if ("ALL".equalsIgnoreCase(mode)) {
                return Arrays.asList(RagService.RetrievalMode.values());
            }
            return List.of(RagService.RetrievalMode.valueOf(mode));
        }
    }
}
