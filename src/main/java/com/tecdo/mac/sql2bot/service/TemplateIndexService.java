package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模板索引服务
 * 负责为所有SQL模板创建和维护向量索引
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateIndexService implements ApplicationRunner {

    private final QueryTemplateService queryTemplateService;
    private final TemplateVectorStoreService templateVectorStoreService;

    /**
     * 应用启动时自动建立索引
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("应用启动，开始建立模板向量索引...");
        try {
            fullIndexTemplates();
            log.info("模板向量索引建立完成");
        } catch (Exception e) {
            log.error("建立模板向量索引失败", e);
        }
    }

    /**
     * 全量重建模板索引
     */
    public void fullIndexTemplates() {
        try {
            log.info("开始全量重建模板索引");

            // 清空现有索引
            templateVectorStoreService.clearAllTemplates();

            // 获取所有模板
            List<QueryTemplate> allTemplates = queryTemplateService.listAll();
            log.info("找到 {} 个模板需要建立索引", allTemplates.size());

            int successCount = 0;
            int failCount = 0;

            for (QueryTemplate template : allTemplates) {
                try {
                    // 只为有示例问题的模板建立索引
                    if (template.getExampleQuestion() != null &&
                        !template.getExampleQuestion().trim().isEmpty()) {
                        templateVectorStoreService.indexTemplate(template);
                        successCount++;
                    } else {
                        log.debug("跳过没有示例问题的模板: templateId={}", template.getId());
                    }
                } catch (Exception e) {
                    log.error("为模板建立索引失败: templateId={}", template.getId(), e);
                    failCount++;
                }
            }

            log.info("模板索引建立完成: 成功={}, 失败={}", successCount, failCount);

        } catch (Exception e) {
            log.error("全量重建模板索引失败", e);
            throw new RuntimeException("全量重建模板索引失败", e);
        }
    }

    /**
     * 为单个模板建立索引
     */
    public void indexTemplate(Long templateId) {
        try {
            QueryTemplate template = queryTemplateService.getById(templateId);
            if (template == null) {
                log.warn("模板不存在: templateId={}", templateId);
                return;
            }

            if (template.getExampleQuestion() == null ||
                template.getExampleQuestion().trim().isEmpty()) {
                log.warn("模板没有示例问题，跳过索引: templateId={}", templateId);
                return;
            }

            templateVectorStoreService.indexTemplate(template);
            log.info("为模板建立索引成功: templateId={}", templateId);

        } catch (Exception e) {
            log.error("为模板建立索引失败: templateId={}", templateId, e);
            throw new RuntimeException("为模板建立索引失败", e);
        }
    }

    /**
     * 删除模板索引
     */
    public void deleteTemplateIndex(Long templateId) {
        try {
            templateVectorStoreService.deleteTemplate(templateId);
            log.info("删除模板索引成功: templateId={}", templateId);
        } catch (Exception e) {
            log.error("删除模板索引失败: templateId={}", templateId, e);
        }
    }

    /**
     * 重建指定模板的索引
     */
    public void reindexTemplate(Long templateId) {
        try {
            // 先删除现有索引
            deleteTemplateIndex(templateId);

            // 重新建立索引
            indexTemplate(templateId);

            log.info("重建模板索引成功: templateId={}", templateId);
        } catch (Exception e) {
            log.error("重建模板索引失败: templateId={}", templateId, e);
            throw new RuntimeException("重建模板索引失败", e);
        }
    }
}