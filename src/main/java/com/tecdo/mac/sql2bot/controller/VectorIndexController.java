package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.dto.ApiResponse;
import com.tecdo.mac.sql2bot.service.VectorRAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 向量索引管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/vector-index")
@RequiredArgsConstructor
public class VectorIndexController {

    private final VectorRAGService vectorRAGService;

    /**
     * 为所有表和字段建立索引
     */
    @PostMapping("/rebuild-all")
    public ApiResponse<String> rebuildAllIndexes() {
        try {
            log.info("Rebuilding all vector indexes");
            vectorRAGService.indexAllModelsAndColumns();
            return ApiResponse.success("All indexes rebuilt successfully");
        } catch (Exception e) {
            log.error("Failed to rebuild indexes", e);
            return ApiResponse.error("Failed to rebuild indexes: " + e.getMessage());
        }
    }

    /**
     * 为单个表建立索引
     */
    @PostMapping("/index-model/{modelId}")
    public ApiResponse<String> indexModel(@PathVariable Long modelId) {
        try {
            log.info("Indexing model: {}", modelId);
            vectorRAGService.indexModel(modelId);
            return ApiResponse.success("Model indexed successfully");
        } catch (Exception e) {
            log.error("Failed to index model: {}", modelId, e);
            return ApiResponse.error("Failed to index model: " + e.getMessage());
        }
    }

    /**
     * 删除表的索引
     */
    @DeleteMapping("/model/{modelId}")
    public ApiResponse<String> deleteModelIndex(@PathVariable Long modelId) {
        try {
            log.info("Deleting index for model: {}", modelId);
            vectorRAGService.deleteModelIndex(modelId);
            return ApiResponse.success("Model index deleted successfully");
        } catch (Exception e) {
            log.error("Failed to delete model index: {}", modelId, e);
            return ApiResponse.error("Failed to delete model index: " + e.getMessage());
        }
    }
}
