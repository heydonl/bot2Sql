package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.Glossary;
import com.tecdo.mac.sql2bot.service.GlossaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 术语库管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/glossary")
@RequiredArgsConstructor
public class GlossaryController {

    private final GlossaryService glossaryService;

    @GetMapping
    public Result<List<Glossary>> listAll() {
        try {
            List<Glossary> list = glossaryService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list glossary", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/active")
    public Result<List<Glossary>> listActive() {
        try {
            List<Glossary> list = glossaryService.listActive();
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list active glossary", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/category/{category}")
    public Result<List<Glossary>> listByCategory(@PathVariable String category) {
        try {
            List<Glossary> list = glossaryService.listByCategory(category);
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list glossary by category", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/search")
    public Result<List<Glossary>> search(@RequestParam String keyword) {
        try {
            List<Glossary> list = glossaryService.search(keyword);
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to search glossary", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<Glossary> getById(@PathVariable Long id) {
        try {
            Glossary glossary = glossaryService.getById(id);
            if (glossary == null) {
                return Result.error(404, "Glossary not found");
            }
            return Result.success(glossary);
        } catch (Exception e) {
            log.error("Failed to get glossary", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    public Result<Glossary> create(@RequestBody Glossary glossary) {
        try {
            Glossary created = glossaryService.create(glossary);
            return Result.success(created);
        } catch (Exception e) {
            log.error("Failed to create glossary", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Glossary glossary) {
        try {
            glossary.setId(id);
            glossaryService.update(glossary);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to update glossary", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            glossaryService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to delete glossary", e);
            return Result.error(e.getMessage());
        }
    }
}
