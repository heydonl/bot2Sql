package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SQL模板向量存储服务
 * 用于存储和检索SQL模板的向量表示，支持基于语义相似度的模板匹配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateVectorStoreService {

    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TEMPLATE_VECTOR_KEY_PREFIX = "template_vector:";
    private static final String TEMPLATE_META_KEY_PREFIX = "template_meta:";
    private static final String TEMPLATE_INDEX_KEY = "template_index";

    /**
     * 为SQL模板创建向量索引
     */
    public void indexTemplate(QueryTemplate template) {
        try {
            // 构建用于embedding的文本：示例问题 + 意图 + 实体
            String embeddingText = buildEmbeddingText(template);

            // 生成向量
            float[] vectorArray = embeddingService.generateEmbedding(embeddingText);
            List<Double> vector = new ArrayList<>();
            for (float f : vectorArray) {
                vector.add((double) f);
            }

            // 存储向量
            String vectorKey = TEMPLATE_VECTOR_KEY_PREFIX + template.getId();
            redisTemplate.opsForList().rightPushAll(vectorKey, vector.toArray());

            // 存储元数据
            TemplateMeta meta = new TemplateMeta();
            meta.setTemplateId(template.getId());
            meta.setSkeleton(template.getSkeleton());
            meta.setSqlTemplate(template.getSqlTemplate());
            meta.setIntent(template.getIntent());
            meta.setEntity(template.getEntity());
            meta.setExampleQuestion(template.getExampleQuestion());
            meta.setScore(template.getScore());
            meta.setUsageCount(template.getUsageCount());

            String metaKey = TEMPLATE_META_KEY_PREFIX + template.getId();
            redisTemplate.opsForValue().set(metaKey, meta);

            // 添加到索引
            redisTemplate.opsForSet().add(TEMPLATE_INDEX_KEY, template.getId().toString());

            log.info("已为模板创建向量索引: templateId={}, embeddingText={}",
                    template.getId(), embeddingText);

        } catch (Exception e) {
            log.error("创建模板向量索引失败: templateId={}", template.getId(), e);
            throw new RuntimeException("创建模板向量索引失败", e);
        }
    }

    /**
     * 搜索相似的SQL模板
     */
    public List<TemplateSearchResult> searchSimilarTemplates(String question, Long datasourceId, int topK) {
        try {
            log.info("搜索相似SQL模板: question={}, datasourceId={}, topK={}",
                    question, datasourceId, topK);

            // 生成查询向量
            float[] queryVectorArray = embeddingService.generateEmbedding(question);
            List<Double> queryVector = new ArrayList<>();
            for (float f : queryVectorArray) {
                queryVector.add((double) f);
            }

            // 获取所有模板ID
            Set<Object> templateIds = redisTemplate.opsForSet().members(TEMPLATE_INDEX_KEY);
            if (templateIds == null || templateIds.isEmpty()) {
                log.warn("没有找到任何模板索引");
                return Collections.emptyList();
            }

            List<TemplateSearchResult> results = new ArrayList<>();

            for (Object templateIdObj : templateIds) {
                String templateId = templateIdObj.toString();

                try {
                    // 获取模板元数据
                    String metaKey = TEMPLATE_META_KEY_PREFIX + templateId;
                    TemplateMeta meta = (TemplateMeta) redisTemplate.opsForValue().get(metaKey);
                    if (meta == null) {
                        continue;
                    }

                    // 获取模板向量
                    String vectorKey = TEMPLATE_VECTOR_KEY_PREFIX + templateId;
                    List<Object> vectorObjs = redisTemplate.opsForList().range(vectorKey, 0, -1);
                    if (vectorObjs == null || vectorObjs.isEmpty()) {
                        continue;
                    }

                    List<Double> templateVector = vectorObjs.stream()
                            .map(obj -> Double.valueOf(obj.toString()))
                            .collect(Collectors.toList());

                    // 计算余弦相似度
                    double similarity = calculateCosineSimilarity(queryVector, templateVector);

                    TemplateSearchResult result = new TemplateSearchResult();
                    result.setMeta(meta);
                    result.setSimilarity(similarity);
                    results.add(result);

                } catch (Exception e) {
                    log.warn("处理模板时出错: templateId={}", templateId, e);
                }
            }

            // 按相似度排序并返回topK
            results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

            List<TemplateSearchResult> topResults = results.stream()
                    .limit(topK)
                    .collect(Collectors.toList());

            log.info("找到 {} 个相似模板，返回前 {} 个", results.size(), topResults.size());

            return topResults;

        } catch (Exception e) {
            log.error("搜索相似模板失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除模板索引
     */
    public void deleteTemplate(Long templateId) {
        try {
            String vectorKey = TEMPLATE_VECTOR_KEY_PREFIX + templateId;
            String metaKey = TEMPLATE_META_KEY_PREFIX + templateId;

            redisTemplate.delete(vectorKey);
            redisTemplate.delete(metaKey);
            redisTemplate.opsForSet().remove(TEMPLATE_INDEX_KEY, templateId.toString());

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
            Set<Object> templateIds = redisTemplate.opsForSet().members(TEMPLATE_INDEX_KEY);
            if (templateIds != null) {
                for (Object templateIdObj : templateIds) {
                    String templateId = templateIdObj.toString();
                    String vectorKey = TEMPLATE_VECTOR_KEY_PREFIX + templateId;
                    String metaKey = TEMPLATE_META_KEY_PREFIX + templateId;
                    redisTemplate.delete(vectorKey);
                    redisTemplate.delete(metaKey);
                }
            }
            redisTemplate.delete(TEMPLATE_INDEX_KEY);

            log.info("已清空所有模板索引");

        } catch (Exception e) {
            log.error("清空模板索引失败", e);
        }
    }

    /**
     * 构建用于embedding的文本
     */
    private String buildEmbeddingText(QueryTemplate template) {
        StringBuilder sb = new StringBuilder();

        if (template.getExampleQuestion() != null) {
            sb.append("问题: ").append(template.getExampleQuestion()).append(" ");
        }

        if (template.getIntent() != null) {
            sb.append("意图: ").append(template.getIntent()).append(" ");
        }

        if (template.getEntity() != null) {
            sb.append("实体: ").append(template.getEntity()).append(" ");
        }

        if (template.getSupportedDimensions() != null) {
            sb.append("维度: ").append(template.getSupportedDimensions()).append(" ");
        }

        if (template.getSupportedMetrics() != null) {
            sb.append("指标: ").append(template.getSupportedMetrics());
        }

        return sb.toString().trim();
    }

    /**
     * 计算余弦相似度
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

    /**
     * 模板元数据
     */
    @Data
    public static class TemplateMeta {
        private Long templateId;
        private String skeleton;
        private String sqlTemplate;
        private String intent;
        private String entity;
        private String exampleQuestion;
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