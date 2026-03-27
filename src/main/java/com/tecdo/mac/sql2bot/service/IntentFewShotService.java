package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.IntentFewShot;
import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.dto.LabelFewShotRequest;
import com.tecdo.mac.sql2bot.dto.BatchLabelRequest;
import com.tecdo.mac.sql2bot.mapper.IntentFewShotMapper;
import com.tecdo.mac.sql2bot.mapper.QueryLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentFewShotService {

    private final IntentFewShotMapper fewShotMapper;
    private final QueryLogMapper queryLogMapper;
    private final QueryLogService queryLogService;

    /**
     * 获取所有 few-shot 示例
     */
    public List<IntentFewShot> findAll(String intent, Long datasourceId) {
        log.debug("查询所有 few-shot 示例: intent={}, datasourceId={}", intent, datasourceId);
        return fewShotMapper.selectList(intent, null, 0, Integer.MAX_VALUE);
    }

    /**
     * 获取启用的 few-shot 示例
     */
    public List<IntentFewShot> findActiveExamples(String intent, Long datasourceId) {
        log.debug("查询启用的 few-shot 示例: intent={}, datasourceId={}", intent, datasourceId);
        return fewShotMapper.selectActiveExamples();
    }

    /**
     * 根据 ID 查询 few-shot
     */
    public IntentFewShot findById(Long id) {
        log.debug("根据 ID 查询 few-shot: id={}", id);
        return fewShotMapper.selectById(id);
    }

    /**
     * 创建 few-shot 示例
     */
    @Transactional
    public Long create(IntentFewShot fewShot) {
        log.info("创建 few-shot 示例: intent={}, question={}", fewShot.getIntent(), fewShot.getQuestion());
        fewShotMapper.insert(fewShot);
        log.info("创建 few-shot 示例成功: id={}", fewShot.getId());
        return fewShot.getId();
    }

    /**
     * 更新 few-shot 示例
     */
    @Transactional
    public void update(IntentFewShot fewShot) {
        log.info("更新 few-shot 示例: id={}, intent={}", fewShot.getId(), fewShot.getIntent());
        fewShotMapper.update(fewShot);
        log.info("更新 few-shot 示例成功: id={}", fewShot.getId());
    }

    /**
     * 删除 few-shot 示例
     */
    @Transactional
    public void delete(Long id) {
        log.info("删除 few-shot 示例: id={}", id);
        fewShotMapper.deleteById(id);
        log.info("删除 few-shot 示例成功: id={}", id);
    }

    /**
     * 切换 few-shot 启用状态
     */
    @Transactional
    public void toggleActive(Long id, boolean isActive) {
        log.info("切换 few-shot 启用状态: id={}, isActive={}", id, isActive);
        fewShotMapper.toggleActive(id);
        log.info("切换 few-shot 启用状态成功: id={}", id);
    }

    /**
     * 从查询日志创建 few-shot（支持修正意图）
     */
    @Transactional
    public Long labelFromQueryLog(Long queryLogId, LabelFewShotRequest request) {
        log.info("从查询日志创建 few-shot: queryLogId={}, targetIntent={}", queryLogId, request.getTargetIntent());

        // 查询日志
        QueryLog queryLog = queryLogMapper.selectById(queryLogId);
        if (queryLog == null) {
            throw new IllegalArgumentException("查询日志不存在: id=" + queryLogId);
        }

        // 创建 IntentFewShot 对象
        IntentFewShot fewShot = new IntentFewShot();
        fewShot.setIntent(request.getTargetIntent() != null ? request.getTargetIntent() : queryLog.getIntent());
        fewShot.setQuestion(queryLog.getQuestion());
        fewShot.setIntentJson(request.getCorrectedIntentJson() != null ? request.getCorrectedIntentJson() : queryLog.getIntentJson());
        fewShot.setSkeleton(queryLog.getSkeleton());
        fewShot.setIsActive(true);
        fewShot.setDatasourceId(queryLog.getDatasourceId());
        fewShot.setCreatedBy(request.getCreatedBy());

        // 插入数据库
        fewShotMapper.insert(fewShot);

        // 标记日志已标注
        queryLogService.markAsLabeled(queryLogId);

        log.info("从查询日志创建 few-shot 成功: fewShotId={}, queryLogId={}", fewShot.getId(), queryLogId);
        return fewShot.getId();
    }

    /**
     * 批量标注 few-shot
     */
    @Transactional
    public List<Long> batchLabel(BatchLabelRequest request) {
        log.info("批量标注 few-shot: queryLogIds={}, count={}", request.getQueryLogIds(), request.getQueryLogIds().size());

        List<Long> fewShotIds = new ArrayList<>();
        for (Long queryLogId : request.getQueryLogIds()) {
            LabelFewShotRequest labelRequest = new LabelFewShotRequest();
            labelRequest.setCreatedBy(request.getCreatedBy());

            Long fewShotId = labelFromQueryLog(queryLogId, labelRequest);
            fewShotIds.add(fewShotId);
        }

        log.info("批量标注 few-shot 成功: count={}", fewShotIds.size());
        return fewShotIds;
    }

    /**
     * 获取Few-Shot示例（用于BFS动态模板生成）
     */
    public String getFewShotExamples(Long datasourceId, String question) {
        try {
            List<IntentFewShot> examples = fewShotMapper.selectActiveExamples();

            if (examples.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("以下是相关的问答示例：\n\n");

            for (IntentFewShot example : examples) {
                // 过滤数据源相关的示例
                if (datasourceId != null && example.getDatasourceId() != null &&
                    !datasourceId.equals(example.getDatasourceId())) {
                    continue;
                }

                sb.append("问题: ").append(example.getQuestion()).append("\n");
                if (example.getIntentJson() != null) {
                    sb.append("答案: ").append(example.getIntentJson()).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("获取Few-Shot示例失败", e);
            return "";
        }
    }
}
