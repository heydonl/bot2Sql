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
 * 基于向量的 RAG 检索服务
 * 使用 Embedding 和 Redis 向量数据库进行语义检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorRAGService {

    private final VectorStoreService vectorStoreService;
    private final ModelService modelService;
    private final ColumnDefinitionService columnDefinitionService;
    private final RelationshipService relationshipService;

    /**
     * 基于向量相似度检索相关的表和字段
     */
    public RetrievalResult retrieveRelevantSchema(String question, Long datasourceId, int topK) {
        log.info("Vector RAG retrieval for question: {}, datasourceId: {}, topK: {}",
                question, datasourceId, topK);

        // 1. 搜索相关的表
        List<VectorStoreService.ModelSearchResult> modelResults =
                vectorStoreService.searchModels(question, datasourceId, topK);

        if (modelResults.isEmpty()) {
            log.warn("No relevant models found for question: {}", question);
            return new RetrievalResult(
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    Collections.emptyList()
            );
        }

        // 2. 提取相关表的 ID
        List<Long> relevantModelIds = modelResults.stream()
                .map(r -> r.getModel().getId())
                .collect(Collectors.toList());

        log.info("Found {} relevant models: {}", relevantModelIds.size(), relevantModelIds);

        // 3. 搜索相关的字段
        List<VectorStoreService.ColumnSearchResult> columnResults =
                vectorStoreService.searchColumns(question, relevantModelIds, topK * 3);

        // 4. 按模型分组字段
        Map<Long, List<ColumnDefinition>> modelColumns = new HashMap<>();
        for (VectorStoreService.ColumnSearchResult result : columnResults) {
            Long modelId = result.getColumn().getModelId();
            ColumnDefinition column = columnDefinitionService.getById(result.getColumn().getId());
            if (column != null) {
                modelColumns.computeIfAbsent(modelId, k -> new ArrayList<>()).add(column);
            }
        }

        // 5. 加载完整的 Model 对象
        List<Model> relevantModels = new ArrayList<>();
        for (Long modelId : relevantModelIds) {
            Model model = modelService.getById(modelId);
            if (model != null) {
                relevantModels.add(model);
            }
        }

        // 6. 查找相关表之间的关系
        List<Relationship> relevantRelationships = findRelevantRelationships(relevantModelIds);

        log.info("Vector RAG retrieved {} models, {} columns, {} relationships",
                relevantModels.size(),
                modelColumns.values().stream().mapToInt(List::size).sum(),
                relevantRelationships.size()
        );

        return new RetrievalResult(relevantModels, modelColumns, relevantRelationships);
    }

    /**
     * 查找相关表之间的关系
     */
    private List<Relationship> findRelevantRelationships(List<Long> modelIds) {
        Set<Long> modelIdSet = new HashSet<>(modelIds);
        List<Relationship> allRelationships = relationshipService.listAll();

        return allRelationships.stream()
                .filter(rel -> modelIdSet.contains(rel.getFromModelId()) ||
                              modelIdSet.contains(rel.getToModelId()))
                .collect(Collectors.toList());
    }

    /**
     * 为所有表和字段建立索引
     */
    public void indexAllModelsAndColumns() {
        log.info("Starting to index all models and columns...");

        // 清空现有索引
        vectorStoreService.clearAll();

        // 索引所有表
        List<Model> allModels = modelService.listAll();
        log.info("Indexing {} models", allModels.size());

        for (Model model : allModels) {
            try {
                vectorStoreService.indexModel(model);

                // 索引该表的所有字段
                List<ColumnDefinition> columns = columnDefinitionService.listByModelId(model.getId());
                for (ColumnDefinition column : columns) {
                    vectorStoreService.indexColumn(column);
                }

                log.info("Indexed model {} with {} columns", model.getTableName(), columns.size());

            } catch (Exception e) {
                log.error("Failed to index model: {}", model.getId(), e);
            }
        }

        log.info("Finished indexing all models and columns");
    }

    /**
     * 为单个表建立索引
     */
    public void indexModel(Long modelId) {
        Model model = modelService.getById(modelId);
        if (model == null) {
            log.warn("Model not found: {}", modelId);
            return;
        }

        vectorStoreService.indexModel(model);

        List<ColumnDefinition> columns = columnDefinitionService.listByModelId(modelId);
        for (ColumnDefinition column : columns) {
            vectorStoreService.indexColumn(column);
        }

        log.info("Indexed model {} with {} columns", model.getTableName(), columns.size());
    }

    /**
     * 删除表的索引
     */
    public void deleteModelIndex(Long modelId) {
        vectorStoreService.deleteModel(modelId);

        List<ColumnDefinition> columns = columnDefinitionService.listByModelId(modelId);
        for (ColumnDefinition column : columns) {
            vectorStoreService.deleteColumn(column.getId());
        }

        log.info("Deleted index for model: {}", modelId);
    }

    /**
     * 检索结果
     */
    @Data
    public static class RetrievalResult {
        private final List<Model> relevantModels;
        private final Map<Long, List<ColumnDefinition>> modelColumns;
        private final List<Relationship> relevantRelationships;

        public RetrievalResult(List<Model> relevantModels,
                              Map<Long, List<ColumnDefinition>> modelColumns,
                              List<Relationship> relevantRelationships) {
            this.relevantModels = relevantModels;
            this.modelColumns = modelColumns;
            this.relevantRelationships = relevantRelationships;
        }
    }
}
