package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import com.tecdo.mac.sql2bot.dto.RateQueryRequest;
import com.tecdo.mac.sql2bot.dto.intent.IntentAnalysisRequest;
import com.tecdo.mac.sql2bot.dto.intent.IntentAnalysisResponse;
import com.tecdo.mac.sql2bot.service.IntentAnalysisService;
import com.tecdo.mac.sql2bot.service.QueryLogService;
import com.tecdo.mac.sql2bot.service.QueryTemplateService;
import com.tecdo.mac.sql2bot.service.TextToSQLService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 查询 API
 */
@Slf4j
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final TextToSQLService textToSQLService;
    private final IntentAnalysisService intentAnalysisService;
    private final QueryLogService queryLogService;
    private final QueryTemplateService queryTemplateService;

    /**
     * 自然语言查询
     */
    @PostMapping
    public Result<QueryResponse> query(@RequestBody QueryRequest request) {
        try {
            log.info("Received query request: datasourceId={}, question={}",
                    request.getDatasourceId(), request.getQuestion());

            QueryResponse response = textToSQLService.processQuery(request);

            if (response.getSuccess()) {
                return Result.success(response);
            } else {
                return Result.error(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Failed to process query", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 对查询结果评分
     */
    @PostMapping("/rate")
    public Result<Void> rateQuery(@RequestBody RateQueryRequest request) {
        try {
            log.info("收到评分请求: queryLogId={}, score={}", request.getQueryLogId(), request.getScore());

            // 1. 更新查询日志评分
            queryLogService.updateRating(request.getQueryLogId(), request.getScore());

            // 2. 如果查询使用了模板,更新模板评分
            QueryLog queryLog = queryLogService.findById(request.getQueryLogId());
            if (queryLog != null && queryLog.getTemplateId() != null) {
                queryTemplateService.updateRating(queryLog.getTemplateId(), request.getScore());
                log.info("已更新模板评分: templateId={}", queryLog.getTemplateId());
            }

            return Result.success(null);
        } catch (Exception e) {
            log.error("评分失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 意图分析端点（调试用）
     */
    @PostMapping("/analyze-intent")
    public Result<IntentAnalysisResponse> analyzeIntent(@RequestBody IntentAnalysisRequest request) {
        try {
            log.info("收到意图分析请求: question={}", request.getQuestion());
            IntentAnalysisResponse response = intentAnalysisService.analyzeIntent(request);
            log.info("意图分析完成: intent={}, skeleton={}", response.getIntent(), response.getSkeleton());
            return Result.success(response);
        } catch (Exception e) {
            log.error("意图分析失败", e);
            return Result.error("意图分析失败: " + e.getMessage());
        }
    }
}