package com.tecdo.mac.sql2bot.service;

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

    /**
     * 获取评分最高的系统模板作为参考示例
     */
    public List<QueryTemplate> getTopRatedTemplates(int limit) {
        try {
            return templateMapper.selectTopRated(limit);
        } catch (Exception e) {
            log.error("获取高评分模板失败", e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 查找匹配的模板（已废弃）
     * 新架构使用向量搜索，此方法保留以兼容旧代码
     *
     * @param skeleton 骨架字符串（已废弃）
     * @return null
     */
    @Deprecated
    public QueryTemplate findTemplate(String skeleton) {
        log.warn("findTemplate(skeleton) 方法已废弃，请使用向量搜索");
        return null;
    }

    /**
     * 验证模板是否适用于当前意图（已废弃）
     * 新架构不再使用意图验证
     *
     * @param template 候选模板
     * @param intent 用户意图
     * @return false
     */
    @Deprecated
    public boolean validateTemplate(QueryTemplate template, IntentAnalysisResponse intent) {
        log.warn("validateTemplate 方法已废弃");
        return false;
    }

    /**
     * 保存 LLM 生成的模板
     *
     * @param skeleton 骨架字符串（已废弃）
     * @param sqlTemplate SQL 模板
     * @param intent 意图类型（已废弃）
     * @param entity 实体类型（已废弃）
     * @param dimensions 支持的维度列表（已废弃）
     * @param metrics 支持的指标列表（已废弃）
     * @param datasourceId 数据源 ID
     * @return 保存的模板 ID
     */
    @Deprecated
    @Transactional
    public Long saveGeneratedTemplate(
            String skeleton,
            String sqlTemplate,
            String intent,
            String entity,
            List<String> dimensions,
            List<IntentAnalysisResponse.MetricDefinition> metrics,
            Long datasourceId) {
        log.warn("saveGeneratedTemplate 方法已废弃，请使用 saveTemplate");
        return null;
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
     * 解析支持的维度列表（已废弃）
     */
    @Deprecated
    private List<String> parseSupportedDimensions(String dimensionsJson) {
        return List.of();
    }

    /**
     * 解析支持的指标列表（已废弃）
     */
    @Deprecated
    private List<String> parseSupportedMetrics(String metricsJson) {
        return List.of();
    }

    /**
     * 根据ID获取模板
     */
    public QueryTemplate getById(Long templateId) {
        return templateMapper.selectById(templateId);
    }

    /**
     * 获取所有模板
     */
    public List<QueryTemplate> listAll() {
        return templateMapper.selectAll();
    }

    /**
     * 更新模板
     */
    @Transactional
    public void updateTemplate(QueryTemplate template) {
        templateMapper.updateById(template);
    }

    /**
     * 保存模板
     */
    @Transactional
    public QueryTemplate saveTemplate(QueryTemplate template) {
        templateMapper.insert(template);
        return template;
    }
}
