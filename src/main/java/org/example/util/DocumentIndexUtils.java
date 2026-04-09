package org.example.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文档索引工具类
 * 统一路径标准化和分片 ID 生成逻辑，确保向量索引与 BM25 索引可以稳定对齐。
 */
public final class DocumentIndexUtils {

    private DocumentIndexUtils() {
    }

    public static String normalizeSourcePath(String filePath) {
        Path normalized = Paths.get(filePath).normalize();
        return normalized.toString().replace("\\", "/");
    }

    public static String buildChunkId(String normalizedSourcePath, int chunkIndex) {
        return UUID.nameUUIDFromBytes((normalizedSourcePath + "_" + chunkIndex).getBytes()).toString();
    }
}
