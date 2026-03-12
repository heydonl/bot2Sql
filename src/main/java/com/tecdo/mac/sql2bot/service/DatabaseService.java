package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.Database;
import com.tecdo.mac.sql2bot.mapper.DatabaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseService {

    private final DatabaseMapper databaseMapper;

    /**
     * 创建或获取数据库记录
     */
    @Transactional
    public Database getOrCreate(Long datasourceId, String databaseName) {
        // 先查询是否已存在
        Database existing = databaseMapper.selectByDatasourceIdAndName(datasourceId, databaseName);
        if (existing != null) {
            return existing;
        }

        // 不存在则创建
        Database database = new Database();
        database.setDatasourceId(datasourceId);
        database.setDatabaseName(databaseName);
        database.setDisplayName(databaseName);
        databaseMapper.insert(database);

        log.info("Created database: datasourceId={}, databaseName={}, id={}",
                datasourceId, databaseName, database.getId());
        return database;
    }

    /**
     * 批量获取或创建数据库记录
     * @return Map<databaseName, Database>
     */
    @Transactional
    public java.util.Map<String, Database> batchGetOrCreate(Long datasourceId, List<String> databaseNames) {
        if (databaseNames == null || databaseNames.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        // 去重
        List<String> uniqueNames = databaseNames.stream().distinct().toList();

        // 批量查询已存在的
        List<Database> existingDatabases = databaseMapper.selectByDatasourceIdAndNames(datasourceId, uniqueNames);
        java.util.Map<String, Database> resultMap = new java.util.HashMap<>();
        existingDatabases.forEach(db -> resultMap.put(db.getDatabaseName(), db));

        // 找出不存在的
        List<String> missingNames = uniqueNames.stream()
                .filter(name -> !resultMap.containsKey(name))
                .toList();

        // 批量创建不存在的
        if (!missingNames.isEmpty()) {
            List<Database> databasesToCreate = new ArrayList<>();
            for (String name : missingNames) {
                Database database = new Database();
                database.setDatasourceId(datasourceId);
                database.setDatabaseName(name);
                database.setDisplayName(name);
                databasesToCreate.add(database);
            }

            databaseMapper.batchInsert(databasesToCreate);
            databasesToCreate.forEach(db -> resultMap.put(db.getDatabaseName(), db));

            log.info("Batch created {} databases for datasourceId={}", databasesToCreate.size(), datasourceId);
        }

        return resultMap;
    }

    /**
     * 根据数据源ID查询所有数据库
     */
    public List<Database> listByDatasourceId(Long datasourceId) {
        return databaseMapper.selectByDatasourceId(datasourceId);
    }

    /**
     * 根据ID查询
     */
    public Database getById(Long id) {
        return databaseMapper.selectById(id);
    }

    /**
     * 更新数据库记录
     */
    @Transactional
    public void update(Database database) {
        databaseMapper.update(database);
        log.info("Updated database: id={}", database.getId());
    }

    /**
     * 删除数据库记录
     */
    @Transactional
    public void delete(Long id) {
        databaseMapper.deleteById(id);
        log.info("Deleted database: id={}", id);
    }
}
