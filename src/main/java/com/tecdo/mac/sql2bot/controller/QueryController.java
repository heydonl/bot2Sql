package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import com.tecdo.mac.sql2bot.service.TextToSQLService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 查询 API
 */
@Slf4j
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final TextToSQLService textToSQLService;

    /**
     * 自然语言查询
     */
    @PostMapping
    public Result<QueryResponse> query(@RequestBody QueryRequest request) {
        try {
            log.info("Received query request: datasourceId={}, question={}",
                    request.getDatasourceId(), request.getQuestion());

            QueryResponse response = textToSQLService.processQuery(request);

            if (response.getSuccess()) {
                return Result.success(response);
            } else {
                return Result.error(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Failed to process query", e);
            return Result.error(e.getMessage());
        }
    }
}
