package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.mapper.ModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模型服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private final ModelMapper modelMapper;

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
