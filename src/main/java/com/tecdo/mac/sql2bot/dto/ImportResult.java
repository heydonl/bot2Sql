package com.tecdo.mac.sql2bot.dto;

import lombok.Data;

/**
 * 导入结果 DTO
 */
@Data
public class ImportResult {

    /**
     * 导入的表数量
     */
    private Integer tableCount;

    /**
     * 导入的字段数量
     */
    private Integer columnCount;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    public static ImportResult success(int tableCount, int columnCount) {
        ImportResult result = new ImportResult();
        result.setSuccess(true);
        result.setTableCount(tableCount);
        result.setColumnCount(columnCount);
        return result;
    }

    public static ImportResult error(String errorMessage) {
        ImportResult result = new ImportResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
