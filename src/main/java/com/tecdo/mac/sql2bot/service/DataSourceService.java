package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.DataSource;
import com.tecdo.mac.sql2bot.mapper.DataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * 数据源服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final DataSourceMapper dataSourceMapper;

    /**
     * 创建数据源
     */
    @Transactional
    public DataSource create(DataSource dataSource) {
        if (dataSource.getStatus() == null) {
            dataSource.setStatus("active");
        }
        dataSourceMapper.insert(dataSource);
        log.info("Created datasource: id={}, name={}", dataSource.getId(), dataSource.getName());
        return dataSource;
    }

    /**
     * 测试数据源连接
     */
    public boolean testConnection(DataSource dataSource) {
        String url = buildJdbcUrl(dataSource);
        try (Connection conn = DriverManager.getConnection(url, dataSource.getUsername(), dataSource.getPassword())) {
            log.info("Connection test successful for datasource: {}", dataSource.getName());
            return true;
        } catch (Exception e) {
            log.error("Connection test failed for datasource: {}", dataSource.getName(), e);
            return false;
        }
    }

    /**
     * 根据ID查询
     */
    public DataSource getById(Long id) {
        return dataSourceMapper.selectById(id);
    }

    /**
     * 查询所有数据源
     */
    public List<DataSource> listAll() {
        return dataSourceMapper.selectAll();
    }

    /**
     * 更新数据源
     */
    @Transactional
    public void update(DataSource dataSource) {
        dataSourceMapper.update(dataSource);
        log.info("Updated datasource: id={}", dataSource.getId());
    }

    /**
     * 删除数据源
     */
    @Transactional
    public void delete(Long id) {
        dataSourceMapper.deleteById(id);
        log.info("Deleted datasource: id={}", id);
    }

    /**
     * 构建JDBC URL
     */
    private String buildJdbcUrl(DataSource dataSource) {
        return String.format("jdbc:%s://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai",
                dataSource.getType(), dataSource.getHost(), dataSource.getPort(), dataSource.getDatabaseName());
    }
}
