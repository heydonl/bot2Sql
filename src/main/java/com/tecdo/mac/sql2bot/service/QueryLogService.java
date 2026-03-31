package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.dto.QueryLogFilter;
import com.tecdo.mac.sql2bot.dto.QueryLogStats;
import com.tecdo.mac.sql2bot.mapper.QueryLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryLogService {

    private final QueryLogMapper queryLogMapper;

    /**
     * 记录查询日志
     */
    @Transactional
    public Long logQuery(QueryLog queryLog) {
        queryLogMapper.insert(queryLog);
        log.info("记录查询日志成功: id={}, question={}, intent={}, executionSuccess={}",
                queryLog.getId(), queryLog.getQuestion(), queryLog.getIntent(), queryLog.getExecutionSuccess());
        return queryLog.getId();
    }

    /**
     * 更新查询评分
     */
    @Transactional
    public void updateRating(Long logId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分必须在 1-5 之间");
        }
        queryLogMapper.updateRating(logId, rating);
        log.info("更新查询评分成功: logId={}, rating={}", logId, rating);
    }

    /**
     * 标注为 few-shot 示例
     */
    @Transactional
    public void markAsLabeled(Long logId) {
        queryLogMapper.markAsLabeled(logId);
        log.info("标注为 few-shot 示例成功: logId={}", logId);
    }

    /**
     * 根据条件查询日志列表
     */
    public List<QueryLog> findByFilter(QueryLogFilter filter) {
        log.debug("查询日志列表: filter={}", filter);
        return queryLogMapper.selectList(
                filter.getIntent(),
                filter.getIsFromTemplate(),
                filter.getExecutionSuccess(),
                filter.getIsLabeled(),
                filter.getMinRating(),
                filter.getMaxRating(),
                filter.getUserId(),
                filter.getStartDate(),
                filter.getEndDate(),
                0,
                Integer.MAX_VALUE
        );
    }

    /**
     * 根据 ID 查询日志
     */
    public QueryLog findById(Long id) {
        return queryLogMapper.selectById(id);
    }

    /**
     * 获取查询统计信息
     */
    public QueryLogStats getStats() {
        log.debug("获取查询统计信息");
        return queryLogMapper.getStats();
    }

    /**
     * 根据模板ID获取最佳示例
     */
    public QueryLog getBestExampleByTemplateId(Long templateId) {
        if (templateId == null) {
            return null;
        }

        try {
            // 查找使用该模板且执行成功的查询日志，按评分或时间排序
            return queryLogMapper.findBestExampleByTemplateId(templateId);
        } catch (Exception e) {
            log.warn("获取模板示例失败: templateId={}", templateId, e);
            return null;
        }
    }

    /**
     * 获取意图分布统计
     */
    public Map<String, Long> getIntentDistribution() {
        return queryLogMapper.getIntentDistribution(null, null);
    }

    /**
     * 更新用户满意度
     */
    @Transactional
    public void updateSatisfied(Long id, Boolean satisfied) {
        queryLogMapper.updateSatisfied(id, satisfied);
        log.info("更新用户满意度: id={}, satisfied={}", id, satisfied);
    }

    /**
     * 获取最近的高评分查询示例（用于参数填充提示词）
     */
    public QueryLog getBestRecentExample(Long datasourceId) {
        List<QueryLog> results = queryLogMapper.selectBestRecentExample(datasourceId, 1);
        return results.isEmpty() ? null : results.get(0);
    }
}
