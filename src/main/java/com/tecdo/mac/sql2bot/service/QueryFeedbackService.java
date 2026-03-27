package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryFeedback;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询反馈服务
 * 处理用户对查询结果的满意度评分和反馈
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryFeedbackService {

    private final QueryTemplateService queryTemplateService;
    private final TemplateVectorStoreService templateVectorStoreService;

    /**
     * 保存用户反馈
     */
    public void saveFeedback(Long userId, Long conversationId, Long queryLogId,
                           Long templateId, String question, String generatedSql,
                           Integer rating, String feedbackReason) {
        try {
            QueryFeedback feedback = new QueryFeedback();
            feedback.setUserId(userId);
            feedback.setConversationId(conversationId);
            feedback.setQueryLogId(queryLogId);
            feedback.setTemplateId(templateId);
            feedback.setQuestion(question);
            feedback.setGeneratedSql(generatedSql);
            feedback.setRating(rating);
            feedback.setFeedbackReason(feedbackReason);
            feedback.setCreatedAt(LocalDateTime.now());

            // 这里需要实现数据库保存逻辑
            // feedbackMapper.insert(feedback);

            log.info("保存用户反馈: userId={}, rating={}, templateId={}",
                    userId, rating, templateId);

            // 更新模板评分
            if (templateId != null) {
                updateTemplateRating(templateId, rating);
            }

            // 如果是不满意的反馈，触发重新生成流程
            if (rating == 0) {
                handleUnsatisfiedFeedback(feedback);
            }

        } catch (Exception e) {
            log.error("保存用户反馈失败", e);
            throw new RuntimeException("保存用户反馈失败", e);
        }
    }

    /**
     * 更新模板评分
     */
    private void updateTemplateRating(Long templateId, Integer rating) {
        try {
            QueryTemplate template = queryTemplateService.getById(templateId);
            if (template == null) {
                log.warn("模板不存在: templateId={}", templateId);
                return;
            }

            // 计算新的平均评分
            int currentRatingCount = template.getRatingCount() != null ? template.getRatingCount() : 0;
            BigDecimal currentScore = template.getScore() != null ? template.getScore() : BigDecimal.ZERO;

            BigDecimal totalScore = currentScore.multiply(BigDecimal.valueOf(currentRatingCount));
            totalScore = totalScore.add(BigDecimal.valueOf(rating));

            int newRatingCount = currentRatingCount + 1;
            BigDecimal newScore = totalScore.divide(BigDecimal.valueOf(newRatingCount), 2, RoundingMode.HALF_UP);

            // 更新模板评分
            template.setScore(newScore);
            template.setRatingCount(newRatingCount);
            template.setUpdatedAt(LocalDateTime.now());

            queryTemplateService.updateTemplate(template);

            // 更新向量存储中的模板信息
            templateVectorStoreService.indexTemplate(template);

            log.info("更新模板评分: templateId={}, newScore={}, ratingCount={}",
                    templateId, newScore, newRatingCount);

        } catch (Exception e) {
            log.error("更新模板评分失败: templateId={}", templateId, e);
        }
    }

    /**
     * 处理不满意的反馈
     */
    private void handleUnsatisfiedFeedback(QueryFeedback feedback) {
        log.info("处理不满意反馈: userId={}, question={}",
                feedback.getUserId(), feedback.getQuestion());

        // 这里将触发基于BFS的表关联查找和动态模板生成
        // 具体实现将在后续的BFS服务中完成

        // 记录需要重新处理的查询
        // 可以通过消息队列或者定时任务来处理
    }

    /**
     * 获取模板的反馈统计
     */
    public TemplateFeedbackStats getTemplateFeedbackStats(Long templateId) {
        // 这里需要实现从数据库查询反馈统计的逻辑
        // List<QueryFeedback> feedbacks = feedbackMapper.findByTemplateId(templateId);

        TemplateFeedbackStats stats = new TemplateFeedbackStats();
        stats.setTemplateId(templateId);
        // 计算满意度统计

        return stats;
    }

    /**
     * 模板反馈统计
     */
    public static class TemplateFeedbackStats {
        private Long templateId;
        private int totalFeedbacks;
        private int satisfiedCount;
        private int unsatisfiedCount;
        private double satisfactionRate;

        // getters and setters
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }

        public int getTotalFeedbacks() { return totalFeedbacks; }
        public void setTotalFeedbacks(int totalFeedbacks) { this.totalFeedbacks = totalFeedbacks; }

        public int getSatisfiedCount() { return satisfiedCount; }
        public void setSatisfiedCount(int satisfiedCount) { this.satisfiedCount = satisfiedCount; }

        public int getUnsatisfiedCount() { return unsatisfiedCount; }
        public void setUnsatisfiedCount(int unsatisfiedCount) { this.unsatisfiedCount = unsatisfiedCount; }

        public double getSatisfactionRate() { return satisfactionRate; }
        public void setSatisfactionRate(double satisfactionRate) { this.satisfactionRate = satisfactionRate; }
    }
}