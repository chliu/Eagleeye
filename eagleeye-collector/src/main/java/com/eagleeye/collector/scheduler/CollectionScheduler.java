package com.eagleeye.collector.scheduler;

import com.eagleeye.collector.service.CollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Triggers daily data collection at 14:30 Taipei time (Asia/Taipei).
 *
 * TAIFEX publishes institutional OI data at ~14:30 after the 13:45 market close.
 */
@Component
public class CollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectionScheduler.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final CollectionService collectionService;

    public CollectionScheduler(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /**
     * Runs Monday–Friday at 14:30 Taipei time.
     * Cron format: second minute hour day month weekday
     */
    @Scheduled(cron = "0 30 14 * * MON-FRI", zone = "Asia/Taipei")
    public void collectDailyData() {
        LocalDate today = LocalDate.now(TAIPEI);
        log.info("=== Daily collection triggered for {} ===", today);
        collectionService.collectAll(today);
        log.info("=== Daily collection completed for {} ===", today);
    }
}
