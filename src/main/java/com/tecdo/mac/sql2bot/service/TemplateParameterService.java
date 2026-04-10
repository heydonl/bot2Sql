package com.tecdo.mac.sql2bot.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.dto.TemplateParameter;
import com.tecdo.mac.sql2bot.dto.intent.IntentAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模板参数服务
 * 负责从意图分析结果中提取参数值并填充到 SQL 模板中
 */
@Slf4j
@Service
public class TemplateParameterService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 使用LLM生成的参数值填充模板
     *
     * @param template 查询模板
     * @param llmParameterResponse LLM生成的参数响应
     * @return 填充后的 SQL
     */
    public String fillTemplateWithLLMParameters(QueryTemplate template, String llmParameterResponse) {
        log.info("开始使用LLM参数填充模板: templateId={}", template.getId());

        String sql = template.getGeneratedSql();

        try {
            // 从LLM响应中提取JSON参数
            String jsonBlock = extractJsonBlock(llmParameterResponse);
            if (jsonBlock == null) {
                log.warn("LLM响应中未找到JSON参数块，尝试解析整个响应");
                jsonBlock = llmParameterResponse;
            }

            // 解析参数值
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> parameterValues = objectMapper.readValue(
                jsonBlock, java.util.Map.class);

            log.debug("解析到 {} 个参数值", parameterValues.size());

            // 替换模板中的占位符
            for (java.util.Map.Entry<String, Object> entry : parameterValues.entrySet()) {
                String paramName = entry.getKey();
                Object value = entry.getValue();

                if (value != null) {
                    String placeholder = "{{" + paramName + "}}";
                    String formattedValue = formatLLMValue(value);
                    sql = sql.replace(placeholder, formattedValue);

                    log.debug("参数 {} 填充完成: {}", paramName, formattedValue);
                }
            }

            log.info("LLM参数模板填充完成");
            return sql;

        } catch (Exception e) {
            log.error("使用LLM参数填充模板失败", e);
            throw new RuntimeException("模板参数填充失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文本中提取JSON代码块
     */
    private String extractJsonBlock(String text) {
        if (text == null) return null;

        // 尝试提取 ```json ``` 代码块
        int start = text.indexOf("```json");
        if (start != -1) {
            start = text.indexOf("\n", start) + 1;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        // 尝试提取 ``` 代码块
        start = text.indexOf("```");
        if (start != -1) {
            start = text.indexOf("\n", start) + 1;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        // 尝试查找JSON对象
        start = text.indexOf("{");
        if (start != -1) {
            int end = text.lastIndexOf("}");
            if (end > start) {
                return text.substring(start, end + 1).trim();
            }
        }

        return null;
    }

    /**
     * 格式化LLM生成的参数值
     */
    private String formatLLMValue(Object value) {
        if (value == null) {
            return "";
        }

        // 如果是字符串且看起来像日期，添加单引号
        String valueStr = value.toString();
        if (valueStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "'" + valueStr + "'";
        }

        // 如果是纯数字，直接返回
        if (valueStr.matches("\\d+(\\.\\d+)?")) {
            return valueStr;
        }

        // 其他情况添加单引号
        return "'" + valueStr + "'";
    }

    /**
     * 填充模板
     * 直接返回生成的SQL（新模板结构不再需要参数填充）
     *
     * @param template 查询模板
     * @param intent 意图分析结果（已废弃，保留参数以兼容）
     * @return 生成的 SQL
     */
    public String fillTemplate(QueryTemplate template, IntentAnalysisResponse intent) {
        log.info("开始填充模板: templateId={}", template.getId());

        // 新模板结构直接存储完整的 generatedSql，不需要参数填充
        String sql = template.getGeneratedSql();

        if (sql == null || sql.isEmpty()) {
            log.warn("模板 SQL 为空: templateId={}", template.getId());
            return "";
        }

        log.info("模板填充完成");
        return sql;
    }

    /**
     * 解析参数定义 JSON
     *
     * @param parametersJson 参数定义 JSON 字符串
     * @return 参数列表
     */
    public List<TemplateParameter> parseParameters(String parametersJson) {
        if (!StringUtils.hasText(parametersJson)) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(parametersJson, new TypeReference<List<TemplateParameter>>() {});
        } catch (Exception e) {
            log.error("解析参数定义 JSON 失败: {}", parametersJson, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从意图分析结果中提取参数值
     *
     * @param param 参数定义
     * @param intent 意图分析结果
     * @return 提取的值
     */
    public Object extractValue(TemplateParameter param, IntentAnalysisResponse intent) {
        String sourceField = param.getSourceField();

        if (!StringUtils.hasText(sourceField)) {
            log.warn("参数 {} 未定义 sourceField", param.getName());
            return null;
        }

        log.debug("从意图中提取参数 {}: sourceField={}", param.getName(), sourceField);

        // 根据 sourceField 从意图中提取值
        switch (sourceField) {
            case "dimensions":
                return intent.getDimensions();

            case "metrics":
                return intent.getMetrics();

            case "entity":
                return intent.getEntity();

            case "dateRanges.startDate":
                return intent.getDateRanges() != null ? intent.getDateRanges().getStartDate() : null;

            case "dateRanges.endDate":
                return intent.getDateRanges() != null ? intent.getDateRanges().getEndDate() : null;

            case "dimensionFilter":
                return intent.getDimensionFilter();

            case "metricFilter":
                return intent.getMetricFilter();

            case "orderBys":
                return intent.getOrderBys();

            case "limit":
                return intent.getLimit();

            default:
                log.warn("未知的 sourceField: {}", sourceField);
                return null;
        }
    }

    /**
     * 格式化参数值
     *
     * @param param 参数定义
     * @param value 原始值
     * @return 格式化后的字符串
     */
    public String formatValue(TemplateParameter param, Object value) {
        if (value == null) {
            return "";
        }

        String type = param.getType();
        if (!StringUtils.hasText(type)) {
            return value.toString();
        }

        log.debug("格式化参数 {}: type={}, value={}", param.getName(), type, value);

        switch (type.toUpperCase()) {
            case "DATE":
                return formatDate(value, param.getFormat());

            case "ARRAY_TO_IN":
                return formatArrayToIn(value);

            case "DIMENSION":
            case "METRIC":
                return formatFieldName(value);

            case "STRING":
                return formatString(value);

            case "NUMBER":
                return value.toString();

            default:
                log.warn("未知的参数类型: {}", type);
                return value.toString();
        }
    }

    /**
     * 格式化日期
     */
    private String formatDate(Object value, String format) {
        try {
            String dateStr = value.toString();
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);

            if (StringUtils.hasText(format)) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                return "'" + date.format(formatter) + "'";
            }

            return "'" + date.format(DATE_FORMATTER) + "'";
        } catch (Exception e) {
            log.error("格式化日期失败: {}", value, e);
            return "'" + value.toString() + "'";
        }
    }

    /**
     * 格式化数组为 IN 子句
     * 例如: ['a', 'b'] -> ('a', 'b')
     */
    private String formatArrayToIn(Object value) {
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;

            if (list.isEmpty()) {
                return "('')";
            }

            String values = list.stream()
                    .map(item -> "'" + item.toString() + "'")
                    .collect(Collectors.joining(", "));

            return "(" + values + ")";
        }

        return "('" + value.toString() + "')";
    }

    /**
     * 格式化字段名
     */
    private String formatFieldName(Object value) {
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            return list.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }

        return value.toString();
    }

    /**
     * 格式化字符串（添加单引号）
     */
    private String formatString(Object value) {
        return "'" + value.toString() + "'";
    }
}
