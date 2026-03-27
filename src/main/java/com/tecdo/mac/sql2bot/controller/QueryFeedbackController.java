package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.service.QueryFeedbackService;
import com.tecdo.mac.sql2bot.service.BFSTableDiscoveryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 查询反馈控制器
 * 处理用户对查询结果的满意度评分和反馈
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class QueryFeedbackController {

    private final QueryFeedbackService queryFeedbackService;
    private final BFSTableDiscoveryService bfsTableDiscoveryService;
    private final com.tecdo.mac.sql2bot.service.SchemaVectorStoreService schemaVectorStoreService;

    /**
     * 提交查询反馈
     */
    @PostMapping("/submit")
    public Result<FeedbackResponse> submitFeedback(@RequestBody FeedbackRequest request) {
        try {
            log.info("收到用户反馈: userId={}, rating={}, queryLogId={}",
                    request.getUserId(), request.getRating(), request.getQueryLogId());

            // 保存反馈
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

            // 如果用户满意，将问答对存入向量索引
            if (request.getRating() == 1) {
                try {
                    schemaVectorStoreService.indexQuestionAnswer(
                        request.getQueryLogId(),
                        request.getQuestion(),
                        request.getGeneratedSql()
                    );
                    log.info("用户满意，已将问答对存入向量索引: queryLogId={}", request.getQueryLogId());
                } catch (Exception e) {
                    log.error("存入问答向量索引失败", e);
                }
            }

            // 如果用户不满意，触发BFS表发现和新模板生成
            if (request.getRating() == 0) {
                log.info("用户不满意，开始BFS表发现流程");

                BFSTableDiscoveryService.BFSDiscoveryResult discoveryResult =
                    bfsTableDiscoveryService.discoverTablesAndGenerateTemplate(
                        request.getQuestion(),
                        null
                    );

                if (discoveryResult.isSuccess()) {
                    response.setMessage("反馈已保存，已生成新的查询模板");
                    response.setNewTemplateGenerated(true);
                    response.setNewTemplateId(discoveryResult.getGeneratedTemplate().getId());
                } else {
                    response.setMessage("反馈已保存，但生成新模板失败: " + discoveryResult.getMessage());
                    response.setNewTemplateGenerated(false);
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
    }
}