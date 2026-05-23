package com.eagleeye.collector.runner;

import com.eagleeye.collector.service.CollectionStatus;
import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot TAIEX backfill runner — activated by setting market-index.backfill.from property.
 *
 * Usage:
 *   java -jar eagleeye-collector-exec.jar \
 *        --market-index.backfill.from=2025-03-01 \
 *        --market-index.backfill.to=2026-03-18 \
 *        --spring.main.web-application-type=none
 *
 * Iterates month-by-month. Does NOT activate BackfillRunner (distinct property name).
 */
@Component
@ConditionalOnProperty(name = "market-index.backfill.from")
public class MarketIndexBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexBackfillRunner.class);
    private static final long REQUEST_DELAY_MS = 500;

    @Value("${market-index.backfill.from}")
    private String fromStr;

    @Value("${market-index.backfill.to:#{null}}")
    private String toStr;

    private final MarketIndexService marketIndexService;
    private final ApplicationContext applicationContext;

    public MarketIndexBackfillRunner(MarketIndexService marketIndexService,
                                     ApplicationContext applicationContext) {
        this.marketIndexService = marketIndexService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        YearMonth from = YearMonth.from(LocalDate.parse(fromStr));
        YearMonth to   = (toStr != null) ? YearMonth.from(LocalDate.parse(toStr)) : YearMonth.now();

        log.info("=== TAIEX backfill start: {} → {} ===", from, to);
        System.out.printf("TAIEX backfill: %s → %s%n%n", from, to);

        List<MarketIndexCollectionResult> results = new ArrayList<>();
        YearMonth current = from;

        while (!current.isAfter(to)) {
            MarketIndexCollectionResult result = marketIndexService.collectMonth(current);
            results.add(result);
            printRow(result);
            Thread.sleep(REQUEST_DELAY_MS);
            current = current.plusMonths(1);
        }

        printSummary(from, to, results);
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    private void printRow(MarketIndexCollectionResult result) {
        System.out.printf("  %-8s  %-8s  bars: %d%n",
                result.yearMonth(),
                result.status(),
                result.barsCount());
    }

    private void printSummary(YearMonth from, YearMonth to, List<MarketIndexCollectionResult> results) {
        long collected = results.stream().filter(MarketIndexCollectionResult::isTradeMonth).count();
        long noData    = results.stream().filter(r -> r.status() == CollectionStatus.NO_DATA).count();
        long errors    = results.stream().filter(r -> r.status() == CollectionStatus.ERROR).count();
        long totalBars = results.stream().mapToLong(MarketIndexCollectionResult::barsCount).sum();

        System.out.printf("""

                === TAIEX backfill complete: %s → %s ===
                  Months collected : %d
                  No-data months   : %d
                  Errors           : %d
                  Total bars       : %d
                %n""", from, to, collected, noData, errors, totalBars);
    }
}
