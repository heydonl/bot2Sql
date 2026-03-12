package com.tecdo.mac.sql2bot.dto;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.Model;
import lombok.Data;

import java.util.List;

/**
 * 表模型及其字段定义 DTO
 */
@Data
public class ModelWithColumnsDTO {
    private Model model;
    private List<ColumnDefinition> columns;

    public ModelWithColumnsDTO() {
    }

    public ModelWithColumnsDTO(Model model, List<ColumnDefinition> columns) {
        this.model = model;
        this.columns = columns;
    }
}
