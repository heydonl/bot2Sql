package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.PromptTemplate;
import com.tecdo.mac.sql2bot.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词模板管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @GetMapping
    public Result<List<PromptTemplate>> listAll() {
        try {
            List<PromptTemplate> list = promptTemplateService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list prompt templates", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/active")
    public Result<List<PromptTemplate>> listActive() {
        try {
            List<PromptTemplate> list = promptTemplateService.listActive();
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list active prompt templates", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/category/{category}")
    public Result<List<PromptTemplate>> listByCategory(@PathVariable String category) {
        try {
            List<PromptTemplate> list = promptTemplateService.listByCategory(category);
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list prompt templates by category", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<PromptTemplate> getById(@PathVariable Long id) {
        try {
            PromptTemplate promptTemplate = promptTemplateService.getById(id);
            if (promptTemplate == null) {
                return Result.error(404, "Prompt template not found");
            }
            return Result.success(promptTemplate);
        } catch (Exception e) {
            log.error("Failed to get prompt template", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    public Result<PromptTemplate> create(@RequestBody PromptTemplate promptTemplate) {
        try {
            PromptTemplate created = promptTemplateService.create(promptTemplate);
            return Result.success(created);
        } catch (Exception e) {
            log.error("Failed to create prompt template", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PromptTemplate promptTemplate) {
        try {
            promptTemplate.setId(id);
            promptTemplateService.update(promptTemplate);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to update prompt template", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            promptTemplateService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to delete prompt template", e);
            return Result.error(e.getMessage());
        }
    }
}
