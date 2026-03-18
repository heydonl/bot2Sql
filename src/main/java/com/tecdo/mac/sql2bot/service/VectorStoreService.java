package com.tecdo.mac.sql2bot.service;

import com.google.gson.Gson;
import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.Model;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis 向量存储服务
 * 用于存储和检索表/字段的向量表示
 *
 * @deprecated 已废弃，请使用 SchemaVectorStoreService
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EmbeddingService embeddingService;
    private final Gson gson = new Gson();

    private static final String MODEL_VECTOR_PREFIX = "vector:model:";
    private static final String COLUMN_VECTOR_PREFIX = "vector:column:";
    private static final String MODEL_META_PREFIX = "meta:model:";
    private static final String COLUMN_META_PREFIX = "meta:column:";

    /**
     * 为表生成并存储向量
     */
    public void indexModel(Model model) {
        try {
            // 构建表的文本描述
            StringBuilder textBuilder = new StringBuilder();
            textBuilder.append("表名: ").append(model.getTableName());

            if (model.getDisplayName() != null && !model.getDisplayName().isEmpty()) {
                textBuilder.append(", 显示名: ").append(model.getDisplayName());
            }

            if (model.getDescription() != null && !model.getDescription().isEmpty()) {
                textBuilder.append(", 描述: ").append(model.getDescription());
            }

            String text = textBuilder.toString();
            log.debug("Indexing model: {}", text);

            // 生成 embedding
            float[] embedding = embeddingService.generateEmbedding(text);

            // 存储向量
            String vectorKey = MODEL_VECTOR_PREFIX + model.getId();
            redisTemplate.opsForValue().set(vectorKey, serializeVector(embedding));

            // 存储元数据
            String metaKey = MODEL_META_PREFIX + model.getId();
            ModelMeta meta = new ModelMeta(
                model.getId(),
                model.getTableName(),
                model.getDisplayName(),
                model.getDescription(),
                model.getDatasourceId()
            );
            redisTemplate.opsForValue().set(metaKey, gson.toJson(meta));

            log.info("Indexed model: id={}, tableName={}", model.getId(), model.getTableName());

        } catch (Exception e) {
            log.error("Failed to index model: {}", model.getId(), e);
        }
    }

    /**
     * 为字段生成并存储向量
     */
    public void indexColumn(ColumnDefinition column) {
        try {
            // 构建字段的文本描述
            StringBuilder textBuilder = new StringBuilder();
            textBuilder.append("字段名: ").append(column.getColumnName());

            if (column.getDisplayName() != null && !column.getDisplayName().isEmpty()) {
                textBuilder.append(", 显示名: ").append(column.getDisplayName());
            }

            if (column.getDescription() != null && !column.getDescription().isEmpty()) {
                textBuilder.append(", 描述: ").append(column.getDescription());
            }

            if (column.getColumnType() != null) {
                textBuilder.append(", 类型: ").append(column.getColumnType());
            }

            String text = textBuilder.toString();
            log.debug("Indexing column: {}", text);

            // 生成 embedding
            float[] embedding = embeddingService.generateEmbedding(text);

            // 存储向量
            String vectorKey = COLUMN_VECTOR_PREFIX + column.getId();
            redisTemplate.opsForValue().set(vectorKey, serializeVector(embedding));

            // 存储元数据
            String metaKey = COLUMN_META_PREFIX + column.getId();
            ColumnMeta meta = new ColumnMeta(
                column.getId(),
                column.getModelId(),
                column.getColumnName(),
                column.getDisplayName(),
                column.getDescription(),
                column.getColumnType()
            );
            redisTemplate.opsForValue().set(metaKey, gson.toJson(meta));

            log.debug("Indexed column: id={}, columnName={}", column.getId(), column.getColumnName());

        } catch (Exception e) {
            log.error("Failed to index column: {}", column.getId(), e);
        }
    }

    /**
     * 搜索相关的表
     */
    public List<ModelSearchResult> searchModels(String query, Long datasourceId, int topK) {
        try {
            log.info("Searching models for query: {}, datasourceId: {}, topK: {}", query, datasourceId, topK);

            // 生成查询向量
            float[] queryVector = embeddingService.generateEmbedding(query);

            // 获取所有表的向量
            Set<String> vectorKeys = redisTemplate.keys(MODEL_VECTOR_PREFIX + "*");
            if (vectorKeys == null || vectorKeys.isEmpty()) {
                log.warn("No model vectors found in Redis");
                return Collections.emptyList();
            }

            List<ModelSearchResult> results = new ArrayList<>();

            for (String vectorKey : vectorKeys) {
                String modelId = vectorKey.substring(MODEL_VECTOR_PREFIX.length());
                String metaKey = MODEL_META_PREFIX + modelId;

                // 获取向量和元数据
                String vectorStr = (String) redisTemplate.opsForValue().get(vectorKey);
                String metaStr = (String) redisTemplate.opsForValue().get(metaKey);

                if (vectorStr == null || metaStr == null) {
                    continue;
                }

                float[] vector = deserializeVector(vectorStr);
                ModelMeta meta = gson.fromJson(metaStr, ModelMeta.class);

                // 过滤数据源
                if (datasourceId != null && !datasourceId.equals(meta.getDatasourceId())) {
                    continue;
                }

                // 计算相似度
                double similarity = embeddingService.cosineSimilarity(queryVector, vector);
                results.add(new ModelSearchResult(meta, similarity));
            }

            // 按相似度排序并返回 Top-K
            List<ModelSearchResult> topResults = results.stream()
                    .sorted(Comparator.comparingDouble(ModelSearchResult::getSimilarity).reversed())
                    .limit(topK)
                    .collect(Collectors.toList());

            log.info("Found {} relevant models", topResults.size());
            return topResults;

        } catch (Exception e) {
            log.error("Failed to search models", e);
            return Collections.emptyList();
        }
    }

    /**
     * 搜索相关的字段
     */
    public List<ColumnSearchResult> searchColumns(String query, List<Long> modelIds, int topK) {
        try {
            log.info("Searching columns for query: {}, modelIds: {}, topK: {}", query, modelIds, topK);

            // 生成查询向量
            float[] queryVector = embeddingService.generateEmbedding(query);

            // 获取所有字段的向量
            Set<String> vectorKeys = redisTemplate.keys(COLUMN_VECTOR_PREFIX + "*");
            if (vectorKeys == null || vectorKeys.isEmpty()) {
                log.warn("No column vectors found in Redis");
                return Collections.emptyList();
            }

            List<ColumnSearchResult> results = new ArrayList<>();

            for (String vectorKey : vectorKeys) {
                String columnId = vectorKey.substring(COLUMN_VECTOR_PREFIX.length());
                String metaKey = COLUMN_META_PREFIX + columnId;

                // 获取向量和元数据
                String vectorStr = (String) redisTemplate.opsForValue().get(vectorKey);
                String metaStr = (String) redisTemplate.opsForValue().get(metaKey);

                if (vectorStr == null || metaStr == null) {
                    continue;
                }

                float[] vector = deserializeVector(vectorStr);
                ColumnMeta meta = gson.fromJson(metaStr, ColumnMeta.class);

                // 过滤模型
                if (modelIds != null && !modelIds.isEmpty() && !modelIds.contains(meta.getModelId())) {
                    continue;
                }

                // 计算相似度
                double similarity = embeddingService.cosineSimilarity(queryVector, vector);
                results.add(new ColumnSearchResult(meta, similarity));
            }

            // 按相似度排序并返回 Top-K
            List<ColumnSearchResult> topResults = results.stream()
                    .sorted(Comparator.comparingDouble(ColumnSearchResult::getSimilarity).reversed())
                    .limit(topK)
                    .collect(Collectors.toList());

            log.info("Found {} relevant columns", topResults.size());
            return topResults;

        } catch (Exception e) {
            log.error("Failed to search columns", e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除表的向量
     */
    public void deleteModel(Long modelId) {
        redisTemplate.delete(MODEL_VECTOR_PREFIX + modelId);
        redisTemplate.delete(MODEL_META_PREFIX + modelId);
        log.info("Deleted model vector: {}", modelId);
    }

    /**
     * 删除字段的向量
     */
    public void deleteColumn(Long columnId) {
        redisTemplate.delete(COLUMN_VECTOR_PREFIX + columnId);
        redisTemplate.delete(COLUMN_META_PREFIX + columnId);
        log.debug("Deleted column vector: {}", columnId);
    }

    /**
     * 清空所有向量
     */
    public void clearAll() {
        Set<String> keys = redisTemplate.keys("vector:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        keys = redisTemplate.keys("meta:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("Cleared all vectors from Redis");
    }

    /**
     * 序列化向量
     */
    private String serializeVector(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    /**
     * 反序列化向量
     */
    private float[] deserializeVector(String vectorStr) {
        String[] parts = vectorStr.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

    // 元数据类
    @Data
    public static class ModelMeta {
        private Long id;
        private String tableName;
        private String displayName;
        private String description;
        private Long datasourceId;

        public ModelMeta(Long id, String tableName, String displayName, String description, Long datasourceId) {
            this.id = id;
            this.tableName = tableName;
            this.displayName = displayName;
            this.description = description;
            this.datasourceId = datasourceId;
        }
    }

    @Data
    public static class ColumnMeta {
        private Long id;
        private Long modelId;
        private String columnName;
        private String displayName;
        private String description;
        private String columnType;

        public ColumnMeta(Long id, Long modelId, String columnName, String displayName,
                         String description, String columnType) {
            this.id = id;
            this.modelId = modelId;
            this.columnName = columnName;
            this.displayName = displayName;
            this.description = description;
            this.columnType = columnType;
        }
    }

    @Data
    public static class ModelSearchResult {
        private ModelMeta model;
        private double similarity;

        public ModelSearchResult(ModelMeta model, double similarity) {
            this.model = model;
            this.similarity = similarity;
        }
    }

    @Data
    public static class ColumnSearchResult {
        private ColumnMeta column;
        private double similarity;

        public ColumnSearchResult(ColumnMeta column, double similarity) {
            this.column = column;
            this.similarity = similarity;
        }
    }
}
