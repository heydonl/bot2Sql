package com.tecdo.mac.sql2bot.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchLabelRequest {
    private List<Long> queryLogIds;
    private Long createdBy;
}
