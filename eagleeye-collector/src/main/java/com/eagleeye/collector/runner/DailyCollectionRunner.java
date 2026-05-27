package com.eagleeye.collector.runner;

import com.eagleeye.collector.service.CollectionResult;
import com.eagleeye.collector.service.CollectionService;
import com.eagleeye.collector.service.InstitutionalFlowResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.collector.service.MarginCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Daily one-shot runner — collects one data source per invocation and exits.
 * Activated when no backfill properties are set (i.e. normal daily run).
 *
 * launchd fires this process four times per weekday (Taipei time):
 *   15:05  → market index        (TWSE FMTQIK afterTrading stats published ~15:00)
 *   15:15  → institutional flow  (TWSE 三大法人 published ~15:00)
 *   15:30  → TAIFEX OI           (未平倉口數及契約金額 published ~15:00)
 *   21:35  → margin transactions (TWSE 融資融券 published 20:30–21:30)
 *
 * The runner selects which collector to run by checking the current Taipei time.
 */
@Component
@ConditionalOnProperty(name = "eagleeye.collector.enabled", havingValue = "true")
@ConditionalOnExpression(
    "!environment.containsProperty('backfill.from') && " +
    "!environment.containsProperty('combined.backfill.from') && " +
    "!environment.containsProperty('market-index.backfill.from')"
)
public class DailyCollectionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DailyCollectionRunner.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    // Time-window boundaries (Taipei)
    private static final java.time.LocalTime INDEX_START  = java.time.LocalTime.of(15,  0);
    private static final java.time.LocalTime IFLOW_START  = java.time.LocalTime.of(15, 10);
    private static final java.time.LocalTime OI_START     = java.time.LocalTime.of(15, 20);
    private static final java.time.LocalTime MARGIN_START = java.time.LocalTime.of(21, 30);

    private final CollectionService collectionService;
    private final MarginTransactionService marginTransactionService;
    private final InstitutionalFlowService institutionalFlowService;
    private final MarketIndexService marketIndexService;
    private final ApplicationContext applicationContext;

    public DailyCollectionRunner(CollectionService collectionService,
                                 MarginTransactionService marginTransactionService,
                                 InstitutionalFlowService institutionalFlowService,
                                 MarketIndexService marketIndexService,
                                 ApplicationContext applicationContext) {
        this.collectionService = collectionService;
        this.marginTransactionService = marginTransactionService;
        this.institutionalFlowService = institutionalFlowService;
        this.marketIndexService = marketIndexService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(TAIPEI);

        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            System.out.printf("Skipping weekend: %s%n", today);
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
            return;
        }

        java.time.LocalTime now = java.time.LocalTime.now(TAIPEI);
        if (now.isBefore(INDEX_START)) {
            log.info("No collector scheduled before 14:00 — skipping");
        } else if (now.isBefore(IFLOW_START)) {
            collectMarketIndex(today);
        } else if (now.isBefore(OI_START)) {
            collectInstitutionalFlow(today);
        } else if (now.isBefore(MARGIN_START)) {
            collectTaifexOi(today);
        } else {
            collectMargin(today);
        }

        System.out.println();
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    // 15:05: market index — FMTQIK afterTrading stats published ~15:00
    private void collectMarketIndex(LocalDate today) {
        log.info("=== Collecting market index: {} ===", today);
        MarketIndexCollectionResult mi = marketIndexService.collectMonth(YearMonth.from(today));
        print(mi);
    }

    // 15:10: institutional flow — 三大法人 published ~15:00
    private void collectInstitutionalFlow(LocalDate today) {
        log.info("=== Collecting institutional flow: {} ===", today);
        InstitutionalFlowResult flow = institutionalFlowService.collectDate(today);
        System.out.printf("  [IFLOW]   %-12s  %s%n", today, statusLabel(flow.status()));
    }

    // 21:35: margin transactions — 融資融券 published 20:30–21:30
    private void collectMargin(LocalDate today) {
        log.info("=== Collecting margin transactions: {} ===", today);
        MarginCollectionResult margin = marginTransactionService.collectDate(today);
        System.out.printf("  [MARGIN]  %-12s  %s%n", today, statusLabel(margin.status()));
    }

    // 15:10: TAIFEX OI — 未平倉口數及契約金額 published ~15:00
    private void collectTaifexOi(LocalDate today) {
        log.info("=== Collecting TAIFEX OI: {} ===", today);
        CollectionResult taifex = collectionService.collectAll(today);
        print(today, taifex);
    }

    private void print(MarketIndexCollectionResult r) {
        String status = switch (r.status()) {
            case COLLECTED -> "bars: " + r.barsCount();
            case NO_DATA   -> "no data";
            case ERROR     -> "ERROR: " + r.errorMessage();
        };
        System.out.printf("  [TAIEX]   %-8s  %s%n", r.yearMonth(), status);
    }

    private void print(LocalDate date, CollectionResult r) {
        String status = switch (r.status()) {
            case COLLECTED -> String.format("futures: %d  options: %d", r.futuresCount(), r.optionsCount());
            case NO_DATA   -> "holiday";
            case ERROR     -> "ERROR: " + r.errorMessage();
        };
        System.out.printf("  [TAIFEX]  %-12s  %s%n", date, status);
    }

    private String statusLabel(com.eagleeye.collector.service.CollectionStatus s) {
        return switch (s) {
            case COLLECTED -> "collected";
            case NO_DATA   -> "no data";
            case ERROR     -> "error";
        };
    }
}
