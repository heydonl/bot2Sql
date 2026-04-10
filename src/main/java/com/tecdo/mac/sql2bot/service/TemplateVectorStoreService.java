package com.tecdo.mac.sql2bot.service;

import com.google.gson.Gson;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * SQL模板向量存储服务
 * 使用 RedisStack 原生 KNN 向量搜索管理模板索引
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateVectorStoreService {

    // Redis Stack 索引配置（统一索引）
    private static final String TEMPLATE_INDEX_NAME = "idx:template";
    private static final String TEMPLATE_KEY_PREFIX = "template:";
    private static final String VECTOR_FIELD = "vector";
    private static final String TYPE_FIELD = "type";
    private static final String DATASOURCE_ID_FIELD = "datasource_id";
    private static final String META_FIELD = "meta";

    // 模板类型常量
    private static final int TYPE_SYSTEM_TEMPLATE = 1;  // 系统模板（query_template）
    private static final int TYPE_USER_TEMPLATE = 2;    // 用户模板（user_query_template）

    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JedisPooled jedisPooled;
    private final Gson gson = new Gson();

    @Value("${embedding.dimension:1024}")
    private int dimension;

    /**
     * 初始化 RedisStack HNSW 索引（统一索引）
     */
    @PostConstruct
    public void initIndex() {
        // 只创建一个统一的模板索引
        try {
            Map<String, Object> info = jedisPooled.ftInfo(TEMPLATE_INDEX_NAME);
            long existingDim = extractDimFromInfo(info);
            if (existingDim > 0 && existingDim != dimension) {
                log.warn("RedisStack 索引 {} 维度不匹配（现有: {}, 配置: {}），删除重建", TEMPLATE_INDEX_NAME, existingDim, dimension);
                jedisPooled.ftDropIndex(TEMPLATE_INDEX_NAME);
                createTemplateIndex();
            } else {
                log.info("RedisStack 索引 {} 已存在，维度: {}", TEMPLATE_INDEX_NAME, existingDim > 0 ? existingDim : "unknown");
            }
        } catch (Exception e) {
            log.info("RedisStack 索引 {} 不存在，开始创建", TEMPLATE_INDEX_NAME);
            createTemplateIndex();
        }
    }

    private long extractDimFromInfo(Map<String, Object> info) {
        try {
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

    private void createTemplateIndex() {
        try {
            Map<String, Object> vectorAttrs = new HashMap<>();
            vectorAttrs.put("TYPE", "FLOAT32");
            vectorAttrs.put("DIM", dimension);
            vectorAttrs.put("DISTANCE_METRIC", "COSINE");

            jedisPooled.ftCreate(TEMPLATE_INDEX_NAME,
                    FTCreateParams.createParams()
                            .on(IndexDataType.HASH)
                            .addPrefix(TEMPLATE_KEY_PREFIX),
                    NumericField.of(TYPE_FIELD),
                    NumericField.of(DATASOURCE_ID_FIELD),
                    TextField.of(META_FIELD).noIndex(),
                    VectorField.builder()
                            .fieldName(VECTOR_FIELD)
                            .algorithm(VectorField.VectorAlgorithm.HNSW)
                            .attributes(vectorAttrs)
                            .build()
            );
            log.info("RedisStack 索引 {} 创建成功，向量维度: {}", TEMPLATE_INDEX_NAME, dimension);
        } catch (Exception ex) {
            log.error("创建 RedisStack 索引失败: {}", ex.getMessage(), ex);
        }
    }

    @Deprecated
    private void createUserTemplateIndex() {
        // 已废弃：现在使用统一索引 TEMPLATE_INDEX_NAME
        // 此方法保留但不再执行任何操作
        log.info("createUserTemplateIndex 已废弃，使用统一索引");
    }

    /**
     * 为系统模板创建向量索引
     */
    public void indexTemplate(QueryTemplate template) {
        try {
            // 使用 question 生成 embedding
            float[] embedding = embeddingService.generateEmbedding(template.getQuestion());

            // 构建元数据
            TemplateMeta meta = new TemplateMeta();
            meta.setTemplateId(template.getId());
            meta.setType(TYPE_SYSTEM_TEMPLATE);  // 1=系统模板
            meta.setQuestion(template.getQuestion());
            meta.setGeneratedSql(template.getGeneratedSql());
            meta.setDatasourceId(template.getDatasourceId());
            meta.setScore(template.getScore());
            meta.setUsageCount(template.getUsageCount());

            // 存储到 RedisStack（向量用 bytes 存储）
            String key = TEMPLATE_KEY_PREFIX + template.getId();
            byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Map<byte[], byte[]> hash = new HashMap<>();
            hash.put(TYPE_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    String.valueOf(TYPE_SYSTEM_TEMPLATE).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(DATASOURCE_ID_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    String.valueOf(template.getDatasourceId() != null ? template.getDatasourceId() : 0).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(META_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    gson.toJson(meta).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(VECTOR_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    toFloat32Bytes(embedding));

            jedisPooled.hset(keyBytes, hash);

            log.info("已索引系统模板: templateId={}, question={}",
                    template.getId(), template.getQuestion());

        } catch (Exception e) {
            log.error("索引系统模板失败: templateId={}", template.getId(), e);
            throw new RuntimeException("索引系统模板失败", e);
        }
    }

    /**
     * 搜索系统模板
     */
    public List<TemplateSearchResult> searchSystemTemplates(String question, Long datasourceId, int topK) {
        return searchTemplates(question, datasourceId, topK, TYPE_SYSTEM_TEMPLATE);
    }

    /**
     * 搜索用户模板
     */
    public List<TemplateSearchResult> searchUserTemplates(String question, Long datasourceId, int topK) {
        return searchTemplates(question, datasourceId, topK, TYPE_USER_TEMPLATE);
    }

    /**
     * 通用模板搜索方法
     */
    private List<TemplateSearchResult> searchTemplates(String question, Long datasourceId, int topK, int type) {
        try {
            log.info("搜索{}模板: question={}, datasourceId={}, topK={}",
                    type == TYPE_SYSTEM_TEMPLATE ? "系统" : "用户", question, datasourceId, topK);

            // 生成查询向量
            float[] queryEmbedding = embeddingService.generateEmbedding(question);
            byte[] queryVector = toFloat32Bytes(queryEmbedding);

            // 构建查询：按 type 过滤
            String queryStr;
            if (datasourceId != null) {
                queryStr = String.format("@%s:[%d %d] @%s:[%d %d]=>[KNN %d @%s $BLOB AS score]",
                        TYPE_FIELD, type, type, DATASOURCE_ID_FIELD, datasourceId, datasourceId, topK, VECTOR_FIELD);
            } else {
                queryStr = String.format("@%s:[%d %d]=>[KNN %d @%s $BLOB AS score]",
                        TYPE_FIELD, type, type, topK, VECTOR_FIELD);
            }

            Query q = new Query(queryStr)
                    .addParam("BLOB", queryVector)
                    .returnFields(META_FIELD, "score")
                    .setSortBy("score", true)
                    .dialect(2);

            SearchResult result = jedisPooled.ftSearch(TEMPLATE_INDEX_NAME, q);
            log.info("KNN 搜索命中 {} 条结果", result.getDocuments().size());

            List<TemplateSearchResult> results = result.getDocuments().stream()
                    .map(doc -> {
                        String metaJson = (String) doc.get(META_FIELD);
                        TemplateMeta meta = gson.fromJson(metaJson, TemplateMeta.class);
                        double similarity = 0.0;
                        Object scoreObj = doc.get("score");
                        if (scoreObj != null) {
                            try {
                                double cosineDistance = Double.parseDouble(scoreObj.toString());
                                similarity = 1.0 - cosineDistance / 2.0;
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        TemplateSearchResult searchResult = new TemplateSearchResult();
                        searchResult.setMeta(meta);
                        searchResult.setSimilarity(similarity);
                        return searchResult;
                    })
                    .collect(Collectors.toList());

            log.info("找到 {} 个{}模板", results.size(), type == TYPE_SYSTEM_TEMPLATE ? "系统" : "用户");
            return results;

        } catch (Exception e) {
            log.error("搜索模板失败: type={}", type, e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除模板索引
     */
    public void deleteTemplate(Long templateId) {
        try {
            String key = TEMPLATE_KEY_PREFIX + templateId;
            jedisPooled.del(key);
            log.info("已删除模板索引: templateId={}", templateId);
        } catch (Exception e) {
            log.error("删除模板索引失败: templateId={}", templateId, e);
        }
    }

    /**
     * 清空所有模板索引
     */
    public void clearAllTemplates() {
        try {
            Set<String> keys = jedisPooled.keys(TEMPLATE_KEY_PREFIX + "*");
            if (!keys.isEmpty()) {
                jedisPooled.del(keys.toArray(new String[0]));
                log.info("已清空所有模板索引，共 {} 条", keys.size());
            }
        } catch (Exception e) {
            log.error("清空模板索引失败", e);
        }
    }

    /**
     * 删除用户模板索引
     */
    public void deleteUserTemplate(Long userTemplateId) {
        try {
            String key = TEMPLATE_KEY_PREFIX + userTemplateId;
            jedisPooled.del(key);
            log.info("已删除用户模板索引: userTemplateId={}", userTemplateId);
        } catch (Exception e) {
            log.error("删除用户模板索引失败: userTemplateId={}", userTemplateId, e);
        }
    }

    /**
     * 清空所有用户模板索引
     */
    public void clearAllUserTemplates() {
        try {
            Set<String> keys = jedisPooled.keys(TEMPLATE_KEY_PREFIX + "*");
            if (!keys.isEmpty()) {
                jedisPooled.del(keys.toArray(new String[0]));
                log.info("已清空所有用户模板索引，共 {} 条", keys.size());
            }
        } catch (Exception e) {
            log.error("清空用户模板索引失败", e);
        }
    }

    /**
     * 构建用于embedding的文本
     */
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

    /**
     * 计算余弦相似度（保留用于兼容性，但 Redis Stack 已内置）
     */
    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("向量维度不匹配");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += Math.pow(vectorA.get(i), 2);
            normB += Math.pow(vectorB.get(i), 2);
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ==================== 用户模板向量索引方法 ====================

    /**
     * 搜索相似的用户查询模板
     */
    public List<TemplateSearchResult> searchSimilarUserTemplates(String question, Long datasourceId, int topK) {
        try {
            log.info("搜索相似用户模板: question={}, datasourceId={}, topK={}", question, datasourceId, topK);

            float[] queryEmbedding = embeddingService.generateEmbedding(question);
            byte[] queryVector = toFloat32Bytes(queryEmbedding);

            String queryStr;
            if (datasourceId != null) {
                queryStr = String.format("@%s:[%d %d]=>[KNN %d @%s $BLOB AS score]",
                        DATASOURCE_ID_FIELD, datasourceId, datasourceId, topK, VECTOR_FIELD);
            } else {
                queryStr = String.format("*=>[KNN %d @%s $BLOB AS score]", topK, VECTOR_FIELD);
            }

            Query q = new Query(queryStr)
                    .addParam("BLOB", queryVector)
                    .returnFields(META_FIELD, "score")
                    .setSortBy("score", true)
                    .dialect(2);

            SearchResult result = jedisPooled.ftSearch(TEMPLATE_INDEX_NAME, q);
            log.info("KNN 搜索命中 {} 条结果", result.getDocuments().size());

            List<TemplateSearchResult> results = result.getDocuments().stream()
                    .map(doc -> {
                        String metaJson = (String) doc.get(META_FIELD);
                        TemplateMeta meta = gson.fromJson(metaJson, TemplateMeta.class);
                        double similarity = 0.0;
                        Object scoreObj = doc.get("score");
                        if (scoreObj != null) {
                            try {
                                double cosineDistance = Double.parseDouble(scoreObj.toString());
                                similarity = 1.0 - cosineDistance / 2.0;
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        TemplateSearchResult searchResult = new TemplateSearchResult();
                        searchResult.setMeta(meta);
                        searchResult.setSimilarity(similarity);
                        return searchResult;
                    })
                    .collect(Collectors.toList());

            log.info("找到 {} 个相似用户模板，返回前 {} 个", results.size(), results.size());
            return results;

        } catch (Exception e) {
            log.error("搜索相似用户模板失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 异步索引用户查询模板到向量存储
     */
    @Async
    public CompletableFuture<Void> indexUserTemplate(UserQueryTemplate userTemplate) {
        try {
            if (userTemplate.getQuestion() == null || userTemplate.getQuestion().trim().isEmpty()) {
                log.warn("用户模板没有问题文本，跳过索引: userTemplateId={}", userTemplate.getId());
                return CompletableFuture.completedFuture(null);
            }

            // 生成嵌入向量
            float[] embedding = embeddingService.generateEmbedding(userTemplate.getQuestion());

            // 构建元数据（type=2 表示 user_template）
            TemplateMeta meta = new TemplateMeta();
            meta.setTemplateId(userTemplate.getId());
            meta.setType(TYPE_USER_TEMPLATE);  // 2=user_template
            meta.setQuestion(userTemplate.getQuestion());
            meta.setGeneratedSql(userTemplate.getGeneratedSql());
            meta.setDatasourceId(userTemplate.getDatasourceId());

            // 存储到 RedisStack（使用 user_ 前缀避免ID冲突）
            String key = TEMPLATE_KEY_PREFIX + "user_" + userTemplate.getId();
            byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Map<byte[], byte[]> hash = new HashMap<>();
            hash.put(TYPE_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    String.valueOf(TYPE_USER_TEMPLATE).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(DATASOURCE_ID_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    String.valueOf(userTemplate.getDatasourceId() != null ? userTemplate.getDatasourceId() : 0).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(META_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    gson.toJson(meta).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(VECTOR_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    toFloat32Bytes(embedding));

            jedisPooled.hset(keyBytes, hash);

            log.info("用户模板向量索引成功: userTemplateId={}", userTemplate.getId());
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("用户模板向量索引失败: userTemplateId={}", userTemplate.getId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 模板元数据
     */
    @Data
    public static class TemplateMeta {
        private Long templateId;
        private Integer type;  // 1=系统模板, 2=用户模板
        private String question;
        private String generatedSql;
        private Long datasourceId;
        private java.math.BigDecimal score;
        private Integer usageCount;
    }

    /**
     * 模板搜索结果
     */
    @Data
    public static class TemplateSearchResult {
        private TemplateMeta meta;
        private double similarity;
    }
}