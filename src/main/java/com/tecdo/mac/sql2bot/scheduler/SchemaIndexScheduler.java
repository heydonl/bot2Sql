package com.tecdo.mac.sql2bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.schema-index.enabled", havingValue = "true", matchIfMissing = true)
public class SchemaIndexScheduler {

    private final SchemaIndexService schemaIndexService;
    private volatile boolean running = true;

    @Scheduled(cron = "${scheduler.schema-index.cron:0 0 * * * ?}")
    public void indexSchema() {
        if (!running) return;
        try {
            log.info("开始执行 Schema 增量索引");
            schemaIndexService.incrementalIndex();
            log.info("Schema 增量索引完成");
        } catch (Exception e) {
            log.error("Schema 增量索引失败，下次执行时将重试", e);
        }
    }

    public void start() { running = true; }
    public void stop()  { running = false; }
    public boolean isRunning() { return running; }
}
