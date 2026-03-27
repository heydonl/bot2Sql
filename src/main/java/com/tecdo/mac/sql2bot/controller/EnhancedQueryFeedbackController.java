package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.service.QueryFeedbackService;
import com.tecdo.mac.sql2bot.service.BFSTableDiscoveryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 增强版查询反馈控制器
 * 实现需求文档中的用户反馈机制和BFS表发现流程
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/feedback")
@RequiredArgsConstructor
public class EnhancedQueryFeedbackController {

    private final QueryFeedbackService queryFeedbackService;
    private final BFSTableDiscoveryService bfsTableDiscoveryService;

    /**
     * 提交查询反馈 - 实现需求文档第二阶段流程
     * 用户根据结果点击满意或者不满意->评分结果会记录到rag中
     * ->如果用户点击不满意->则根据用户的的问题到rag里面搜索对应的表（以高召回率为基准）
     * ->根据搜索出来的表，然后再到数据库里面找到它关联的另外一个表，根据bfs两层深度找到涉及的表
     * ->然后将表，表关系,数据库，数据源，预先由专家写好的在数据库里的问答对(few-shot),用户提问的问题给到llm
     * ->由llm生成对应的sql模板，和对应的入参值,最后由程序来执行获取结果，给到用户
     * ->用户还可以打分->直到用户满意
     */
    @PostMapping("/submit")
    public Result<FeedbackResponse> submitFeedback(@RequestBody FeedbackRequest request) {
        try {
            log.info("收到用户反馈: userId={}, rating={}, queryLogId={}",
                    request.getUserId(), request.getRating(), request.getQueryLogId());

            // 1. 保存反馈评分到RAG中
            queryFeedbackService.saveFeedback(
                request.getUserId(),
                request.getConversationId(),
                request.getQueryLogId(),
                request.getTemplateId(),
                request.getQuestion(),
                request.getGeneratedSql(),
                request.getRating(),
                request.getFeedbackReason()
            );

            FeedbackResponse response = new FeedbackResponse();
            response.setSuccess(true);
            response.setMessage("反馈已保存");

            // 2. 如果用户不满意，触发第二阶段BFS流程
            if (request.getRating() == 0) {
                log.info("用户不满意，开始第二阶段BFS表发现和动态模板生成流程");

                // 执行BFS表发现和动态模板生成
                BFSTableDiscoveryService.BFSDiscoveryResult discoveryResult =
                    bfsTableDiscoveryService.discoverTablesAndGenerateTemplate(
                        request.getQuestion(),
                        null
                    );

                if (discoveryResult.isSuccess()) {
                    response.setMessage("反馈已保存，基于BFS表关联分析生成了新的查询模板");
                    response.setNewTemplateGenerated(true);
                    response.setNewTemplateId(discoveryResult.getGeneratedTemplate().getId());
                    response.setDiscoveredTableCount(discoveryResult.getDiscoveredModelIds().size());

                    log.info("BFS动态模板生成成功: newTemplateId={}, discoveredTables={}",
                            discoveryResult.getGeneratedTemplate().getId(),
                            discoveryResult.getDiscoveredModelIds().size());
                } else {
                    response.setMessage("反馈已保存，但BFS表发现失败: " + discoveryResult.getMessage());
                    response.setNewTemplateGenerated(false);

                    log.warn("BFS动态模板生成失败: {}", discoveryResult.getMessage());
                }
            }

            return Result.success(response);

        } catch (Exception e) {
            log.error("提交反馈失败", e);
            return Result.error("提交反馈失败: " + e.getMessage());
        }
    }

    /**
     * 获取模板反馈统计
     */
    @GetMapping("/template/{templateId}/stats")
    public Result<QueryFeedbackService.TemplateFeedbackStats> getTemplateFeedbackStats(
            @PathVariable Long templateId) {
        try {
            QueryFeedbackService.TemplateFeedbackStats stats =
                queryFeedbackService.getTemplateFeedbackStats(templateId);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取模板反馈统计失败", e);
            return Result.error("获取模板反馈统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户反馈历史
     */
    @GetMapping("/user/{userId}/history")
    public Result<java.util.List<FeedbackHistoryItem>> getUserFeedbackHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            // 这里需要实现获取用户反馈历史的逻辑
            // List<FeedbackHistoryItem> history = queryFeedbackService.getUserFeedbackHistory(userId, page, size);
            return Result.success(java.util.Collections.emptyList());
        } catch (Exception e) {
            log.error("获取用户反馈历史失败", e);
            return Result.error("获取用户反馈历史失败: " + e.getMessage());
        }
    }

    /**
     * 反馈请求
     */
    @Data
    public static class FeedbackRequest {
        private Long userId;
        private Long conversationId;
        private Long queryLogId;
        private Long templateId;
        private String question;
        private String generatedSql;
        private Integer rating; // 1=满意, 0=不满意
        private String feedbackReason;
    }

    /**
     * 反馈响应
     */
    @Data
    public static class FeedbackResponse {
        private boolean success;
        private String message;
        private boolean newTemplateGenerated;
        private Long newTemplateId;
        private Integer discoveredTableCount;
    }

    /**
     * 反馈历史项
     */
    @Data
    public static class FeedbackHistoryItem {
        private Long id;
        private String question;
        private String generatedSql;
        private Integer rating;
        private String feedbackReason;
        private java.time.LocalDateTime createdAt;
        private boolean newTemplateGenerated;
        private Long newTemplateId;
    }
}