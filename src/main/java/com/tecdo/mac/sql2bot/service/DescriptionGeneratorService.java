package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.mapper.ColumnDefinitionMapper;
import com.tecdo.mac.sql2bot.mapper.ModelMapper;
import com.tecdo.mac.sql2bot.scheduler.SchemaIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 描述生成服务
 * 负责批量为 model 表中所有表自动生成中文业务描述
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DescriptionGeneratorService {

    // Redis Key 定义
    private static final String REDIS_KEY_RUNNING = "schema:desc_gen:running";
    private static final String REDIS_KEY_TOTAL = "schema:desc_gen:total";
    private static final String REDIS_KEY_DONE = "schema:desc_gen:done";
    private static final String REDIS_KEY_FAILED = "schema:desc_gen:failed";

    // 依赖注入
    private final ModelMapper modelMapper;
    private final ColumnDefinitionMapper columnDefinitionMapper;
    private final AIService aiService;
    private final SchemaIndexService schemaIndexService;
    private final JedisPooled jedisPooled;

    // 线程池
    private ExecutorService executor;

    /**
     * 初始化固定线程池（5个线程）
     */
    @PostConstruct
    public void init() {
        executor = Executors.newFixedThreadPool(5);
        log.info("DescriptionGeneratorService 线程池已初始化，线程数: 5");
    }

    /**
     * 优雅关闭线程池
     */
    @PreDestroy
    public void destroy() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            log.info("DescriptionGeneratorService 线程池已关闭");
        }
    }

    /**
     * 启动批量生成描述任务
     *
     * @return 启动状态和总数
     */
    public Map<String, Object> generateAll() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 互斥锁控制（Redis SETNX）
            String lockResult = jedisPooled.set(REDIS_KEY_RUNNING, "true",
                SetParams.setParams().nx().ex(7200)); // 2小时过期

            if (lockResult == null) {
                // 锁已存在，说明任务正在运行
                result.put("status", "running");
                result.put("message", "描述生成任务正在运行中，请稍后再试");
                return result;
            }

            // 获取所有 model 记录
            List<Model> allModels = modelMapper.selectAll();
            int total = allModels.size();

            // 重置 Redis 进度计数器
            jedisPooled.set(REDIS_KEY_TOTAL, String.valueOf(total),
                SetParams.setParams().ex(86400)); // 24小时过期
            jedisPooled.set(REDIS_KEY_DONE, "0",
                SetParams.setParams().ex(86400));
            jedisPooled.set(REDIS_KEY_FAILED, "0",
                SetParams.setParams().ex(86400));

            log.info("开始批量生成描述任务，总数: {}", total);

            if (total == 0) {
                // 没有数据，直接完成
                jedisPooled.del(REDIS_KEY_RUNNING);
                result.put("status", "started");
                result.put("total", 0);
                return result;
            }

            // 异步提交批量处理任务
            executor.submit(() -> {
                try {
                    processBatch(allModels);
                } finally {
                    // 确保释放锁
                    jedisPooled.del(REDIS_KEY_RUNNING);
                }
            });

            result.put("status", "started");
            result.put("total", total);
            return result;

        } catch (Exception e) {
            log.error("启动描述生成任务失败", e);
            // 确保释放锁
            jedisPooled.del(REDIS_KEY_RUNNING);
            result.put("status", "error");
            result.put("message", "启动任务失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 获取生成进度
     *
     * @return 进度信息
     */
    public Map<String, Object> getProgress() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 检查是否正在运行
            String running = jedisPooled.get(REDIS_KEY_RUNNING);
            result.put("running", running != null);

            // 获取进度数据，如果 key 不存在则返回默认值
            String totalStr = jedisPooled.get(REDIS_KEY_TOTAL);
            String doneStr = jedisPooled.get(REDIS_KEY_DONE);
            String failedStr = jedisPooled.get(REDIS_KEY_FAILED);

            int total = 0;
            int done = 0;
            int failed = 0;

            // 安全解析数字，处理格式错误
            try {
                total = totalStr != null ? Integer.parseInt(totalStr) : 0;
            } catch (NumberFormatException e) {
                log.warn("解析 total 值失败: {}", totalStr);
            }

            try {
                done = doneStr != null ? Integer.parseInt(doneStr) : 0;
            } catch (NumberFormatException e) {
                log.warn("解析 done 值失败: {}", doneStr);
            }

            try {
                failed = failedStr != null ? Integer.parseInt(failedStr) : 0;
            } catch (NumberFormatException e) {
                log.warn("解析 failed 值失败: {}", failedStr);
            }

            // 处理异常情况：done 不能大于 total
            if (done > total && total > 0) {
                log.warn("检测到异常数据: done({}) > total({}), 将 done 调整为 total", done, total);
                done = total;
            }

            // 处理负数值
            total = Math.max(0, total);
            done = Math.max(0, done);
            failed = Math.max(0, failed);

            result.put("total", total);
            result.put("done", done);
            result.put("failed", failed);

            // 计算百分比，避免除零错误
            int percent = total == 0 ? 0 : Math.min(100, (int)(done * 100 / total));
            result.put("percent", percent);

            // 调试日志记录查询到的原始数据
            log.debug("进度查询结果: running={}, total={}, done={}, failed={}, percent={}",
                running != null, total, done, failed, percent);

            return result;

        } catch (Exception e) {
            log.error("获取进度信息失败", e);
            // 返回默认安全值
            result.put("running", false);
            result.put("total", 0);
            result.put("done", 0);
            result.put("failed", 0);
            result.put("percent", 0);
            return result;
        }
    }

    /**
     * 批量处理所有 model
     *
     * @param models 要处理的 model 列表
     */
    private void processBatch(List<Model> models) {
        log.info("开始批量处理 {} 个 model", models.size());

        // 使用异步任务处理所有 model
        processAllModels(models);

        log.info("批量描述生成任务完成");
    }

    /**
     * 异步处理所有 model 记录
     *
     * @param models 要处理的 model 列表
     */
    private void processAllModels(List<Model> models) {
        int totalModels = models.size();
        log.info("开始异步处理 {} 个 model", totalModels);

        // 为每个 model 提交异步任务
        for (int i = 0; i < totalModels; i++) {
            final Model model = models.get(i);
            final int index = i + 1;

            executor.submit(() -> {
                try {
                    processModel(model);

                    // 每处理10个记录记录一次进度
                    if (index % 10 == 0 || index == totalModels) {
                        log.info("处理进度: {}/{}", index, totalModels);
                    }

                    // 检查是否所有任务都完成
                    checkAndCompleteTask(totalModels);

                } catch (Exception e) {
                    log.error("异步处理 model 失败: modelId={}, tableName={}",
                        model.getId(), model.getTableName(), e);

                    // 更新失败计数
                    try {
                        jedisPooled.incr(REDIS_KEY_DONE);
                        jedisPooled.incr(REDIS_KEY_FAILED);
                    } catch (Exception redisE) {
                        log.error("更新 Redis 计数器失败", redisE);
                    }

                    // 检查是否所有任务都完成
                    checkAndCompleteTask(totalModels);
                }
            });
        }
    }

    /**
     * 检查任务是否全部完成，如果完成则触发后续处理
     *
     * @param totalModels 总模型数量
     */
    private synchronized void checkAndCompleteTask(int totalModels) {
        try {
            String doneStr = jedisPooled.get(REDIS_KEY_DONE);
            int done = doneStr != null ? Integer.parseInt(doneStr) : 0;

            if (done >= totalModels) {
                // 所有任务完成，触发全量索引
                log.info("所有 model 处理完成，开始触发全量索引");

                try {
                    schemaIndexService.fullIndex();
                    log.info("全量索引完成");
                } catch (Exception e) {
                    log.error("触发全量索引失败", e);
                }

                // 清理运行状态锁
                jedisPooled.del(REDIS_KEY_RUNNING);
                log.info("描述生成任务完全完成，已清理运行状态锁");

                // 记录最终统计
                String failedStr = jedisPooled.get(REDIS_KEY_FAILED);
                int failed = failedStr != null ? Integer.parseInt(failedStr) : 0;
                int success = done - failed;

                log.info("任务完成统计 - 总数: {}, 成功: {}, 失败: {}",
                    totalModels, success, failed);
            }
        } catch (Exception e) {
            log.error("检查任务完成状态失败", e);
        }
    }

    /**
     * 处理单个 model 的描述生成
     *
     * @param model 要处理的 model
     */
    private void processModel(Model model) {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("开始处理 model: {}", model.getTableName());

            // 先查询确保 model 仍然存在
            Model currentModel = modelMapper.selectById(model.getId());
            if (currentModel == null) {
                log.warn("Model 已被删除，跳过: modelId={}", model.getId());
                updateRedisCounters(true, false);
                return;
            }

            // 获取字段信息
            List<ColumnDefinition> columns = null;
            try {
                columns = columnDefinitionMapper.selectByModelId(model.getId());
            } catch (Exception e) {
                log.warn("获取字段信息失败，继续处理: modelId={}, error={}",
                    model.getId(), e.getMessage());
            }

            // 构建 prompt
            String[] prompts = buildPrompt(currentModel, columns);
            String systemPrompt = prompts[0];
            String userPrompt = prompts[1];

            log.debug("为 model {} 构建的 prompt 长度: system={}, user={}",
                model.getTableName(), systemPrompt.length(), userPrompt.length());

            // 调用 AI 服务生成描述
            String generatedDescription = null;
            try {
                generatedDescription = aiService.generateSQL(systemPrompt, userPrompt);
            } catch (Exception e) {
                log.error("AI 服务调用失败: modelId={}, tableName={}, error={}",
                    model.getId(), model.getTableName(), e.getMessage());
                updateRedisCounters(true, true);
                return;
            }

            // 验证生成的描述
            if (!isValidDescription(generatedDescription)) {
                log.error("AI 服务返回无效描述: modelId={}, tableName={}, description='{}'",
                    model.getId(), model.getTableName(), generatedDescription);
                updateRedisCounters(true, true);
                return;
            }

            // 清理和验证描述内容
            String cleanedDescription = cleanDescription(generatedDescription);
            if (!isDescriptionLengthValid(cleanedDescription)) {
                log.warn("生成的描述长度不合理: modelId={}, tableName={}, length={}, description='{}'",
                    model.getId(), model.getTableName(), cleanedDescription.length(), cleanedDescription);
            }

            // 更新数据库
            try {
                currentModel.setDescription(cleanedDescription);
                int updateResult = modelMapper.update(currentModel);

                if (updateResult > 0) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("成功生成并更新描述: modelId={}, tableName={}, duration={}ms, description='{}'",
                        model.getId(), model.getTableName(), duration, cleanedDescription);
                    updateRedisCounters(true, false);
                } else {
                    log.error("更新数据库失败，影响行数为0: modelId={}, tableName={}",
                        model.getId(), model.getTableName());
                    updateRedisCounters(true, true);
                }
            } catch (Exception e) {
                log.error("数据库更新异常: modelId={}, tableName={}, error={}",
                    model.getId(), model.getTableName(), e.getMessage(), e);
                updateRedisCounters(true, true);
            }

        } catch (Exception e) {
            log.error("处理 model 发生未预期异常: modelId={}, tableName={}, error={}",
                model.getId(), model.getTableName(), e.getMessage(), e);
            updateRedisCounters(true, true);
        }
    }

    /**
     * 原子性更新 Redis 计数器
     *
     * @param incrementDone 是否增加完成计数
     * @param incrementFailed 是否增加失败计数
     */
    private void updateRedisCounters(boolean incrementDone, boolean incrementFailed) {
        try {
            if (incrementDone) {
                jedisPooled.incr(REDIS_KEY_DONE);
            }
            if (incrementFailed) {
                jedisPooled.incr(REDIS_KEY_FAILED);
            }
        } catch (Exception e) {
            log.error("更新 Redis 计数器失败: incrementDone={}, incrementFailed={}, error={}",
                incrementDone, incrementFailed, e.getMessage());
        }
    }

    /**
     * 验证生成的描述是否有效
     *
     * @param description 生成的描述
     * @return 是否有效
     */
    private boolean isValidDescription(String description) {
        return description != null &&
               !description.trim().isEmpty() &&
               description.trim().length() >= 10; // 至少10个字符
    }

    /**
     * 清理描述内容
     *
     * @param description 原始描述
     * @return 清理后的描述
     */
    private String cleanDescription(String description) {
        if (description == null) {
            return "";
        }

        // 去除首尾空白
        String cleaned = description.trim();

        // 去除可能的前缀（如"描述："、"业务描述："等）
        cleaned = cleaned.replaceFirst("^(描述：|业务描述：|表描述：|说明：)", "");

        // 去除多余的换行和空格
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }

    /**
     * 验证描述长度是否合理
     *
     * @param description 描述内容
     * @return 长度是否合理
     */
    private boolean isDescriptionLengthValid(String description) {
        if (description == null) {
            return false;
        }
        int length = description.length();
        return length >= 50 && length <= 600; // 50-600字符范围
    }

    /**
     * 构建 AI 提示词
     *
     * @param model 表模型
     * @param columns 字段列表
     * @return [systemPrompt, userPrompt]
     */
    private String[] buildPrompt(Model model, List<ColumnDefinition> columns) {
        String systemPrompt = "你是一个数据库业务分析专家。根据以下表结构信息，生成一段简洁的中文业务描述（200-300字），" +
                             "描述该表的业务用途、存储的核心数据和主要使用场景。只输出描述文字，不要加任何前缀或解释。";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("表名: ").append(model.getTableName()).append("\n");
        userPrompt.append("字段: ");

        if (columns == null || columns.isEmpty()) {
            userPrompt.append("（无字段信息）");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                ColumnDefinition col = columns.get(i);
                if (i > 0) userPrompt.append(", ");
                userPrompt.append(col.getColumnName());
                if (col.getColumnType() != null) {
                    userPrompt.append("(").append(col.getColumnType()).append(")");
                }
            }
        }

        return new String[]{systemPrompt, userPrompt.toString()};
    }

}