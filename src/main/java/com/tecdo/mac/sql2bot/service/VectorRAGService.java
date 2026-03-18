package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.domain.Relationship;
import com.tecdo.mac.sql2bot.scheduler.SchemaIndexService;
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

    private final SchemaVectorStoreService schemaVectorStoreService;
    private final SchemaIndexService schemaIndexService;
    private final ModelService modelService;
    private final RelationshipService relationshipService;

    /**
     * 基于向量相似度检索相关的表和字段
     */
    public RetrievalResult retrieveRelevantSchema(String question, Long datasourceId, int topK) {
        log.info("Vector RAG retrieval for question: {}, datasourceId: {}, topK: {}",
                question, datasourceId, topK);

        // 1. 搜索相关的表
        List<SchemaVectorStoreService.SchemaSearchResult> searchResults =
                schemaVectorStoreService.searchSchemas(question, datasourceId, topK);

        if (searchResults.isEmpty()) {
            log.warn("No relevant schemas found for question: {}", question);
            return new RetrievalResult(
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    Collections.emptyList()
            );
        }

        // 2. 提取相关表的 ID
        List<Long> relevantModelIds = searchResults.stream()
                .map(r -> r.getMeta().getModelId())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("Found {} relevant models: {}", relevantModelIds.size(), relevantModelIds);

        // 3. 从 SchemaMeta 中构建 ColumnDefinition 列表（按 modelId 分组）
        Map<Long, List<ColumnDefinition>> modelColumns = new HashMap<>();
        for (SchemaVectorStoreService.SchemaSearchResult result : searchResults) {
            SchemaVectorStoreService.SchemaMeta meta = result.getMeta();
            if (meta == null || meta.getModelId() == null) {
                continue;
            }
            Long modelId = meta.getModelId();
            List<ColumnDefinition> columns = new ArrayList<>();
            if (meta.getColumns() != null) {
                for (SchemaVectorStoreService.SchemaMeta.ColumnInfo colInfo : meta.getColumns()) {
                    ColumnDefinition col = new ColumnDefinition();
                    col.setModelId(modelId);
                    col.setColumnName(colInfo.getColumnName());
                    col.setDisplayName(colInfo.getDisplayName());
                    col.setColumnType(colInfo.getColumnType());
                    columns.add(col);
                }
            }
            modelColumns.put(modelId, columns);
        }

        // 4. 加载完整的 Model 对象
        List<Model> relevantModels = new ArrayList<>();
        for (Long modelId : relevantModelIds) {
            Model model = modelService.getById(modelId);
            if (model != null) {
                relevantModels.add(model);
            }
        }

        // 5. 查找相关表之间的关系
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
        log.info("Starting full index via SchemaIndexService...");
        schemaIndexService.fullIndex();
        log.info("Full index completed");
    }

    /**
     * 为单个表建立索引
     */
    public void indexModel(Long modelId) {
        log.info("Triggering incremental index for modelId: {}", modelId);
        schemaIndexService.incrementalIndex();
    }

    /**
     * 删除表的索引
     */
    public void deleteModelIndex(Long modelId) {
        schemaVectorStoreService.deleteModel(modelId);
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
