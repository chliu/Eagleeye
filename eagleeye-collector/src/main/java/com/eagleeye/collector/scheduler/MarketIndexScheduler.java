package com.eagleeye.collector.scheduler;

import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Triggers TAIEX daily collection at 18:00 Taipei time (Asia/Taipei).
 *
 * TWSE publishes closing index data after the 13:30 market close.
 * 18:00 provides a safe buffer (vs TAIFEX which publishes at ~16:15).
 *
 * Does NOT modify CollectionScheduler — independent bean.
 */
@Component
public class MarketIndexScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexScheduler.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final MarketIndexService marketIndexService;

    public MarketIndexScheduler(MarketIndexService marketIndexService) {
        this.marketIndexService = marketIndexService;
    }

    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Taipei")
    public void collectTaiex() {
        YearMonth ym = YearMonth.now(TAIPEI);
        log.info("=== TAIEX daily collection triggered for {} ===", ym);
        MarketIndexCollectionResult result = marketIndexService.collectMonth(ym);
        log.info("=== TAIEX collection completed: {} bars for {} ===", result.barsCount(), ym);
    }
}
