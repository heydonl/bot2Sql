package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.domain.Relationship;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 检索服务 - 根据用户问题检索相关的表和字段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGRetrievalService {

    private final ModelService modelService;
    private final ColumnDefinitionService columnDefinitionService;
    private final RelationshipService relationshipService;

    /**
     * 根据用户问题检索相关的表和字段
     */
    public RetrievalResult retrieveRelevantSchema(String question, Long datasourceId, int topK) {
        log.info("Retrieving relevant schema for question: {}", question);

        // 1. 提取问题中的关键词
        Set<String> keywords = extractKeywords(question);
        log.debug("Extracted keywords: {}", keywords);

        // 2. 获取所有可用的表
        List<Model> allModels;
        if (datasourceId != null) {
            allModels = modelService.listByDatasourceId(datasourceId);
        } else {
            allModels = modelService.listVisible();
        }

        // 3. 计算每个表的相关性分数
        List<ModelScore> modelScores = new ArrayList<>();
        for (Model model : allModels) {
            if (!model.getIsVisible()) {
                continue;
            }

            double score = calculateModelRelevance(model, keywords);
            if (score > 0) {
                modelScores.add(new ModelScore(model, score));
            }
        }

        // 4. 按分数排序，取 Top-K
        modelScores.sort((a, b) -> Double.compare(b.score, a.score));
        List<Model> relevantModels = modelScores.stream()
                .limit(topK)
                .map(ms -> ms.model)
                .collect(Collectors.toList());

        log.info("Found {} relevant models out of {}", relevantModels.size(), allModels.size());

        // 5. 获取相关表的字段
        Map<Long, List<ColumnDefinition>> modelColumns = new HashMap<>();
        for (Model model : relevantModels) {
            List<ColumnDefinition> columns = columnDefinitionService.listByModelId(model.getId());

            // 计算字段相关性，过滤不相关的字段
            List<ColumnDefinition> relevantColumns = columns.stream()
                    .filter(col -> calculateColumnRelevance(col, keywords) > 0)
                    .collect(Collectors.toList());

            // 如果没有相关字段，保留所有字段
            if (relevantColumns.isEmpty()) {
                modelColumns.put(model.getId(), columns);
            } else {
                modelColumns.put(model.getId(), relevantColumns);
            }
        }

        // 6. 获取相关表之间的关系
        Set<Long> relevantModelIds = relevantModels.stream()
                .map(Model::getId)
                .collect(Collectors.toSet());

        List<Relationship> relevantRelationships = relationshipService.listAll().stream()
                .filter(rel -> relevantModelIds.contains(rel.getFromModelId())
                            && relevantModelIds.contains(rel.getToModelId()))
                .collect(Collectors.toList());

        log.info("Found {} relevant relationships", relevantRelationships.size());

        return new RetrievalResult(relevantModels, modelColumns, relevantRelationships, keywords);
    }

    /**
     * 提取问题中的关键词
     */
    private Set<String> extractKeywords(String question) {
        Set<String> keywords = new HashSet<>();

        // 转换为小写
        String lowerQuestion = question.toLowerCase();

        // 移除常见的停用词
        String[] stopWords = {"the", "a", "an", "is", "are", "was", "were", "be", "been",
                              "have", "has", "had", "do", "does", "did", "will", "would",
                              "could", "should", "may", "might", "can", "of", "in", "on",
                              "at", "to", "for", "with", "by", "from", "how", "many", "what",
                              "which", "who", "where", "when", "why", "show", "me", "get",
                              "find", "list", "display", "there", "their", "them", "this",
                              "that", "these", "those"};
        Set<String> stopWordSet = new HashSet<>(Arrays.asList(stopWords));

        // 分词（简单按空格和标点分割）
        String[] words = lowerQuestion.split("[\\s,?.!;:]+");

        for (String word : words) {
            word = word.trim();
            // 过滤停用词和短词
            if (!word.isEmpty() && word.length() > 2 && !stopWordSet.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * 计算表与问题的相关性分数
     */
    private double calculateModelRelevance(Model model, Set<String> keywords) {
        double score = 0.0;

        String tableName = model.getTableName().toLowerCase();
        String displayName = model.getDisplayName() != null ? model.getDisplayName().toLowerCase() : "";
        String description = model.getDescription() != null ? model.getDescription().toLowerCase() : "";

        for (String keyword : keywords) {
            // 表名完全匹配：高分
            if (tableName.equals(keyword)) {
                score += 10.0;
            }
            // 表名包含关键词：中分
            else if (tableName.contains(keyword) || keyword.contains(tableName)) {
                score += 5.0;
            }

            // 显示名匹配
            if (displayName.contains(keyword)) {
                score += 3.0;
            }

            // 描述匹配
            if (description.contains(keyword)) {
                score += 2.0;
            }
        }

        return score;
    }

    /**
     * 计算字段与问题的相关性分数
     */
    private double calculateColumnRelevance(ColumnDefinition column, Set<String> keywords) {
        double score = 0.0;

        String columnName = column.getColumnName().toLowerCase();
        String displayName = column.getDisplayName() != null ? column.getDisplayName().toLowerCase() : "";
        String description = column.getDescription() != null ? column.getDescription().toLowerCase() : "";

        for (String keyword : keywords) {
            // 字段名匹配
            if (columnName.equals(keyword) || columnName.contains(keyword)) {
                score += 5.0;
            }

            // 显示名匹配
            if (displayName.contains(keyword)) {
                score += 3.0;
            }

            // 描述匹配
            if (description.contains(keyword)) {
                score += 2.0;
            }
        }

        return score;
    }

    /**
     * 检索结果
     */
    @Data
    public static class RetrievalResult {
        private final List<Model> relevantModels;
        private final Map<Long, List<ColumnDefinition>> modelColumns;
        private final List<Relationship> relevantRelationships;
        private final Set<String> extractedKeywords;

        public RetrievalResult(List<Model> relevantModels,
                              Map<Long, List<ColumnDefinition>> modelColumns,
                              List<Relationship> relevantRelationships,
                              Set<String> extractedKeywords) {
            this.relevantModels = relevantModels;
            this.modelColumns = modelColumns;
            this.relevantRelationships = relevantRelationships;
            this.extractedKeywords = extractedKeywords;
        }
    }

    /**
     * 表分数
     */
    @Data
    private static class ModelScore {
        private final Model model;
        private final double score;
    }
}
