package com.tecdo.mac.sql2bot.dto;

import lombok.Data;

@Data
public class TemplateParameter {
    private String name;
    private String type;
    private Boolean required;
    private String sourceField;
    private String format;
    private String defaultValue;
}
