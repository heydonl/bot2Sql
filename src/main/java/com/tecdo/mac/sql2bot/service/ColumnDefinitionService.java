package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.mapper.ColumnDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字段定义服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColumnDefinitionService {

    private final ColumnDefinitionMapper columnDefinitionMapper;

    /**
     * 创建字段定义
     */
    @Transactional
    public ColumnDefinition create(ColumnDefinition columnDefinition) {
        columnDefinitionMapper.insert(columnDefinition);
        log.info("Created column: id={}, modelId={}, columnName={}",
                columnDefinition.getId(), columnDefinition.getModelId(), columnDefinition.getColumnName());
        return columnDefinition;
    }

    /**
     * 批量创建字段定义
     */
    @Transactional
    public void batchCreate(List<ColumnDefinition> columns) {
        for (ColumnDefinition column : columns) {
            columnDefinitionMapper.insert(column);
        }
        log.info("Batch created {} columns", columns.size());
    }

    /**
     * 根据ID查询
     */
    public ColumnDefinition getById(Long id) {
        return columnDefinitionMapper.selectById(id);
    }

    /**
     * 根据模型ID查询所有字段
     */
    public List<ColumnDefinition> listByModelId(Long modelId) {
        return columnDefinitionMapper.selectByModelId(modelId);
    }

    /**
     * 根据模型ID查询所有字段（别名方法）
     */
    public List<ColumnDefinition> getByModelId(Long modelId) {
        return listByModelId(modelId);
    }

    /**
     * 根据字段类型查询
     */
    public List<ColumnDefinition> listByColumnType(Long modelId, String columnType) {
        return columnDefinitionMapper.selectByColumnType(modelId, columnType);
    }

    /**
     * 根据字段名查找所有匹配的字段定义（跨表）
     */
    public List<ColumnDefinition> listByColumnName(String columnName) {
        return columnDefinitionMapper.selectByColumnName(columnName);
    }

    /**
     * 更新字段定义
     */
    @Transactional
    public void update(ColumnDefinition columnDefinition) {
        columnDefinitionMapper.update(columnDefinition);
        log.info("Updated column: id={}", columnDefinition.getId());
    }

    /**
     * 删除字段定义
     */
    @Transactional
    public void delete(Long id) {
        columnDefinitionMapper.deleteById(id);
        log.info("Deleted column: id={}", id);
    }
}
