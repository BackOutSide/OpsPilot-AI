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

            List<ChunkEvalResult> results = new ArrayList<>();
            int hitAt1 = 0;
            int hitAt3 = 0;
            double mrr = 0.0d;

            for (ChunkEvalSample sample : samples) {
                ChunkEvalResult result = evaluateSample(sample, ragService);
                results.add(result);

                if (result.isHitAt1()) {
                    hitAt1++;
                }
                if (result.isHitAt3()) {
                    hitAt3++;
                }
                if (result.getFirstHitRank() > 0) {
                    mrr += 1.0d / result.getFirstHitRank();
                }
            }

            Files.createDirectories(resultsFile.getParent());
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(resultsFile.toFile(), results);

            double total = samples.size();
            System.out.println("========== Chunk Evaluation Summary ==========");
            System.out.printf(Locale.ROOT, "Samples: %d%n", samples.size());
            System.out.printf(Locale.ROOT, "Hit@1: %.4f%n", hitAt1 / total);
            System.out.printf(Locale.ROOT, "Hit@3: %.4f%n", hitAt3 / total);
            System.out.printf(Locale.ROOT, "MRR@3: %.4f%n", mrr / total);
            System.out.printf(Locale.ROOT, "Results file: %s%n", resultsFile);
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

    private static ChunkEvalResult evaluateSample(ChunkEvalSample sample, RagService ragService) {
        List<RetrievedChunk> retrievedChunks = ragService.retrieveForDebug(sample.getQuery());

        ChunkEvalResult result = new ChunkEvalResult();
        result.setSampleId(sample.getId());
        result.setQuery(sample.getQuery());
        result.setExpectedSource(sample.getExpectedSource());
        result.setExpectedChunkIndexes(sample.getExpectedChunkIndexes());
        result.setRetrievedChunkKeys(
                retrievedChunks.stream()
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

    private static class EvalArguments {
        private String samplesFile = "eval/chunk_eval_samples.jsonl";
        private String docsDir = "aiops-docs";
        private String chunkCatalogFile = "eval/chunk_catalog.json";
        private String resultsFile = "eval/chunk_eval_results.json";
        private boolean reindex = true;

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
                }
            });
            return parsed;
        }
    }
}
