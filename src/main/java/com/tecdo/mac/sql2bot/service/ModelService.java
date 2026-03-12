package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.dto.ModelWithColumnsDTO;
import com.tecdo.mac.sql2bot.mapper.ColumnDefinitionMapper;
import com.tecdo.mac.sql2bot.mapper.ModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private final ModelMapper modelMapper;
    private final ColumnDefinitionMapper columnDefinitionMapper;

    /**
     * 创建模型
     */
    @Transactional
    public Model create(Model model) {
        if (model.getIsVisible() == null) {
            model.setIsVisible(true);
        }
        modelMapper.insert(model);
        log.info("Created model: id={}, tableName={}", model.getId(), model.getTableName());
        return model;
    }

    /**
     * 批量创建模型
     */
    @Transactional
    public void batchCreate(List<Model> models) {
        if (models == null || models.isEmpty()) {
            return;
        }

        // 设置默认值
        models.forEach(model -> {
            if (model.getIsVisible() == null) {
                model.setIsVisible(true);
            }
        });

        // 分批插入，每批 500 条
        int batchSize = 500;
        for (int i = 0; i < models.size(); i += batchSize) {
            int end = Math.min(i + batchSize, models.size());
            List<Model> batch = models.subList(i, end);
            modelMapper.batchInsert(batch);
            log.info("Batch inserted {} models (batch {}/{})", batch.size(), (i / batchSize) + 1, (models.size() + batchSize - 1) / batchSize);
        }

        log.info("Batch created {} models in total", models.size());
    }

    /**
     * 根据ID查询
     */
    public Model getById(Long id) {
        return modelMapper.selectById(id);
    }

    /**
     * 查询所有模型
     */
    public List<Model> listAll() {
        return modelMapper.selectAll();
    }

    /**
     * 查询所有模型及其字段
     */
    public List<ModelWithColumnsDTO> listAllWithColumns() {
        List<Model> models = modelMapper.selectAll();
        List<ModelWithColumnsDTO> result = new ArrayList<>();

        for (Model model : models) {
            List<ColumnDefinition> columns = columnDefinitionMapper.selectByModelId(model.getId());
            result.add(new ModelWithColumnsDTO(model, columns));
        }

        log.info("Loaded {} models with columns", result.size());
        return result;
    }

    /**
     * 根据数据源ID查询
     */
    public List<Model> listByDatasourceId(Long datasourceId) {
        return modelMapper.selectByDatasourceId(datasourceId);
    }

    /**
     * 查询可见的模型
     */
    public List<Model> listVisible() {
        return modelMapper.selectVisible();
    }

    /**
     * 更新模型
     */
    @Transactional
    public void update(Model model) {
        modelMapper.update(model);
        log.info("Updated model: id={}", model.getId());
    }

    /**
     * 删除模型
     */
    @Transactional
    public void delete(Long id) {
        modelMapper.deleteById(id);
        log.info("Deleted model: id={}", id);
    }
}
