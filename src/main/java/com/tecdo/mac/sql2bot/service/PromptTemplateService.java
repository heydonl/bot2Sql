package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.PromptTemplate;
import com.tecdo.mac.sql2bot.mapper.PromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 提示词模板服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    public List<PromptTemplate> listAll() {
        return promptTemplateMapper.selectAll();
    }

    public List<PromptTemplate> listActive() {
        return promptTemplateMapper.selectActive();
    }

    public List<PromptTemplate> listByCategory(String category) {
        return promptTemplateMapper.selectByCategory(category);
    }

    public PromptTemplate getById(Long id) {
        return promptTemplateMapper.selectById(id);
    }

    @Transactional
    public PromptTemplate create(PromptTemplate promptTemplate) {
        if (promptTemplate.getIsActive() == null) {
            promptTemplate.setIsActive(true);
        }
        if (promptTemplate.getPriority() == null) {
            promptTemplate.setPriority(0);
        }
        promptTemplateMapper.insert(promptTemplate);
        log.info("Created prompt template: id={}, name={}", promptTemplate.getId(), promptTemplate.getName());
        return promptTemplate;
    }

    @Transactional
    public void update(PromptTemplate promptTemplate) {
        promptTemplateMapper.update(promptTemplate);
        log.info("Updated prompt template: id={}", promptTemplate.getId());
    }

    @Transactional
    public void delete(Long id) {
        promptTemplateMapper.deleteById(id);
        log.info("Deleted prompt template: id={}", id);
    }
}
