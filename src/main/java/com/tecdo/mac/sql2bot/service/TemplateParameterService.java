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
     * 填充模板
     * 将意图分析结果中的参数值填充到 SQL 模板的占位符中
     *
     * @param template 查询模板
     * @param intent 意图分析结果
     * @return 填充后的 SQL
     */
    public String fillTemplate(QueryTemplate template, IntentAnalysisResponse intent) {
        log.info("开始填充模板: templateId={}, skeleton={}", template.getId(), template.getSkeleton());

        String sql = template.getSqlTemplate();

        // 解析参数定义
        List<TemplateParameter> parameters = parseParameters(template.getParameters());

        if (parameters.isEmpty()) {
            log.debug("模板无参数定义，直接返回 SQL");
            return sql;
        }

        log.debug("解析到 {} 个参数", parameters.size());

        // 遍历参数，提取值并替换占位符
        for (TemplateParameter param : parameters) {
            try {
                // 从意图中提取参数值
                Object value = extractValue(param, intent);

                if (value == null) {
                    if (Boolean.TRUE.equals(param.getRequired())) {
                        log.warn("必需参数 {} 未找到值", param.getName());
                    }
                    // 使用默认值
                    value = param.getDefaultValue();
                }

                if (value != null) {
                    // 格式化参数值
                    String formattedValue = formatValue(param, value);

                    // 替换占位符
                    String placeholder = "{{" + param.getName() + "}}";
                    sql = sql.replace(placeholder, formattedValue);

                    log.debug("参数 {} 填充完成: {}", param.getName(), formattedValue);
                }
            } catch (Exception e) {
                log.error("填充参数 {} 时出错", param.getName(), e);
            }
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
