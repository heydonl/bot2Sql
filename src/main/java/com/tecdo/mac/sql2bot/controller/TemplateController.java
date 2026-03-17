package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.mapper.QueryTemplateMapper;
import com.tecdo.mac.sql2bot.mapper.QueryTemplateMapper.QueryTemplateStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 查询模板管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final QueryTemplateMapper templateMapper;

    /**
     * 查询模板列表
     * @param intent 意图过滤（可选）
     * @param entity 实体过滤（可选）
     * @return 模板列表
     */
    @GetMapping
    public Result<List<QueryTemplate>> listTemplates(
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) String entity) {
        try {
            log.info("查询模板列表: intent={}, entity={}", intent, entity);
            List<QueryTemplate> templates = templateMapper.selectList(intent, entity, 0, 1000);
            return Result.success(templates);
        } catch (Exception e) {
            log.error("查询模板列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取模板详情
     * @param id 模板ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    public Result<QueryTemplate> getTemplate(@PathVariable Long id) {
        try {
            log.info("获取模板详情: id={}", id);
            QueryTemplate template = templateMapper.selectById(id);
            if (template == null) {
                return Result.error(404, "模板不存在");
            }
            return Result.success(template);
        } catch (Exception e) {
            log.error("获取模板详情失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除模板
     * @param id 模板ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        try {
            log.info("删除模板: id={}", id);
            int rows = templateMapper.deleteById(id);
            if (rows == 0) {
                return Result.error(404, "模板不存在");
            }
            return Result.success();
        } catch (Exception e) {
            log.error("删除模板失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取模板统计信息
     * @return 统计信息
     */
    @GetMapping("/stats")
    public Result<QueryTemplateStats> getStats() {
        try {
            log.info("获取模板统计信息");
            QueryTemplateStats stats = templateMapper.getStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取模板统计信息失败", e);
            return Result.error(e.getMessage());
        }
    }
}
