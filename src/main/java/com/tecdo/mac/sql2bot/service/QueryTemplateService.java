package com.tecdo.mac.sql2bot.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.dto.intent.IntentAnalysisResponse;
import com.tecdo.mac.sql2bot.mapper.QueryTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 查询模板服务
 * 负责模板检索、验证、保存和评分管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryTemplateService {

    private final QueryTemplateMapper templateMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 查找匹配的模板（两层检索）
     * 第一层：精确匹配骨架字符串
     * 第二层：TODO 向量相似度搜索（暂不实现）
     *
     * @param skeleton 骨架字符串
     * @return 匹配的模板，如果没有则返回 null
     */
    public QueryTemplate findTemplate(String skeleton) {
        log.info("开始查找模板: skeleton={}", skeleton);

        // 第一层：精确匹配骨架字符串
        QueryTemplate template = templateMapper.selectBySkeleton(skeleton);

        if (template != null) {
            log.info("找到精确匹配的模板: id={}, score={}", template.getId(), template.getScore());
            return template;
        }

        // 第二层：TODO 向量相似度搜索
        // 使用 RedisStack 的向量搜索功能，找到语义相似的模板
        log.debug("精确匹配未找到，向量相似度搜索暂未实现");

        return null;
    }

    /**
     * 验证模板是否适用于当前意图（字段级验证）
     *
     * @param template 候选模板
     * @param intent 用户意图
     * @return true 如果模板适用，false 否则
     */
    public boolean validateTemplate(QueryTemplate template, IntentAnalysisResponse intent) {
        try {
            log.info("开始验证模板: templateId={}, intent={}", template.getId(), intent.getIntent());

            // 1. 意图类型必须匹配
            if (!template.getIntent().equals(intent.getIntent().name())) {
                log.warn("意图类型不匹配: template={}, intent={}", template.getIntent(), intent.getIntent());
                return false;
            }

            // 2. 实体类型必须匹配（如果模板指定了实体）
            if (template.getEntity() != null && !template.getEntity().isEmpty()) {
                if (!template.getEntity().equals(intent.getEntity())) {
                    log.warn("实体类型不匹配: template={}, intent={}", template.getEntity(), intent.getEntity());
                    return false;
                }
            }

            // 3. 维度字段验证（intent 的维度必须是模板支持的子集）
            if (intent.getDimensions() != null && !intent.getDimensions().isEmpty()) {
                List<String> supportedDimensions = parseSupportedDimensions(template.getSupportedDimensions());
                for (String dimension : intent.getDimensions()) {
                    if (!supportedDimensions.contains(dimension)) {
                        log.warn("维度字段不支持: dimension={}, supported={}", dimension, supportedDimensions);
                        return false;
                    }
                }
            }

            // 4. 指标字段验证（intent 的指标必须是模板支持的子集）
            if (intent.getMetrics() != null && !intent.getMetrics().isEmpty()) {
                List<String> supportedMetrics = parseSupportedMetrics(template.getSupportedMetrics());
                for (IntentAnalysisResponse.MetricDefinition metric : intent.getMetrics()) {
                    String metricKey = metric.getField() + "_" + metric.getAggregation();
                    if (!supportedMetrics.contains(metricKey)) {
                        log.warn("指标字段不支持: metric={}, supported={}", metricKey, supportedMetrics);
                        return false;
                    }
                }
            }

            log.info("模板验证通过: templateId={}", template.getId());
            return true;

        } catch (Exception e) {
            log.error("模板验证失败", e);
            return false;
        }
    }

    /**
     * 保存 LLM 生成的模板
     *
     * @param skeleton 骨架字符串
     * @param sqlTemplate SQL 模板
     * @param intent 意图类型
     * @param entity 实体类型
     * @param dimensions 支持的维度列表
     * @param metrics 支持的指标列表
     * @param datasourceId 数据源 ID
     * @return 保存的模板 ID
     */
    @Transactional
    public Long saveGeneratedTemplate(
            String skeleton,
            String sqlTemplate,
            String intent,
            String entity,
            List<String> dimensions,
            List<IntentAnalysisResponse.MetricDefinition> metrics,
            Long datasourceId) {
        try {
            log.info("开始保存生成的模板: skeleton={}, intent={}", skeleton, intent);

            QueryTemplate template = new QueryTemplate();
            template.setSkeleton(skeleton);
            template.setSqlTemplate(sqlTemplate);
            template.setIntent(intent);
            template.setEntity(entity);
            template.setDatasourceId(datasourceId);

            // 序列化 dimensions 为 JSON
            if (dimensions != null && !dimensions.isEmpty()) {
                String dimensionsJson = objectMapper.writeValueAsString(dimensions);
                template.setSupportedDimensions(dimensionsJson);
            }

            // 序列化 metrics 为 JSON
            if (metrics != null && !metrics.isEmpty()) {
                String metricsJson = objectMapper.writeValueAsString(metrics);
                template.setSupportedMetrics(metricsJson);
            }

            templateMapper.insert(template);

            log.info("模板保存成功: id={}", template.getId());

            // TODO 同步到 RedisStack 向量存储
            // 将模板的骨架字符串转换为向量，存储到 Redis 用于相似度搜索

            return template.getId();

        } catch (Exception e) {
            log.error("保存模板失败", e);
            throw new RuntimeException("保存模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新模板评分
     *
     * @param templateId 模板 ID
     * @param rating 评分（1-5）
     */
    @Transactional
    public void updateRating(Long templateId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分必须在 1-5 之间");
        }

        log.info("更新模板评分: templateId={}, rating={}", templateId, rating);
        templateMapper.updateRating(templateId, rating);
    }

    /**
     * 增加模板使用次数
     *
     * @param templateId 模板 ID
     */
    @Transactional
    public void incrementUsageCount(Long templateId) {
        log.debug("增加模板使用次数: templateId={}", templateId);
        templateMapper.incrementUsageCount(templateId);
    }

    /**
     * 解析支持的维度列表
     */
    private List<String> parseSupportedDimensions(String dimensionsJson) {
        try {
            if (dimensionsJson == null || dimensionsJson.isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(dimensionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("解析维度 JSON 失败: {}", dimensionsJson, e);
            return List.of();
        }
    }

    /**
     * 解析支持的指标列表
     */
    private List<String> parseSupportedMetrics(String metricsJson) {
        try {
            if (metricsJson == null || metricsJson.isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(metricsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("解析指标 JSON 失败: {}", metricsJson, e);
            return List.of();
        }
    }
}
