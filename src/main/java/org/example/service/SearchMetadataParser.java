package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * 检索元数据解析工具
 * 将 Milvus metadata 的 JSON 字符串解析为结构化字段，便于召回融合和精排使用。
 */
public final class SearchMetadataParser {

    private static final Logger logger = LoggerFactory.getLogger(SearchMetadataParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SearchMetadataParser() {
    }

    public static Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return OBJECT_MAPPER.readValue(metadata, new TypeReference<>() {});
        } catch (Exception e) {
            logger.debug("解析 metadata 失败，返回空对象: {}", metadata, e);
            return Collections.emptyMap();
        }
    }
}
