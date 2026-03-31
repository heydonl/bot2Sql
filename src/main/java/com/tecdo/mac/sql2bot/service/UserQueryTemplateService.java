package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import com.tecdo.mac.sql2bot.mapper.UserQueryTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryTemplateService {

    private final UserQueryTemplateMapper mapper;

    public UserQueryTemplate findByQuestionAndSql(String question, String sql) {
        return mapper.findByQuestionAndSql(question, sql);
    }

    public UserQueryTemplate create(String question, String sql, Long datasourceId) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        if (datasourceId == null) {
            throw new IllegalArgumentException("数据源 ID 不能为空");
        }

        UserQueryTemplate template = new UserQueryTemplate();
        template.setQuestion(question);
        template.setGeneratedSql(sql);
        template.setDatasourceId(datasourceId);
        mapper.insert(template);
        log.info("创建用户查询模板: id={}, question={}", template.getId(), question);
        return template;
    }

    public UserQueryTemplate findById(Long id) {
        return mapper.findById(id);
    }

    public void updateScoreOnSatisfied(Long id) {
        int updateCount = mapper.updateScoreOnSatisfied(id);
        if (updateCount == 0) {
            log.warn("更新用户模板评分失败，记录不存在: id={}", id);
        } else {
            log.info("更新用户模板评分（满意）: id={}, 更新记录数={}", id, updateCount);
        }
    }

    public void updateScoreOnUnsatisfied(Long id) {
        int updateCount = mapper.updateScoreOnUnsatisfied(id);
        if (updateCount == 0) {
            log.warn("更新用户模板评分失败，记录不存在: id={}", id);
        } else {
            log.info("更新用户模板评分（不满意）: id={}, 更新记录数={}", id, updateCount);
        }
    }
}
