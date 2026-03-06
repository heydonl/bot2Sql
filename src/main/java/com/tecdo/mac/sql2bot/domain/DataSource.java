package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据源实体
 */
@Data
public class DataSource {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 数据源类型: mysql, postgresql
     */
    private String type;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口号
     */
    private Integer port;

    /**
     * 数据库名
     */
    private String databaseName;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码(加密存储)
     */
    private String password;

    /**
     * 额外配置(JSON格式)
     */
    private String properties;

    /**
     * 状态: active, inactive
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
