package com.eagleeye.collector.scheduler;

import com.eagleeye.collector.service.InstitutionalFlowResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Triggers daily institutional flow collection at 15:30 Taipei time.
 *
 * TWSE publishes BFI82U data after the 13:30 market close.
 * 15:30 provides a safe 2-hour buffer.
 *
 * Does NOT modify CollectionScheduler or MarginTransactionScheduler — independent bean.
 */
@Component
public class InstitutionalFlowScheduler {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalFlowScheduler.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final InstitutionalFlowService institutionalFlowService;

    public InstitutionalFlowScheduler(InstitutionalFlowService institutionalFlowService) {
        this.institutionalFlowService = institutionalFlowService;
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Taipei")
    public void collect() {
        LocalDate today = LocalDate.now(TAIPEI);
        log.info("=== Institutional flow daily collection triggered for {} ===", today);
        try {
            InstitutionalFlowResult result = institutionalFlowService.collectDate(today);
            log.info("=== Institutional flow collection completed: {} for {} ===", result.status(), today);
        } catch (Exception e) {
            log.error("Institutional flow daily collection failed for {}: {}", today, e.getMessage(), e);
        }
    }
}
