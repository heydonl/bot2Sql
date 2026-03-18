package com.tecdo.mac.sql2bot.scheduler;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.DataSource;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.mapper.ColumnDefinitionMapper;
import com.tecdo.mac.sql2bot.mapper.DataSourceMapper;
import com.tecdo.mac.sql2bot.mapper.ModelMapper;
import com.tecdo.mac.sql2bot.service.SchemaVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Schema 增量/全量索引服务
 * 负责将 model 和 column_definition 数据同步到 Redis 向量索引
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaIndexService {

    private static final String LAST_INDEX_TIME_KEY = "schema:last_index_time";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SchemaVectorStoreService schemaVectorStoreService;
    private final ModelMapper modelMapper;
    private final ColumnDefinitionMapper columnDefinitionMapper;
    private final DataSourceMapper dataSourceMapper;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 增量索引：只处理 lastIndexTime 之后有变更的 model
     */
    public void incrementalIndex() {
        log.info("开始增量索引...");
        LocalDateTime lastIndexTime = getLastIndexTime();

        List<Model> modelsToIndex;
        if (lastIndexTime == null) {
            log.info("首次执行，进行全量索引");
            modelsToIndex = modelMapper.selectAll();
        } else {
            log.info("增量索引，上次执行时间: {}", lastIndexTime);
            // 查找 model 本身有更新的
            List<Model> updatedModels = modelMapper.selectUpdatedAfter(lastIndexTime);

            // 查找 column_definition 有更新的 model（通过 modelId 去重合并）
            List<ColumnDefinition> updatedColumns = columnDefinitionMapper.selectUpdatedAfter(lastIndexTime);
            List<Long> updatedColumnModelIds = updatedColumns.stream()
                    .map(ColumnDefinition::getModelId)
                    .distinct()
                    .collect(Collectors.toList());

            // 合并两个 model 列表（按 id 去重）
            Map<Long, Model> mergedMap = new LinkedHashMap<>();
            for (Model m : updatedModels) {
                mergedMap.put(m.getId(), m);
            }
            if (!updatedColumnModelIds.isEmpty()) {
                for (Long modelId : updatedColumnModelIds) {
                    if (!mergedMap.containsKey(modelId)) {
                        Model m = modelMapper.selectById(modelId);
                        if (m != null) {
                            mergedMap.put(m.getId(), m);
                        }
                    }
                }
            }
            modelsToIndex = new ArrayList<>(mergedMap.values());
        }

        log.info("本次需要索引的 model 数量: {}", modelsToIndex.size());
        indexModels(modelsToIndex);
        updateLastIndexTime(LocalDateTime.now());
        log.info("增量索引完成");
    }

    /**
     * 全量索引：清空所有索引后重新索引全部 model
     */
    public void fullIndex() {
        log.info("开始全量索引...");
        schemaVectorStoreService.clearAll();

        List<Model> allModels = modelMapper.selectAll();
        log.info("全量索引 model 数量: {}", allModels.size());
        indexModels(allModels);
        updateLastIndexTime(LocalDateTime.now());
        log.info("全量索引完成");
    }

    /**
     * 从 Redis 读取上次执行时间
     *
     * @return LocalDateTime，null 表示从未执行
     */
    public LocalDateTime getLastIndexTime() {
        try {
            String value = redisTemplate.opsForValue().get(LAST_INDEX_TIME_KEY);
            if (value == null || value.isBlank()) {
                return null;
            }
            return LocalDateTime.parse(value, FORMATTER);
        } catch (Exception e) {
            log.warn("读取上次索引时间失败，将视为首次执行: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 批量索引 model 列表
     */
    private void indexModels(List<Model> models) {
        for (Model model : models) {
            try {
                List<ColumnDefinition> columns = columnDefinitionMapper.selectByModelId(model.getId());
                String datasourceName = getDatasourceName(model.getDatasourceId());
                schemaVectorStoreService.indexModel(model, columns, datasourceName);
            } catch (Exception e) {
                log.error("索引 model 失败，跳过: modelId={}, error={}", model.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 根据 datasourceId 获取数据源名称
     * datasourceId 为 null 时返回 "unknown"
     */
    private String getDatasourceName(Long datasourceId) {
        if (datasourceId == null) {
            return "unknown";
        }
        try {
            DataSource ds = dataSourceMapper.selectById(datasourceId);
            return ds != null ? ds.getName() : "unknown";
        } catch (Exception e) {
            log.warn("获取数据源名称失败: datasourceId={}, error={}", datasourceId, e.getMessage());
            return "unknown";
        }
    }

    /**
     * 更新 Redis 中的上次索引时间
     */
    private void updateLastIndexTime(LocalDateTime time) {
        try {
            redisTemplate.opsForValue().set(LAST_INDEX_TIME_KEY, time.format(FORMATTER));
            log.debug("已更新上次索引时间: {}", time);
        } catch (Exception e) {
            log.error("更新上次索引时间失败: {}", e.getMessage(), e);
        }
    }
}
