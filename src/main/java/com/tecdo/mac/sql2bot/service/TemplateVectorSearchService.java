package com.tecdo.mac.sql2bot.service;

import com.google.gson.Gson;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
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
 * 模板向量搜索服务
 * 使用 RedisStack 原生 KNN 向量搜索管理SQL模板索引
 */
@Slf4j
@Service
public class TemplateVectorSearchService {

    private static final String INDEX_NAME = "idx:template";
    private static final String INDEX_NAME_QUESTION_ANSWER = "idx:question-answer:template";
    private static final String KEY_PREFIX = "template:";
    private static final String VECTOR_FIELD = "vector";
    private static final String TEMPLATE_ID_FIELD = "template_id";
    private static final String DATASOURCE_ID_FIELD = "datasource_id";
    private static final String META_FIELD = "meta";
    private static final String TEMPLATE_SCORE_FIELD = "template_score";

    private final JedisPooled jedisPooled;
    private final EmbeddingService embeddingService;
    private final Gson gson;

    @Value("${embedding.dimension:1024}")
    private int dimension;

    public TemplateVectorSearchService(JedisPooled jedisPooled, EmbeddingService embeddingService) {
        this.jedisPooled = jedisPooled;
        this.embeddingService = embeddingService;
        this.gson = new Gson();
    }

    /**
     * 初始化 RedisStack HNSW 索引
     */
    @PostConstruct
    public void initIndex() {
        try {
            Map<String, Object> info = jedisPooled.ftInfo(INDEX_NAME);
            // 检查现有索引的向量维度是否匹配
            long existingDim = extractDimFromInfo(info);
            if (existingDim > 0 && existingDim != dimension) {
                log.warn("RedisStack 模板索引 {} 维度不匹配（现有: {}, 配置: {}），删除重建", INDEX_NAME, existingDim, dimension);
                jedisPooled.ftDropIndex(INDEX_NAME);
                createIndex();
            } else {
                log.info("RedisStack 模板索引 {} 已存在，维度: {}", INDEX_NAME, existingDim > 0 ? existingDim : "unknown");
            }
        } catch (Exception e) {
            log.info("RedisStack 模板索引 {} 不存在，开始创建", INDEX_NAME);
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
            log.debug("解析模板索引维度失败: {}", e.getMessage());
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
                    TextField.of(TEMPLATE_ID_FIELD),
                    NumericField.of(DATASOURCE_ID_FIELD),
                    TextField.of(META_FIELD).noIndex(),
                    VectorField.builder()
                            .fieldName(VECTOR_FIELD)
                            .algorithm(VectorField.VectorAlgorithm.HNSW)
                            .attributes(vectorAttrs)
                            .build()
            );
            log.info("RedisStack 模板索引 {} 创建成功，向量维度: {}", INDEX_NAME, dimension);
        } catch (Exception ex) {
            log.error("创建 RedisStack 模板索引失败: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 为模板生成并存储向量
     */
    public void indexTemplate(QueryTemplate template) {
        try {
            // 构建模板的文本描述用于向量化
            StringBuilder textBuilder = new StringBuilder();

            if (template.getExampleQuestion() != null && !template.getExampleQuestion().isEmpty()) {
                textBuilder.append("示例问题: ").append(template.getExampleQuestion()).append(". ");
            }

            if (template.getSqlTemplate() != null && !template.getSqlTemplate().isEmpty()) {
                textBuilder.append("SQL模板: ").append(template.getSqlTemplate()).append(". ");
            }

            if (template.getIntent() != null && !template.getIntent().isEmpty()) {
                textBuilder.append("意图: ").append(template.getIntent()).append(". ");
            }

            if (template.getEntity() != null && !template.getEntity().isEmpty()) {
                textBuilder.append("实体: ").append(template.getEntity()).append(". ");
            }

            String text = textBuilder.toString();
            log.debug("为模板生成向量: templateId={}, text={}", template.getId(), text);

            // 生成 embedding
            float[] embedding = embeddingService.generateEmbedding(text);

            // 构建 TemplateMeta
            TemplateMeta meta = buildTemplateMeta(template);

            // 存储到 RedisStack（向量用 bytes 存储）
            String key = KEY_PREFIX + template.getId();
            byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Map<byte[], byte[]> hash = new HashMap<>();
            hash.put(TEMPLATE_ID_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    String.valueOf(template.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(DATASOURCE_ID_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "0".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(META_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    gson.toJson(meta).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hash.put(VECTOR_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    toFloat32Bytes(embedding));
            hash.put(TEMPLATE_SCORE_FIELD.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    String.valueOf(template.getScore() != null ? template.getScore().doubleValue() : 0.0).getBytes(java.nio.charset.StandardCharsets.UTF_8));

            jedisPooled.hset(keyBytes, hash);
            log.info("已索引模板: {} (id={})", template.getExampleQuestion(), template.getId());

        } catch (Exception e) {
            log.error("模板向量索引失败: templateId={}", template.getId(), e);
        }
    }

    /**
     * KNN 向量搜索，返回相似的SQL模板列表
     */
    public List<TemplateSearchResult> searchSimilarTemplates(String query, Long datasourceId, int topK) {
        log.info("开始模板 KNN 搜索: query={}, datasourceId={}, topK={}", query, datasourceId, topK);
        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            log.debug("模板 Embedding 生成完成，维度: {}", queryEmbedding.length);
            byte[] queryVector = toFloat32Bytes(queryEmbedding);

            String queryStr;
            if (datasourceId != null) {
                queryStr = String.format("@%s:[%d %d]=>[KNN %d @%s $BLOB AS score]",
                        DATASOURCE_ID_FIELD, datasourceId, datasourceId, topK, VECTOR_FIELD);
            } else {
                queryStr = String.format("*=>[KNN %d @%s $BLOB AS score]", topK, VECTOR_FIELD);
            }
            log.debug("模板 KNN 查询语句: {}", queryStr);

            Query q = new Query(queryStr)
                    .addParam("BLOB", queryVector)
                    .returnFields(META_FIELD, "score", "template_score")
                    .setSortBy("score", true)
                    .dialect(2);

            SearchResult result = jedisPooled.ftSearch(INDEX_NAME_QUESTION_ANSWER, q);
            log.info("模板 KNN 搜索命中 {} 条结果", result.getDocuments().size());

            List<TemplateSearchResult> results = result.getDocuments().stream()
                    .map(doc -> {
                        String metaJson = (String) doc.get(META_FIELD);
                        TemplateMeta meta = gson.fromJson(metaJson, TemplateMeta.class);
                        double cosineSimilarity = 0.0;
                        Object scoreObj = doc.get("score");
                        if (scoreObj != null) {
                            try {
                                double cosineDistance = Double.parseDouble(scoreObj.toString());
                                cosineSimilarity = 1.0 - cosineDistance / 2.0;
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        // 获取模板的评分（从Redis字段中获取）
                        double templateScore = 0.0; // 默认评分为0
                        Object templateScoreObj = doc.get("template_score");
                        if (templateScoreObj != null) {
                            try {
                                templateScore = Double.parseDouble(templateScoreObj.toString());
                            } catch (NumberFormatException ignored) {
                                // 如果解析失败，使用默认值1.0
                            }
                        }

                        // 计算综合得分：余弦相似度 * 模板评分
                        double finalScore = cosineSimilarity * templateScore;

                        log.debug("命中模板: templateId={}, cosineSimilarity={}, templateScore={}, finalScore={}",
                                meta != null ? meta.getTemplateId() : "null", cosineSimilarity, templateScore, finalScore);

                        // 创建结果对象，同时保存余弦相似度和最终得分
                        TemplateSearchResult templateResult = new TemplateSearchResult(meta, finalScore);
                        templateResult.setCosineSimilarity(cosineSimilarity); // 保存余弦相似度用于过滤
                        return templateResult;
                    })
                    .filter(r -> {
                        // 过滤条件：余弦相似度 >= 0.7 且 综合得分 >= 0.6
                        return r.getCosineSimilarity() >= 0.7 && r.getScore() >= 0.6;
                    })
                    .sorted(Comparator.comparingDouble(TemplateSearchResult::getScore).reversed()) // 按综合得分降序排列
                    .collect(Collectors.toList());

            if (results.isEmpty()) {
                log.warn("模板 KNN 搜索无结果或所有结果被过滤: query={}, datasourceId={}", query, datasourceId);
            } else {
                log.info("模板 KNN 搜索结果（过滤后）: {}",
                        results.stream()
                                .map(r -> r.getMeta() != null ? "templateId=" + r.getMeta().getTemplateId() + "(finalScore=" + String.format("%.4f", r.getScore()) + ")" : "null")
                                .collect(Collectors.joining(", ")));
            }
            return results;
        } catch (Exception e) {
            log.error("模板 KNN 搜索失败: query={}, datasourceId={}, error={}", query, datasourceId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除模板向量
     */
    public void deleteTemplate(Long templateId) {
        String key = KEY_PREFIX + templateId;
        try {
            jedisPooled.del(key);
            log.info("已删除模板索引: templateId={}", templateId);
        } catch (Exception e) {
            log.error("删除模板索引失败: templateId={}, error={}", templateId, e.getMessage(), e);
        }
    }

    /**
     * 清空所有模板索引
     */
    public void clearAll() {
        try {
            Set<String> keys = jedisPooled.keys(KEY_PREFIX + "*");
            if (!keys.isEmpty()) {
                jedisPooled.del(keys.toArray(new String[0]));
                log.info("已清空所有模板索引，共 {} 条", keys.size());
            }
        } catch (Exception e) {
            log.error("清空模板索引失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 返回模板索引数量
     */
    public long getIndexCount() {
        try {
            Set<String> keys = jedisPooled.keys(KEY_PREFIX + "*");
            return keys.size();
        } catch (Exception e) {
            log.error("获取模板索引数量失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    // ==================== 私有方法 ====================

    private TemplateMeta buildTemplateMeta(QueryTemplate template) {
        TemplateMeta meta = new TemplateMeta();
        meta.setTemplateId(template.getId());
        meta.setExampleQuestion(template.getExampleQuestion());
        meta.setIntent(template.getIntent());
        meta.setEntity(template.getEntity());
        meta.setSqlTemplate(template.getSqlTemplate());
        meta.setParameters(template.getParameters());
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
     * 模板元数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateMeta {
        private Long templateId;
        private String exampleQuestion;
        private String intent;
        private String entity;
        private String sqlTemplate;
        private String parameters;
    }

    /**
     * 模板搜索结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateSearchResult {
        private TemplateMeta meta;
        private double score;
        private double cosineSimilarity; // 新增字段保存余弦相似度

        public TemplateSearchResult(TemplateMeta meta, double score) {
            this.meta = meta;
            this.score = score;
        }

        // 为了兼容性，保留 similarity 属性
        public double getSimilarity() {
            return score;
        }
    }
}