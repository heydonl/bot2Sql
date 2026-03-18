package com.tecdo.mac.sql2bot.service;

import tools.jackson.databind.ObjectMapper;
import com.tecdo.mac.sql2bot.domain.IntentFewShot;
import com.tecdo.mac.sql2bot.dto.intent.*;
import com.tecdo.mac.sql2bot.mapper.IntentFewShotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 意图分析服务
 * 负责将用户的自然语言问题转换为结构化的意图表示
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentAnalysisService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;
    private final IntentFewShotMapper fewShotMapper;

    /**
     * 分析用户问题的意图
     */
    public IntentAnalysisResponse analyzeIntent(IntentAnalysisRequest request) {
        try {
            log.info("开始分析用户意图: {}", request.getQuestion());

            // 1. 加载启用的 few-shot 示例
            List<IntentFewShot> fewShots = fewShotMapper.selectActiveExamples();

            // 2. 构建包含 few-shot 的 prompt
            String prompt = buildIntentAnalysisPrompt(request, fewShots);
            log.info("意图分析 Prompt 内容:\n{}", prompt);

            // 3. 调用 AI 服务进行意图分析
            String aiResponse = aiService.generateSQL("", prompt);
            log.info("AI 返回的原始响应:\n{}", aiResponse);

            // 4. 解析 AI 返回的 JSON
            IntentAnalysisResponse response = parseIntentJson(aiResponse);

            // 5. 如果需要，生成骨架格式
            if (request.getIncludeSkeleton()) {
                String skeleton = convertToSkeleton(response);
                response.setSkeleton(skeleton);
            }

            // 6. 如果是 OTHER 意图，记录日志提示需要补充 few-shot
            if (response.getIntent() == QueryIntent.OTHER) {
                log.warn("检测到 OTHER 意图，需要人工添加 few-shot: question={}", request.getQuestion());
            }

            log.info("意图分析完成: intent={}, skeleton={}", response.getIntent(), response.getSkeleton());

            return response;

        } catch (Exception e) {
            log.error("意图分析失败，降级到 OTHER 意图", e);
            return createFallbackIntent(request.getQuestion());
        }
    }

    /**
     * 构建意图分析的 prompt，动态注入 few-shot 示例
     */
    private String buildIntentAnalysisPrompt(IntentAnalysisRequest request, List<IntentFewShot> fewShots) {
        String currentDate = request.getCurrentDate();
        if (currentDate == null || currentDate.isEmpty()) {
            currentDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        StringBuilder prompt = new StringBuilder(String.format("""
                你是一个广告账户管理系统的意图理解模块。你的任务是将用户的自然语言问题转换为结构化的意图表示，输出严格的 JSON 格式。

                ### 意图类型（intent）
                必须是以下之一：
                - SINGLE_METRIC_QUERY：单指标查询（如工单数、消耗金额）
                - COMPARISON_QUERY：对比查询（两个时间段对比）
                - RANKING_QUERY：排名查询（如 top N）
                - TREND_QUERY：趋势查询（如月度趋势）
                - DETAIL_QUERY：明细查询（列出符合条件的记录）
                - OTHER：其他不属于以上类型的查询

                ### 工单类型与数字编码映射（taskType）
                当用户提到以下工单类型时，请使用对应的数字编码（字符串形式）：
                - 开户工单 → "10"
                - 广告账户充值工单 → "30"
                - 广告账户减款工单 → "50"（清零工单也使用"50"）
                - 广告账户清零工单 → "50"
                - 广告账户绑定BM/MCC/BC工单 → "20"
                - 广告账户解绑BM/MCC/BC工单 → "21"
                - 广告账户绑定邮箱工单 → "25"
                - 广告账户解绑邮箱工单 → "26"
                - 广告账户绑定主页工单 → "90"
                - 广告账户解绑主页工单 → "100"
                - 广告账户绑定pixel工单 → "70"
                - 其他工单类型 → null

                ### 输出 JSON 结构
                输出必须包含以下两个顶级字段：
                - "intent": 意图类型（字符串）
                - entity: 实体类型（如 "work_order"）
                - dimensions: 维度字段数组
                - metrics: 指标数组，每个包含 field 和 aggregation
                - dateRanges: 日期范围对象（包含 startDate 和 endDate）
                - comparisonPeriods: 对比时间段数组
                - dimensionFilter: 维度过滤条件
                - metricFilter: 指标过滤条件
                - orderBys: 排序规则数组
                - limit: 限制行数

                注意：
                - 所有时间范围请尽量转换为具体的 YYYY-MM-DD 格式
                - 当前日期是：%s
                - 如果问题中没有提到某字段，填 null
                - 对于工单类型，优先使用上述数字编码；如果无法确定，填 null
                - 输出必须是合法的 JSON，不要添加任何额外解释或标记

                """, currentDate));

        // 动态注入 few-shot 示例
        if (fewShots != null && !fewShots.isEmpty()) {
            prompt.append("### 参考示例\n\n");
            for (IntentFewShot fewShot : fewShots) {
                prompt.append(String.format("""
                        #### 示例：%s
                        用户：%s
                        输出：
                        %s

                        """,
                        fewShot.getIntent(),
                        fewShot.getQuestion(),
                        fewShot.getIntentJson()
                ));
            }
        }

        prompt.append(String.format("""
                ### 现在，请分析以下用户问题，输出 JSON（使用 ```json ``` 代码块包裹）：

                用户：%s
                输出：
                ```json

                ```
                """, request.getQuestion()));

        return prompt.toString();
    }

    /**
     * 解析 AI 返回的 JSON，失败时抛出异常由上层降级处理
     */
    private IntentAnalysisResponse parseIntentJson(String aiResponse) {
        try {
            String cleanJson = aiResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            IntentAnalysisResponse response = objectMapper.readValue(cleanJson, IntentAnalysisResponse.class);
            response.setRawJson(cleanJson);

            return response;

        } catch (Exception e) {
            log.error("解析意图 JSON 失败: {}", aiResponse, e);
            throw new RuntimeException("JSON 解析失败", e);
        }
    }

    /**
     * 降级：返回 OTHER 意图，确保系统不因意图分析失败而崩溃
     */
    private IntentAnalysisResponse createFallbackIntent(String question) {
        IntentAnalysisResponse response = new IntentAnalysisResponse();
        response.setIntent(QueryIntent.OTHER);
        response.setEntity("unknown");
        response.setDimensions(new ArrayList<>());
        response.setMetrics(new ArrayList<>());
        response.setSkeleton("INTENT=OTHER | ENTITY=unknown | DIMS=none | METRICS=none | DATERANGES=none | DIMFILTERS=none | METFILTERS=none | ORDER=none | LIMIT=none | COMPPERIODS=none");
        return response;
    }

    /**
     * 将意图 JSON 转换为骨架格式
     */
    public String convertToSkeleton(IntentAnalysisResponse response) {
        String intent = response.getIntent().name();
        String entityType = response.getEntity() != null ? response.getEntity() : "none";
        String dims = formatDimensions(response.getDimensions());
        String metrics = formatMetrics(response.getMetrics());
        String dateRanges = response.getDateRanges() != null ? "yes" : "none";
        String dimFilters = formatFilterFields(response.getDimensionFilter());
        String metFilters = formatFilterFields(response.getMetricFilter());
        String order = formatOrderBy(response.getOrderBys());
        String limit = response.getLimit() != null ? "yes" : "none";
        String compPeriods = (response.getComparisonPeriods() != null && !response.getComparisonPeriods().isEmpty())
                ? "yes" : "none";

        return String.format(
                "INTENT=%s | ENTITY=%s | DIMS=%s | METRICS=%s | DATERANGES=%s | DIMFILTERS=%s | METFILTERS=%s | ORDER=%s | LIMIT=%s | COMPPERIODS=%s",
                intent, entityType, dims, metrics, dateRanges, dimFilters, metFilters, order, limit, compPeriods
        );
    }

    private String formatDimensions(List<String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) return "none";
        return String.join(",", dimensions);
    }

    private String formatMetrics(List<IntentAnalysisResponse.MetricDefinition> metrics) {
        if (metrics == null || metrics.isEmpty()) return "none";
        return metrics.stream()
                .map(m -> {
                    String field = "*".equals(m.getField()) ? "star" : m.getField();
                    return field + "_" + m.getAggregation();
                })
                .collect(Collectors.joining(","));
    }

    private String formatFilterFields(IntentAnalysisResponse.FilterCondition filterCondition) {
        if (filterCondition == null) return "none";
        if (filterCondition.getFilter() != null) {
            return filterCondition.getFilter().getFieldName();
        }
        if (filterCondition.getFilters() != null && !filterCondition.getFilters().isEmpty()) {
            return filterCondition.getFilters().stream()
                    .map(this::formatFilterFields)
                    .filter(s -> !"none".equals(s))
                    .collect(Collectors.joining(","));
        }
        return "none";
    }

    private String formatOrderBy(List<IntentAnalysisResponse.OrderBy> orderBys) {
        if (orderBys == null || orderBys.isEmpty()) return "none";
        return orderBys.stream()
                .map(IntentAnalysisResponse.OrderBy::getField)
                .collect(Collectors.joining(","));
    }
}
