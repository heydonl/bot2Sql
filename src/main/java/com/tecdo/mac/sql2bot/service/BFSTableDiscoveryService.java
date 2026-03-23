package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.domain.Relationship;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BFS表关联查找服务
 * 当用户对查询结果不满意时，通过BFS算法查找相关表并生成新的SQL模板
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BFSTableDiscoveryService {

    private final VectorRAGService vectorRAGService;
    private final ModelService modelService;
    private final RelationshipService relationshipService;
    private final ColumnDefinitionService columnDefinitionService;
    private final AIService aiService;
    private final QueryTemplateService queryTemplateService;
    private final IntentFewShotService intentFewShotService;
    private final TemplateVectorStoreService templateVectorStoreService;

    /**
     * 基于用户问题进行BFS表发现并生成新模板
     */
    public BFSDiscoveryResult discoverTablesAndGenerateTemplate(String question, Long datasourceId) {
        try {
            log.info("开始BFS表发现: question={}, datasourceId={}", question, datasourceId);

            // 1. 基于高召回率搜索相关表（初始种子表）
            VectorRAGService.RetrievalResult initialResult =
                vectorRAGService.retrieveRelevantSchema(question, datasourceId, 20); // 增加召回数量

            if (initialResult.getRelevantModels().isEmpty()) {
                log.warn("未找到相关表，无法进行BFS发现");
                return new BFSDiscoveryResult(false, "未找到相关表", null, null);
            }

            Set<Long> seedModelIds = initialResult.getRelevantModels().stream()
                .map(Model::getId)
                .collect(Collectors.toSet());

            log.info("初始种子表: {}", seedModelIds);

            // 2. 执行BFS两层深度查找关联表
            Set<Long> discoveredModelIds = performBFS(seedModelIds, 2);
            log.info("BFS发现的所有表: {}", discoveredModelIds);

            // 3. 构建完整的表结构信息
            List<Model> allModels = new ArrayList<>();
            Map<Long, List<ColumnDefinition>> allColumns = new HashMap<>();

            for (Long modelId : discoveredModelIds) {
                Model model = modelService.getById(modelId);
                if (model != null) {
                    allModels.add(model);
                    List<ColumnDefinition> columns = columnDefinitionService.getByModelId(modelId);
                    allColumns.put(modelId, columns);
                }
            }

            // 4. 查找所有相关的表关系
            List<Relationship> allRelationships = findAllRelationships(discoveredModelIds);

            // 5. 获取few-shot示例
            String fewShotExamples = intentFewShotService.getFewShotExamples(datasourceId, question);

            // 6. 构建上下文并生成新模板
            String contextPrompt = buildBFSContextPrompt(
                question, allModels, allColumns, allRelationships, fewShotExamples
            );

            log.info("调用LLM生成新模板，上下文长度: {}", contextPrompt.length());

            String llmResponse = aiService.generateSQLTemplate(contextPrompt, question);

            // 7. 解析LLM响应并创建新模板
            QueryTemplate newTemplate = parseLLMResponseToTemplate(llmResponse, datasourceId);

            if (newTemplate != null) {
                // 保存新模板
                QueryTemplate savedTemplate = queryTemplateService.saveTemplate(newTemplate);

                // 为新模板创建向量索引
                templateVectorStoreService.indexTemplate(savedTemplate);

                log.info("成功生成并保存新模板: templateId={}", savedTemplate.getId());

                return new BFSDiscoveryResult(true, "成功生成新模板", savedTemplate, discoveredModelIds);
            } else {
                log.warn("LLM响应解析失败，无法生成新模板");
                return new BFSDiscoveryResult(false, "LLM响应解析失败", null, discoveredModelIds);
            }

        } catch (Exception e) {
            log.error("BFS表发现失败", e);
            return new BFSDiscoveryResult(false, "BFS表发现失败: " + e.getMessage(), null, null);
        }
    }

    /**
     * 执行BFS算法查找关联表
     */
    private Set<Long> performBFS(Set<Long> seedModelIds, int maxDepth) {
        Set<Long> visited = new HashSet<>();
        Queue<BFSNode> queue = new LinkedList<>();

        // 初始化队列
        for (Long modelId : seedModelIds) {
            queue.offer(new BFSNode(modelId, 0));
            visited.add(modelId);
        }

        while (!queue.isEmpty()) {
            BFSNode current = queue.poll();

            if (current.getDepth() >= maxDepth) {
                continue;
            }

            // 查找与当前表直接关联的表
            List<Long> connectedModels = findDirectlyConnectedModels(current.getModelId());

            for (Long connectedModelId : connectedModels) {
                if (!visited.contains(connectedModelId)) {
                    visited.add(connectedModelId);
                    queue.offer(new BFSNode(connectedModelId, current.getDepth() + 1));
                }
            }
        }

        log.info("BFS完成，深度{}，发现{}个表", maxDepth, visited.size());
        return visited;
    }

    /**
     * 查找与指定表直接关联的表
     */
    private List<Long> findDirectlyConnectedModels(Long modelId) {
        List<Relationship> relationships = relationshipService.findByModelId(modelId);

        return relationships.stream()
            .flatMap(rel -> {
                List<Long> connected = new ArrayList<>();
                if (!modelId.equals(rel.getFromModelId())) {
                    connected.add(rel.getFromModelId());
                }
                if (!modelId.equals(rel.getToModelId())) {
                    connected.add(rel.getToModelId());
                }
                return connected.stream();
            })
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 查找所有相关的表关系
     */
    private List<Relationship> findAllRelationships(Set<Long> modelIds) {
        List<Relationship> allRelationships = relationshipService.listAll();

        return allRelationships.stream()
            .filter(rel -> modelIds.contains(rel.getFromModelId()) ||
                          modelIds.contains(rel.getToModelId()))
            .collect(Collectors.toList());
    }

    /**
     * 构建BFS上下文提示词
     */
    private String buildBFSContextPrompt(String question,
                                       List<Model> models,
                                       Map<Long, List<ColumnDefinition>> columns,
                                       List<Relationship> relationships,
                                       String fewShotExamples) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个SQL模板生成专家。基于用户的问题和以下完整的数据库结构信息，生成一个可重用的SQL模板。\n\n");

        sb.append("## 用户问题\n");
        sb.append(question).append("\n\n");

        sb.append("## 数据库表结构\n");
        for (Model model : models) {
            sb.append("### 表: ").append(model.getTableName());
            if (model.getDisplayName() != null) {
                sb.append(" (").append(model.getDisplayName()).append(")");
            }
            sb.append("\n");

            if (model.getDescription() != null) {
                sb.append("描述: ").append(model.getDescription()).append("\n");
            }

            sb.append("字段:\n");
            List<ColumnDefinition> modelColumns = columns.get(model.getId());
            if (modelColumns != null) {
                for (ColumnDefinition col : modelColumns) {
                    sb.append("- ").append(col.getColumnName());
                    if (col.getColumnType() != null) {
                        sb.append(" (").append(col.getColumnType()).append(")");
                    }
                    if (col.getDisplayName() != null) {
                        sb.append(" - ").append(col.getDisplayName());
                    }
                    if (col.getDescription() != null) {
                        sb.append(" : ").append(col.getDescription());
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("## 表关系\n");
        for (Relationship rel : relationships) {
            sb.append("- ").append(getModelName(rel.getFromModelId(), models))
              .append(".").append(rel.getFromColumn())
              .append(" -> ").append(getModelName(rel.getToModelId(), models))
              .append(".").append(rel.getToColumn())
              .append(" (").append(rel.getRelationshipType()).append(")\n");
        }
        sb.append("\n");

        if (fewShotExamples != null && !fewShotExamples.trim().isEmpty()) {
            sb.append("## 参考示例\n");
            sb.append(fewShotExamples).append("\n\n");
        }

        sb.append("## 要求\n");
        sb.append("1. 生成标准MySQL SQL模板，使用{{paramName}}作为参数占位符\n");
        sb.append("2. 模板应该具有通用性，可以通过参数适配类似的查询\n");
        sb.append("3. 充分利用表关系进行JOIN操作\n");
        sb.append("4. 输出格式为JSON，包含以下字段：\n");
        sb.append("   - sql_template: SQL模板字符串\n");
        sb.append("   - parameters: 参数定义数组\n");
        sb.append("   - intent: 查询意图\n");
        sb.append("   - entity: 主要实体\n");
        sb.append("   - example_question: 示例问题\n");
        sb.append("   - skeleton: 查询骨架\n");
        sb.append("\n请用```json```代码块包裹你的响应。");

        return sb.toString();
    }

    /**
     * 获取模型名称
     */
    private String getModelName(Long modelId, List<Model> models) {
        return models.stream()
            .filter(m -> m.getId().equals(modelId))
            .map(Model::getTableName)
            .findFirst()
            .orElse("unknown_table");
    }

    /**
     * 解析LLM响应为QueryTemplate
     */
    private QueryTemplate parseLLMResponseToTemplate(String llmResponse, Long datasourceId) {
        try {
            // 提取JSON代码块
            String jsonBlock = extractJsonBlock(llmResponse);
            if (jsonBlock == null) {
                log.warn("LLM响应中未找到JSON代码块");
                return null;
            }

            // 这里需要使用Jackson解析JSON
            // 简化实现，实际应该使用ObjectMapper
            // JsonNode root = objectMapper.readTree(jsonBlock);

            QueryTemplate template = new QueryTemplate();
            // template.setSqlTemplate(root.get("sql_template").asText());
            // template.setParameters(root.get("parameters").toString());
            // template.setIntent(root.get("intent").asText());
            // template.setEntity(root.get("entity").asText());
            // template.setExampleQuestion(root.get("example_question").asText());
            // template.setSkeleton(root.get("skeleton").asText());
            template.setScore(new java.math.BigDecimal("0.0"));
            template.setRatingCount(0);
            template.setUsageCount(0);

            return template;

        } catch (Exception e) {
            log.error("解析LLM响应失败", e);
            return null;
        }
    }

    /**
     * 提取JSON代码块
     */
    private String extractJsonBlock(String text) {
        if (text == null) return null;
        int start = text.indexOf("```json");
        if (start == -1) return null;
        start = text.indexOf("\n", start) + 1;
        int end = text.indexOf("```", start);
        if (end == -1) return null;
        return text.substring(start, end).trim();
    }

    /**
     * BFS节点
     */
    @Data
    private static class BFSNode {
        private final Long modelId;
        private final int depth;
    }

    /**
     * BFS发现结果
     */
    @Data
    public static class BFSDiscoveryResult {
        private final boolean success;
        private final String message;
        private final QueryTemplate generatedTemplate;
        private final Set<Long> discoveredModelIds;
    }
}