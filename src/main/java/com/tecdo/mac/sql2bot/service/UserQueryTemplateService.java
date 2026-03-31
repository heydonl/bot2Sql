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
        mapper.updateScoreOnSatisfied(id);
        log.info("更新用户模板评分（满意）: id={}", id);
    }

    public void updateScoreOnUnsatisfied(Long id) {
        mapper.updateScoreOnUnsatisfied(id);
        log.info("更新用户模板评分（不满意）: id={}", id);
    }
}
