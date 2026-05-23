package com.eagleeye.collector.scheduler;

import com.eagleeye.collector.service.MarginCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Triggers daily margin transaction collection at 15:30 Taipei time.
 *
 * TWSE publishes margin transaction data after the 13:30 market close.
 * 15:30 provides a safe 2-hour buffer.
 *
 * Does NOT modify CollectionScheduler or MarketIndexScheduler — independent bean.
 */
@Component
public class MarginTransactionScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarginTransactionScheduler.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final MarginTransactionService marginTransactionService;

    public MarginTransactionScheduler(MarginTransactionService marginTransactionService) {
        this.marginTransactionService = marginTransactionService;
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Taipei")
    public void collectMargin() {
        LocalDate today = LocalDate.now(TAIPEI);
        log.info("=== Margin transaction daily collection triggered for {} ===", today);
        MarginCollectionResult result = marginTransactionService.collectDate(today);
        log.info("=== Margin collection completed: {} for {} ===", result.status(), today);
    }
}
