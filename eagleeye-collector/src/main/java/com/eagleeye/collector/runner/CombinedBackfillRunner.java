package com.eagleeye.collector.runner;

import com.eagleeye.collector.service.CollectionResult;
import com.eagleeye.collector.service.CollectionService;
import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.collector.service.MarginTransactionService;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Combined one-shot backfill runner — collects TAIEX market index and TAIFEX
 * institutional data together, grouped by trading date.
 *
 * For each month in range:
 *   1. Collect TAIEX market index (one API call for the whole month)
 *   2. For each weekday in the month (within range): collect TAIFEX data
 *
 * Activated by setting the {@code combined.backfill.from} property.
 */
@Component
@ConditionalOnProperty(name = "combined.backfill.from")
public class CombinedBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CombinedBackfillRunner.class);
    @Value("${combined.backfill.from}")
    private String fromStr;

    @Value("${combined.backfill.to:#{null}}")
    private String toStr;

    private final MarketIndexService marketIndexService;
    private final CollectionService collectionService;
    private final MarginTransactionService marginTransactionService;
    private final InstitutionalFlowService institutionalFlowService;
    private final ApplicationContext applicationContext;
    private final long requestDelayMs;

    public CombinedBackfillRunner(MarketIndexService marketIndexService,
                                  CollectionService collectionService,
                                  ApplicationContext applicationContext,
                                  MarginTransactionService marginTransactionService,
                                  InstitutionalFlowService institutionalFlowService) {
        this(marketIndexService, collectionService, applicationContext,
                marginTransactionService, institutionalFlowService, 500);
    }

    CombinedBackfillRunner(MarketIndexService marketIndexService,
                           CollectionService collectionService,
                           ApplicationContext applicationContext,
                           MarginTransactionService marginTransactionService,
                           InstitutionalFlowService institutionalFlowService,
                           long requestDelayMs) {
        this.marketIndexService = marketIndexService;
        this.collectionService = collectionService;
        this.marginTransactionService = marginTransactionService;
        this.institutionalFlowService = institutionalFlowService;
        this.applicationContext = applicationContext;
        this.requestDelayMs = requestDelayMs;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to   = (toStr != null) ? LocalDate.parse(toStr) : LocalDate.now();

        log.info("=== Combined backfill start: {} → {} ===", from, to);
        System.out.printf("Combined backfill: %s → %s%n%n", from, to);

        executeBackfill(from, to);

        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    /**
     * Core backfill logic — extracted for testability.
     */
    void executeBackfill(LocalDate from, LocalDate to) throws InterruptedException {
        YearMonth fromYm = YearMonth.from(from);
        YearMonth toYm   = YearMonth.from(to);

        YearMonth currentYm = fromYm;
        while (!currentYm.isAfter(toYm)) {

            // 1. Market index — one call per month
            MarketIndexCollectionResult miResult = marketIndexService.collectMonth(currentYm);
            printMarketIndex(miResult);
            Thread.sleep(requestDelayMs);

            // 2. TAIFEX — one call per weekday in the month, clipped to [from, to]
            LocalDate dayStart = currentYm.equals(fromYm) ? from : currentYm.atDay(1);
            LocalDate dayEnd   = currentYm.equals(toYm)   ? to   : currentYm.atEndOfMonth();

            LocalDate day = dayStart;
            while (!day.isAfter(dayEnd)) {
                DayOfWeek dow = day.getDayOfWeek();
                if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                    CollectionResult result = collectionService.collectAll(day);
                    printTaifex(day, result);
                    Thread.sleep(requestDelayMs);

                    DateCollectionResult marginResult = marginTransactionService.collectDate(day);
                    printDateResult("MARGIN", day, marginResult);
                    Thread.sleep(requestDelayMs);

                    DateCollectionResult flowResult = institutionalFlowService.collectDate(day);
                    printDateResult("IFLOW ", day, flowResult);
                    Thread.sleep(requestDelayMs);
                }
                day = day.plusDays(1);
            }

            System.out.println();
            currentYm = currentYm.plusMonths(1);
        }
    }

    private void printMarketIndex(MarketIndexCollectionResult r) {
        String status = switch (r) {
            case MarketIndexCollectionResult.Collected c -> "bars: " + c.barsCount();
            case MarketIndexCollectionResult.NoData n    -> "no data";
            case MarketIndexCollectionResult.Error e     -> "ERROR: " + e.message();
        };
        System.out.printf("  [TAIEX]   %-8s  %s%n", r.yearMonth(), status);
    }

    private void printTaifex(LocalDate date, CollectionResult r) {
        String status = switch (r) {
            case CollectionResult.Collected c -> String.format("futures: %d  options: %d", c.futuresCount(), c.optionsCount());
            case CollectionResult.NoData n    -> "holiday";
            case CollectionResult.Error e     -> "ERROR: " + e.message();
        };
        System.out.printf("  [TAIFEX]  %-12s  %s%n", date, status);
    }

    private void printDateResult(String label, LocalDate date, DateCollectionResult r) {
        String status = switch (r) {
            case DateCollectionResult.Collected c -> "collected";
            case DateCollectionResult.NoData n    -> "no data";
            case DateCollectionResult.Error e     -> "ERROR: " + e.message();
        };
        System.out.printf("  [%s]  %-12s  %s%n", label, date, status);
    }
}
