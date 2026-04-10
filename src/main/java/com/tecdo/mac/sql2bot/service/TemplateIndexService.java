package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import com.tecdo.mac.sql2bot.mapper.QueryTemplateMapper;
import com.tecdo.mac.sql2bot.mapper.UserQueryTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 模板索引服务
 * 负责为所有SQL模板创建和维护向量索引
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateIndexService implements ApplicationRunner {

    private final QueryTemplateMapper queryTemplateMapper;
    private final UserQueryTemplateMapper userQueryTemplateMapper;
    private final TemplateVectorStoreService templateVectorStoreService;

    /**
     * 应用启动时自动建立索引
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("应用启动，开始建立模板向量索引...");
        try {
            rebuildIndex();
            log.info("模板向量索引建立完成");
        } catch (Exception e) {
            log.error("建立模板向量索引失败", e);
        }
    }

    /**
     * 全量重建模板索引
     */
    public void rebuildIndex() {
        try {
            log.info("开始全量重建模板索引");

            // 清空现有索引
            templateVectorStoreService.clearAllTemplates();

            // 1. 索引所有系统模板
            List<QueryTemplate> systemTemplates = queryTemplateMapper.selectAll();
            log.info("找到 {} 个系统模板需要建立索引", systemTemplates.size());

            int successCount = 0;
            int failCount = 0;

            for (QueryTemplate template : systemTemplates) {
                try {
                    templateVectorStoreService.indexTemplate(template);
                    successCount++;
                } catch (Exception e) {
                    log.error("索引系统模板失败: templateId={}", template.getId(), e);
                    failCount++;
                }
            }

            // 2. 索引所有用户模板
            List<UserQueryTemplate> userTemplates = userQueryTemplateMapper.selectAll();
            log.info("找到 {} 个用户模板需要建立索引", userTemplates.size());

            for (UserQueryTemplate template : userTemplates) {
                try {
                    templateVectorStoreService.indexUserTemplate(template).get();
                    successCount++;
                } catch (Exception e) {
                    log.error("索引用户模板失败: userTemplateId={}", template.getId(), e);
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
     * 为单个系统模板建立索引
     */
    public void indexSystemTemplate(Long templateId) {
        try {
            QueryTemplate template = queryTemplateMapper.selectById(templateId);
            if (template == null) {
                log.warn("系统模板不存在: templateId={}", templateId);
                return;
            }

            templateVectorStoreService.indexTemplate(template);
            log.info("为系统模板建立索引成功: templateId={}", templateId);

        } catch (Exception e) {
            log.error("为系统模板建立索引失败: templateId={}", templateId, e);
            throw new RuntimeException("为系统模板建立索引失败", e);
        }
    }

    /**
     * 为单个用户模板建立索引
     */
    public void indexUserTemplate(Long userTemplateId) {
        try {
            UserQueryTemplate template = userQueryTemplateMapper.findById(userTemplateId);
            if (template == null) {
                log.warn("用户模板不存在: userTemplateId={}", userTemplateId);
                return;
            }

            templateVectorStoreService.indexUserTemplate(template).get();
            log.info("为用户模板建立索引成功: userTemplateId={}", userTemplateId);

        } catch (Exception e) {
            log.error("为用户模板建立索引失败: userTemplateId={}", userTemplateId, e);
            throw new RuntimeException("为用户模板建立索引失败", e);
        }
    }

    /**
     * 删除系统模板索引
     */
    public void deleteSystemTemplateIndex(Long templateId) {
        try {
            templateVectorStoreService.deleteTemplate(templateId);
            log.info("删除系统模板索引成功: templateId={}", templateId);
        } catch (Exception e) {
            log.error("删除系统模板索引失败: templateId={}", templateId, e);
        }
    }

    /**
     * 删除用户模板索引
     */
    public void deleteUserTemplateIndex(Long userTemplateId) {
        try {
            templateVectorStoreService.deleteUserTemplate(userTemplateId);
            log.info("删除用户模板索引成功: userTemplateId={}", userTemplateId);
        } catch (Exception e) {
            log.error("删除用户模板索引失败: userTemplateId={}", userTemplateId, e);
        }
    }

    /**
     * 重建指定系统模板的索引
     */
    public void reindexSystemTemplate(Long templateId) {
        try {
            deleteSystemTemplateIndex(templateId);
            indexSystemTemplate(templateId);
            log.info("重建系统模板索引成功: templateId={}", templateId);
        } catch (Exception e) {
            log.error("重建系统模板索引失败: templateId={}", templateId, e);
            throw new RuntimeException("重建系统模板索引失败", e);
        }
    }

    /**
     * 重建指定用户模板的索引
     */
    public void reindexUserTemplate(Long userTemplateId) {
        try {
            deleteUserTemplateIndex(userTemplateId);
            indexUserTemplate(userTemplateId);
            log.info("重建用户模板索引成功: userTemplateId={}", userTemplateId);
        } catch (Exception e) {
            log.error("重建用户模板索引失败: userTemplateId={}", userTemplateId, e);
            throw new RuntimeException("重建用户模板索引失败", e);
        }
    }
}
