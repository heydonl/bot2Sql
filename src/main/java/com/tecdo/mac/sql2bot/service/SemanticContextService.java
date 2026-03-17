package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.DataSource;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.domain.Relationship;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 语义上下文生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticContextService {

    private final ModelService modelService;
    private final ColumnDefinitionService columnDefinitionService;
    private final RelationshipService relationshipService;
    private final DataSourceService dataSourceService;
    private final RAGRetrievalService ragRetrievalService;  // 使用关键词 RAG

    /**
     * 生成系统 Prompt（语义模型上下文）
     * 使用关键词 RAG 检索相关的表和字段
     *
     * @param datasourceId 数据源ID，如果为 null 则从所有数据源中检索
     * @param question 用户问题，用于 RAG 检索
     */
    public String generateSystemPrompt(Long datasourceId, String question) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的 SQL 查询助手。你的任务是根据用户的自然语言问题生成准确的 SQL 查询语句。\n\n");

        // 使用关键词 RAG 检索相关的表和字段
        log.info("Using Keyword RAG to retrieve relevant schema for question: {}", question);
        RAGRetrievalService.RetrievalResult ragResult = ragRetrievalService.retrieveRelevantSchema(
            question, datasourceId, 10
        );

        log.info("Keyword RAG retrieved {} models, {} columns, {} relationships",
            ragResult.getRelevantModels().size(),
            ragResult.getModelColumns().values().stream().mapToInt(List::size).sum(),
            ragResult.getRelevantRelationships().size()
        );

        // 如果指定了数据源，只显示该数据源的信息
        if (datasourceId != null) {
            prompt.append("## 数据库结构\n\n");
            appendRelevantModels(prompt, ragResult, datasourceId);
        } else {
            // 未指定数据源，显示所有相关数据源
            prompt.append("## 可用的数据源和数据库结构\n\n");
            prompt.append("系统中有多个数据源，请根据用户问题自动选择合适的数据源。\n\n");
            prompt.append("**根据问题检索到的相关表**:\n\n");

            // 按数据源分组显示
            Map<Long, List<Model>> modelsByDatasource = new HashMap<>();
            for (Model model : ragResult.getRelevantModels()) {
                modelsByDatasource.computeIfAbsent(model.getDatasourceId(), k -> new java.util.ArrayList<>())
                    .add(model);
            }

            if (modelsByDatasource.isEmpty()) {
                log.warn("No relevant models found for question: {}", question);
                prompt.append("**警告**: 未找到与问题相关的表。\n\n");
            } else {
                for (Map.Entry<Long, List<Model>> entry : modelsByDatasource.entrySet()) {
                    Long dsId = entry.getKey();
                    DataSource ds = dataSourceService.getById(dsId);
                    if (ds != null) {
                        prompt.append("### 数据源: ").append(ds.getName()).append("\n");
                        prompt.append("- **ID**: ").append(ds.getId()).append("\n");
                        prompt.append("- **类型**: ").append(ds.getType()).append("\n");
                        prompt.append("- **数据库**: ").append(ds.getDatabaseName()).append("\n\n");
                        prompt.append("**相关的表**:\n");
                        appendRelevantModels(prompt, ragResult, dsId);
                        prompt.append("---\n\n");
                    }
                }
            }
        }

        // 添加相关的表关系信息
        if (!ragResult.getRelevantRelationships().isEmpty()) {
            prompt.append("## 表关系\n\n");
            for (Relationship rel : ragResult.getRelevantRelationships()) {
                Model fromModel = modelService.getById(rel.getFromModelId());
                Model toModel = modelService.getById(rel.getToModelId());

                if (fromModel != null && toModel != null) {
                    prompt.append("- ").append(fromModel.getTableName())
                          .append(" → ").append(toModel.getTableName())
                          .append(" (").append(rel.getJoinType()).append(")");

                    if (rel.getDescription() != null) {
                        prompt.append(": ").append(rel.getDescription());
                    }

                    prompt.append("\n  JOIN 条件: ").append(rel.getJoinCondition()).append("\n");
                }
            }
            prompt.append("\n");
        }

        // 添加 SQL 生成规则
        prompt.append("## SQL 生成规则\n\n");
        prompt.append("1. 只生成 SELECT 查询语句，不要生成 INSERT、UPDATE、DELETE 等修改数据的语句\n");
        prompt.append("2. 使用标准的 MySQL 语法\n");
        prompt.append("3. 字段名和表名使用反引号包裹（如 `table_name`.`column_name`）\n");
        prompt.append("4. 如果需要 JOIN 多个表，使用上面定义的表关系\n");
        prompt.append("5. 对于聚合查询，使用适当的 GROUP BY 子句\n");
        prompt.append("6. 添加合理的 LIMIT 限制（默认 100 条）\n");
        prompt.append("7. 只返回 SQL 语句，用 ```sql 代码块包裹\n");
        prompt.append("8. 在 SQL 语句后，用自然语言简要解释这个查询的含义\n");

        if (datasourceId == null) {
            prompt.append("9. **重要**: 如果有多个数据源，请在解释中明确说明你选择了哪个数据源以及原因\n");
        }
        prompt.append("\n");

        prompt.append("## 示例\n\n");
        prompt.append("用户问题: 查询所有用户的数量\n");
        prompt.append("你的回答:\n");
        prompt.append("```sql\n");
        prompt.append("SELECT COUNT(*) as user_count FROM `users`;\n");
        prompt.append("```\n");
        prompt.append("这个查询统计了 users 表中的总记录数。\n");

        return prompt.toString();
    }

    /**
     * 添加 RAG 检索到的相关模型信息到 Prompt
     */
    private void appendRelevantModels(StringBuilder prompt, RAGRetrievalService.RetrievalResult ragResult, Long datasourceId) {
        List<Model> relevantModels = ragResult.getRelevantModels().stream()
            .filter(m -> datasourceId == null || m.getDatasourceId().equals(datasourceId))
            .toList();

        for (Model model : relevantModels) {
            if (!model.getIsVisible()) {
                continue;
            }

            prompt.append("#### 表: ").append(model.getTableName()).append("\n");
            if (model.getDescription() != null && !model.getDescription().isEmpty()) {
                prompt.append("**描述**: ").append(model.getDescription()).append("\n");
            }
            if (model.getPrimaryKey() != null) {
                prompt.append("**主键**: ").append(model.getPrimaryKey()).append("\n");
            }
            prompt.append("\n**相关字段**:\n");

            // 获取 RAG 检索到的相关字段
            List<ColumnDefinition> relevantColumns = ragResult.getModelColumns().get(model.getId());
            if (relevantColumns != null && !relevantColumns.isEmpty()) {
                for (ColumnDefinition column : relevantColumns) {
                    prompt.append("- `").append(column.getColumnName()).append("` (")
                          .append(column.getDataType()).append(")");

                    if (column.getDescription() != null && !column.getDescription().isEmpty()) {
                        prompt.append(": ").append(column.getDescription());
                    }

                    if (column.getColumnType() != null) {
                        prompt.append(" [").append(column.getColumnType()).append("]");
                    }

                    prompt.append("\n");
                }
            } else {
                // 如果没有检索到相关字段，加载所有字段
                List<ColumnDefinition> allColumns = columnDefinitionService.listByModelId(model.getId());
                for (ColumnDefinition column : allColumns) {
                    prompt.append("- `").append(column.getColumnName()).append("` (")
                          .append(column.getDataType()).append(")");

                    if (column.getDescription() != null && !column.getDescription().isEmpty()) {
                        prompt.append(": ").append(column.getDescription());
                    }

                    if (column.getColumnType() != null) {
                        prompt.append(" [").append(column.getColumnType()).append("]");
                    }

                    prompt.append("\n");
                }
            }
            prompt.append("\n");
        }
    }

    /**
     * 添加指定数据源的模型信息到 Prompt（保留用于向后兼容）
     * @deprecated 使用 appendRelevantModels 替代
     */
    @Deprecated
    private void appendDataSourceModels(StringBuilder prompt, Long datasourceId) {
        List<Model> models = modelService.listByDatasourceId(datasourceId);
        if (models.isEmpty()) {
            models = modelService.listVisible();
        }

        for (Model model : models) {
            if (!model.getIsVisible()) {
                continue;
            }

            prompt.append("#### 表: ").append(model.getTableName()).append("\n");
            if (model.getDescription() != null && !model.getDescription().isEmpty()) {
                prompt.append("**描述**: ").append(model.getDescription()).append("\n");
            }
            if (model.getPrimaryKey() != null) {
                prompt.append("**主键**: ").append(model.getPrimaryKey()).append("\n");
            }
            prompt.append("\n**字段列表**:\n");

            // 获取字段信息
            List<ColumnDefinition> columns = columnDefinitionService.listByModelId(model.getId());
            for (ColumnDefinition column : columns) {
                prompt.append("- `").append(column.getColumnName()).append("` (")
                      .append(column.getDataType()).append(")");

                if (column.getDescription() != null && !column.getDescription().isEmpty()) {
                    prompt.append(": ").append(column.getDescription());
                }

                if (column.getColumnType() != null) {
                    prompt.append(" [").append(column.getColumnType()).append("]");
                }

                prompt.append("\n");
            }
            prompt.append("\n");
        }
    }

    /**
     * 根据 SQL 语句推断使用的数据源
     * 通过分析 SQL 中使用的表名来判断
     */
    public Long inferDataSourceFromSQL(String sql) {
        // 提取 SQL 中的表名
        String upperSQL = sql.toUpperCase();

        // 获取所有模型
        List<Model> allModels = modelService.listAll();
        Map<Long, Integer> datasourceMatchCount = new HashMap<>();

        for (Model model : allModels) {
            String tableName = model.getTableName().toUpperCase();
            if (upperSQL.contains("`" + tableName + "`") ||
                upperSQL.contains(" " + tableName + " ") ||
                upperSQL.contains(" " + tableName + ";") ||
                upperSQL.contains("FROM " + tableName) ||
                upperSQL.contains("JOIN " + tableName)) {

                Long dsId = model.getDatasourceId();
                datasourceMatchCount.put(dsId, datasourceMatchCount.getOrDefault(dsId, 0) + 1);
            }
        }

        // 返回匹配次数最多的数据源
        return datasourceMatchCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 生成用户 Prompt
     */
    public String generateUserPrompt(String question) {
        return "用户问题: " + question + "\n\n请生成对应的 SQL 查询语句。";
    }

    /**
     * 根据字段列表生成精确的系统提示词
     * 用于意图分析后的精确上下文生成
     *
     * @param datasourceId 数据源 ID
     * @param fieldNames 字段名列表（维度 + 指标）
     * @return 系统提示词
     */
    public String generateSystemPromptForTables(Long datasourceId, List<String> fieldNames) {
        log.info("根据字段列表生成精确上下文: datasourceId={}, fields={}", datasourceId, fieldNames);

        if (fieldNames == null || fieldNames.isEmpty()) {
            log.warn("字段列表为空，降级到 RAG 检索");
            return generateSystemPrompt(datasourceId, "");
        }

        // 1. 查询包含这些字段的所有表
        Set<Long> modelIds = new HashSet<>();
        for (String fieldName : fieldNames) {
            List<ColumnDefinition> columns = columnDefinitionService.listByColumnName(fieldName);
            for (ColumnDefinition column : columns) {
                Model model = modelService.getById(column.getModelId());
                if (model != null && model.getDatasourceId().equals(datasourceId)) {
                    modelIds.add(column.getModelId());
                }
            }
        }

        if (modelIds.isEmpty()) {
            log.warn("未找到包含指定字段的表，降级到 RAG 检索");
            return generateSystemPrompt(datasourceId, String.join(", ", fieldNames));
        }

        log.info("找到 {} 个相关表: {}", modelIds.size(), modelIds);

        // 2. 获取这些表的完整信息
        List<Model> models = modelIds.stream()
            .map(modelService::getById)
            .filter(java.util.Objects::nonNull)
            .toList();

        // 3. 查询这些表之间的关系
        List<Relationship> relationships = relationshipService.listByModelIds(new ArrayList<>(modelIds));

        // 4. 构建系统提示词
        return buildSystemPromptFromModels(models, relationships, datasourceId);
    }

    /**
     * 根据模型列表和关系构建系统提示词
     */
    private String buildSystemPromptFromModels(List<Model> models, List<Relationship> relationships, Long datasourceId) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的 SQL 查询助手。你的任务是根据用户的自然语言问题生成准确的 SQL 查询语句。\n\n");

        // 添加数据库结构信息
        prompt.append("## 数据库结构\n\n");

        for (Model model : models) {
            if (!model.getIsVisible()) {
                continue;
            }

            prompt.append("#### 表: ").append(model.getTableName()).append("\n");
            if (model.getDescription() != null && !model.getDescription().isEmpty()) {
                prompt.append("**描述**: ").append(model.getDescription()).append("\n");
            }
            if (model.getPrimaryKey() != null) {
                prompt.append("**主键**: ").append(model.getPrimaryKey()).append("\n");
            }
            prompt.append("\n**字段列表**:\n");

            // 获取字段信息
            List<ColumnDefinition> columns = columnDefinitionService.listByModelId(model.getId());
            for (ColumnDefinition column : columns) {
                prompt.append("- `").append(column.getColumnName()).append("` (")
                      .append(column.getDataType()).append(")");

                if (column.getDescription() != null && !column.getDescription().isEmpty()) {
                    prompt.append(": ").append(column.getDescription());
                }

                if (column.getColumnType() != null) {
                    prompt.append(" [").append(column.getColumnType()).append("]");
                }

                prompt.append("\n");
            }
            prompt.append("\n");
        }

        // 添加表关系信息
        if (!relationships.isEmpty()) {
            prompt.append("## 表关系\n\n");
            for (Relationship rel : relationships) {
                Model fromModel = modelService.getById(rel.getFromModelId());
                Model toModel = modelService.getById(rel.getToModelId());

                if (fromModel != null && toModel != null) {
                    prompt.append("- ").append(fromModel.getTableName())
                          .append(" → ").append(toModel.getTableName())
                          .append(" (").append(rel.getJoinType()).append(")");

                    if (rel.getDescription() != null) {
                        prompt.append(": ").append(rel.getDescription());
                    }

                    prompt.append("\n  JOIN 条件: ").append(rel.getJoinCondition()).append("\n");
                }
            }
            prompt.append("\n");
        }

        // 添加 SQL 生成规则
        prompt.append("## SQL 生成规则\n\n");
        prompt.append("1. 只生成 SELECT 查询语句，不要生成 INSERT、UPDATE、DELETE 等修改数据的语句\n");
        prompt.append("2. 使用标准的 MySQL 语法\n");
        prompt.append("3. 字段名和表名使用反引号包裹（如 `table_name`.`column_name`）\n");
        prompt.append("4. 如果需要 JOIN 多个表，使用上面定义的表关系\n");
        prompt.append("5. 对于聚合查询，使用适当的 GROUP BY 子句\n");
        prompt.append("6. 添加合理的 LIMIT 限制（默认 100 条）\n");
        prompt.append("7. 只返回 SQL 语句，用 ```sql 代码块包裹\n");
        prompt.append("8. 在 SQL 语句后，用自然语言简要解释这个查询的含义\n\n");

        prompt.append("## 示例\n\n");
        prompt.append("用户问题: 查询所有用户的数量\n");
        prompt.append("你的回答:\n");
        prompt.append("```sql\n");
        prompt.append("SELECT COUNT(*) as user_count FROM `users`;\n");
        prompt.append("```\n");
        prompt.append("这个查询统计了 users 表中的总记录数。\n");

        return prompt.toString();
    }
}
