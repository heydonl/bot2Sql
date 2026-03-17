package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.IntentFewShot;
import com.tecdo.mac.sql2bot.service.IntentFewShotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Few-Shot 示例管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/few-shots")
@RequiredArgsConstructor
public class FewShotController {

    private final IntentFewShotService fewShotService;

    @GetMapping
    public Result<List<IntentFewShot>> listAll(
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) Long datasourceId) {
        try {
            List<IntentFewShot> list = fewShotService.findAll(intent, datasourceId);
            return Result.success(list);
        } catch (Exception e) {
            log.error("获取 few-shot 列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    public Result<Long> create(@RequestBody IntentFewShot fewShot) {
        try {
            Long id = fewShotService.create(fewShot);
            return Result.success(id);
        } catch (Exception e) {
            log.error("创建 few-shot 失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IntentFewShot fewShot) {
        try {
            fewShot.setId(id);
            fewShotService.update(fewShot);
            return Result.success();
        } catch (Exception e) {
            log.error("更新 few-shot 失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            fewShotService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除 few-shot 失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggleActive(@PathVariable Long id, @RequestParam boolean isActive) {
        try {
            fewShotService.toggleActive(id, isActive);
            return Result.success();
        } catch (Exception e) {
            log.error("切换 few-shot 状态失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }
}
