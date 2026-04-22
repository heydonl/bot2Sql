package com.tecdo.mac.sql2bot.service;

import com.google.gson.Gson;
import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.Model;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Schema 向量存储服务
 * 使用 RedisStack 原生 KNN 向量搜索管理表结构索引
 */
@Slf4j
@Service
public class SchemaVectorStoreService {

    private static final String INDEX_NAME = "idx:schema";
    private static final String KEY_PREFIX = "schema:";
    private static final String VECTOR_FIELD = "vector";
    private static final String TABLE_NAME_FIELD = "table_name";
    private static final String DATASOURCE_ID_FIELD = "datasource_id";
    private static final String META_FIELD = "meta";

    private final JedisPooled jedisPooled;
    private final EmbeddingService embeddingService;
    private final Gson gson;

    @Value("${embedding.dimension:1024}")
    private int dimension;

    public SchemaVectorStoreService(JedisPooled jedisPooled, EmbeddingService embeddingService) {
        this.jedisPooled = jedisPooled;
        this.embeddingService = embeddingService;
        this.gson = new Gson();
    }

    /**
     * 初始化 RedisStack HNSW 索引
     */
    @PostConstruct
    public void initIndex() {
        // 初始化 schema 索引
        try {
            Map<String, Object> info = jedisPooled.ftInfo(INDEX_NAME);
            long existingDim = extractDimFromInfo(info);
            if (existingDim > 0 && existingDim != dimension) {
                log.warn("RedisStack 索引 {} 维度不匹配（现有: {}, 配置: {}），删除重建", INDEX_NAME, existingDim, dimension);
                jedisPooled.ftDropIndex(INDEX_NAME);
                createIndex();
            } else {
                log.info("RedisStack 索引 {} 已存在，维度: {}", INDEX_NAME, existingDim > 0 ? existingDim : "unknown");
            }
        } catch (Exception e) {
            log.info("RedisStack 索引 {} 不存在，开始创建", INDEX_NAME);
            createIndex();
        }
    }

    private long extractDimFromInfo(Map<String, Object> info) {
        try {
            // ftInfo 返回的 attributes 列表中找 vector 字段的 DIM
            Object attrs = info.get("attributes");
            if (attrs instanceof List) {
                for (Object attr : (List<?>) attrs) {
                    if (attr instanceof List) {
                        List<?> attrList = (List<?>) attr;
                        String attrStr = attrList.toString().toLowerCase();
                        if (attrStr.contains("vector") && attrStr.contains("dim")) {
                            int dimIdx = attrList.indexOf("DIM");
                            if (dimIdx == -1) dimIdx = attrList.indexOf("dim");
                            if (dimIdx >= 0 && dimIdx + 1 < attrList.size()) {
                                return Long.parseLong(attrList.get(dimIdx + 1).toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析索引维度失败: {}", e.getMessage());
        }
        return -1;
    }

    private void createIndex() {
        try {
            Map<String, Object> vectorAttrs = new HashMap<>();
            vectorAttrs.put("TYPE", "FLOAT32");
            vectorAttrs.put("DIM", dimension);
            vectorAttrs.put("DISTANCE_METRIC", "COSINE");

            jedisPooled.ftCreate(INDEX_NAME,
                    FTCreateParams.createParams()
                            .on(IndexDataType.HASH)
                            .addPrefix(KEY_PREFIX),
                    TextField.of(TABLE_NAME_FIELD),
                    NumericField.of(DATASOURCE_ID_FIELD),
                    TextField.of(META_FIELD).noIndex(),
                    VectorField.builder()
                            .fieldName(VECTOR_FIELD)
                            .algorithm(VectorField.VectorAlgorithm.HNSW)
                            .attributes(vectorAttrs)
                            .build()
            );
            log.info("RedisStack 索引 {} 创建成功，向量维度: {}", INDEX_NAME, dimension);
        } catch (Exception ex) {
            log.error("创建 RedisStack 索引失败: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 索引表模型
     */
    public void indexModel(Model model, List<ColumnDefinition> columns, String datasourceName) {
        try {
            // 构建用于 embedding 的文本（仅使用描述）
            String embeddingText = model.getDescription() != null ? model.getDescription() : "";

            // 生成 embedding
            float[] embedding = embeddingService.generateEmbedding(embeddingText);

            // 构建 SchemaMeta
            SchemaMeta meta = buildSchemaMeta(model, columns, datasourceName);

            // 存储到 RedisStack（向量用 bytes 存储）
            String key = KEY_PREFIX + model.getId();
            byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Map<byte[], byte[]> hash = new HashMap<>();
            hash.put(TABLE_NAME_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    model.getTableName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(DATASOURCE_ID_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    String.valueOf(model.getDatasourceId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(META_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    gson.toJson(meta).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(VECTOR_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    toFloat32Bytes(embedding));

            jedisPooled.hset(keyBytes, hash);
            log.info("已索引表模型: {} (id={})", model.getTableName(), model.getId());
        } catch (Exception e) {
            log.error("索引表模型失败: modelId={}, error={}", model.getId(), e.getMessage(), e);
            throw new RuntimeException("索引表模型失败", e);
        }
    }

    /**
     * KNN 向量搜索，返回相关表结构列表
     */
    public List<SchemaSearchResult> searchSchemas(String query, Long datasourceId, int topK) {
        log.info("开始 KNN 搜索: query={}, datasourceId={}, topK={}", query, datasourceId, topK);
        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            log.debug("Embedding 生成完成，维度: {}", queryEmbedding.length);
            byte[] queryVector = toFloat32Bytes(queryEmbedding);

            String queryStr;
            if (datasourceId != null) {
                queryStr = String.format("@%s:[%d %d]=>[KNN %d @%s $BLOB AS score]",
                        DATASOURCE_ID_FIELD, datasourceId, datasourceId, topK, VECTOR_FIELD);
            } else {
                queryStr = String.format("*=>[KNN %d @%s $BLOB AS score]", topK, VECTOR_FIELD);
            }
            log.debug("KNN 查询语句: {}", queryStr);

            Query q = new Query(queryStr)
                    .addParam("BLOB", queryVector)
                    .returnFields(META_FIELD, "score")
                    .setSortBy("score", true)  // true = 升序排序，让cosine distance小的（相似度高的）排在前面
                    .dialect(2);

            SearchResult result = jedisPooled.ftSearch(INDEX_NAME, q);
            log.info("KNN 搜索命中 {} 条结果", result.getDocuments().size());

            List<SchemaSearchResult> results = result.getDocuments().stream()
                    .map(doc -> {
                        String metaJson = (String) doc.get(META_FIELD);
                        SchemaMeta meta = gson.fromJson(metaJson, SchemaMeta.class);
                        double similarity = 0.0;
                        Object scoreObj = doc.get("score");
                        if (scoreObj != null) {
                            try {
                                double cosineDistance = Double.parseDouble(scoreObj.toString());
                                similarity = 1.0 - cosineDistance / 2.0;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        log.debug("命中表: tableName={}, similarity={}", meta != null ? meta.getTableName() : "null", similarity);
                        return new SchemaSearchResult(meta, similarity);
                    })
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore())) // 按相似度分数倒序排序
                    .collect(Collectors.toList());

            if (results.isEmpty()) {
                log.warn("KNN 搜索无结果: query={}, datasourceId={}", query, datasourceId);
            } else {
                log.info("KNN 搜索结果: {}",
                        results.stream()
                                .map(r -> r.getMeta() != null ? r.getMeta().getTableName() + "(similarity=" + String.format("%.4f", r.getScore()) + ")" : "null")
                                .collect(Collectors.joining(", ")));
            }
            return results;
        } catch (Exception e) {
            log.error("KNN 搜索失败: query={}, datasourceId={}, error={}", query, datasourceId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除单个表索引
     */
    public void deleteModel(Long modelId) {
        String key = KEY_PREFIX + modelId;
        try {
            jedisPooled.del(key);
            log.info("已删除表模型索引: modelId={}", modelId);
        } catch (Exception e) {
            log.error("删除表模型索引失败: modelId={}, error={}", modelId, e.getMessage(), e);
        }
    }

    /**
     * 清空所有 schema: 前缀的 key
     */
    public void clearAll() {
        try {
            Set<String> keys = jedisPooled.keys(KEY_PREFIX + "*");
            if (!keys.isEmpty()) {
                jedisPooled.del(keys.toArray(new String[0]));
                log.info("已清空所有 schema 索引，共 {} 条", keys.size());
            }
        } catch (Exception e) {
            log.error("清空 schema 索引失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 返回索引数量
     */
    public long getIndexCount() {
        try {
            Set<String> keys = jedisPooled.keys(KEY_PREFIX + "*");
            return keys.size();
        } catch (Exception e) {
            log.error("获取索引数量失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    // ==================== 私有方法 ====================

    private SchemaMeta buildSchemaMeta(Model model, List<ColumnDefinition> columns, String datasourceName) {
        SchemaMeta meta = new SchemaMeta();
        meta.setModelId(model.getId());
        meta.setTableName(model.getTableName());
        meta.setDisplayName(model.getDisplayName());
        meta.setDescription(model.getDescription());
        meta.setDatasourceId(model.getDatasourceId());
        meta.setDatasourceName(datasourceName);

        if (columns != null) {
            List<SchemaMeta.ColumnInfo> columnInfos = columns.stream()
                    .map(col -> {
                        SchemaMeta.ColumnInfo info = new SchemaMeta.ColumnInfo();
                        info.setColumnName(col.getColumnName());
                        info.setDisplayName(col.getDisplayName());
                        info.setColumnType(col.getColumnType());
                        return info;
                    })
                    .collect(Collectors.toList());
            meta.setColumns(columnInfos);
        }
        return meta;
    }

    /**
     * 将 float[] 转换为 FLOAT32 little-endian 二进制格式
     */
    private byte[] toFloat32Bytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    // ==================== 内部类 ====================

    /**
     * 表结构元数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemaMeta {
        private Long modelId;
        private String tableName;
        private String displayName;
        private String description;
        private Long datasourceId;
        private String datasourceName;
        private List<ColumnInfo> columns;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ColumnInfo {
            private String columnName;
            private String displayName;
            private String columnType;
        }
    }

    /**
     * 搜索结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemaSearchResult {
        private SchemaMeta meta;
        private double score;
    }
}
