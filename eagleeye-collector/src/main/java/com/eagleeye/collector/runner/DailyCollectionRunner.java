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
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Daily one-shot runner — collects all data for today and exits.
 * Activated when no backfill properties are set (i.e. normal daily run).
 * launchd starts this process at 18:30 Taipei time via StartCalendarInterval.
 */
@Component
@ConditionalOnExpression(
    "!environment.containsProperty('backfill.from') && " +
    "!environment.containsProperty('combined.backfill.from') && " +
    "!environment.containsProperty('market-index.backfill.from')"
)
public class DailyCollectionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DailyCollectionRunner.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final long REQUEST_DELAY_MS = 500;

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
    public void run(ApplicationArguments args) throws Exception {
        LocalDate today = LocalDate.now(TAIPEI);

        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            System.out.printf("Skipping weekend: %s%n", today);
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
            return;
        }

        log.info("=== Daily collection: {} ===", today);
        System.out.printf("Daily collection: %s%n%n", today);

        MarketIndexCollectionResult mi = marketIndexService.collectMonth(YearMonth.from(today));
        print(mi);
        Thread.sleep(REQUEST_DELAY_MS);

        CollectionResult taifex = collectionService.collectAll(today);
        print(today, taifex);
        Thread.sleep(REQUEST_DELAY_MS);

        MarginCollectionResult margin = marginTransactionService.collectDate(today);
        System.out.printf("  [MARGIN]  %-12s  %s%n", today, statusLabel(margin.status()));
        Thread.sleep(REQUEST_DELAY_MS);

        InstitutionalFlowResult flow = institutionalFlowService.collectDate(today);
        System.out.printf("  [IFLOW]   %-12s  %s%n", today, statusLabel(flow.status()));

        System.out.println();
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
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
