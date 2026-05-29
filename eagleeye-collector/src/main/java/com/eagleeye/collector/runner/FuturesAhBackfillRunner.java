package com.eagleeye.collector.runner;

import com.eagleeye.collector.service.CollectionStatus;
import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.FuturesAhService;
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
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot after-hours futures backfill — activate with --futures-ah.backfill.from.
 *
 * Usage:
 *   java -jar eagleeye-collector-exec.jar \
 *        --futures-ah.backfill.from=2026-05-01 \
 *        --futures-ah.backfill.to=2026-05-28
 */
@Component
@ConditionalOnProperty(name = "futures-ah.backfill.from")
public class FuturesAhBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FuturesAhBackfillRunner.class);
    private static final long REQUEST_DELAY_MS = 500;

    @Value("${futures-ah.backfill.from}")
    private String fromStr;

    @Value("${futures-ah.backfill.to:#{null}}")
    private String toStr;

    private final FuturesAhService futuresAhService;
    private final ApplicationContext applicationContext;

    public FuturesAhBackfillRunner(FuturesAhService futuresAhService,
                                   ApplicationContext applicationContext) {
        this.futuresAhService = futuresAhService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to   = (toStr != null) ? LocalDate.parse(toStr) : LocalDate.now();

        log.info("=== After-hours futures backfill start: {} → {} ===", from, to);
        System.out.printf("After-hours futures backfill: %s → %s%n%n", from, to);

        List<DateCollectionResult> results = new ArrayList<>();
        LocalDate current = from;

        while (!current.isAfter(to)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                System.out.printf("  %-12s  WEEKEND%n", current);
                current = current.plusDays(1);
                continue;
            }

            DateCollectionResult result = futuresAhService.collectDate(current);
            results.add(result);
            printRow(current, result);

            Thread.sleep(REQUEST_DELAY_MS);
            current = current.plusDays(1);
        }

        printSummary(from, to, results);
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    private void printRow(LocalDate date, DateCollectionResult r) {
        String status = switch (r.status()) {
            case COLLECTED -> "ok";
            case NO_DATA   -> "no data";
            case ERROR     -> "ERROR: " + r.errorMessage();
        };
        System.out.printf("  [FUTAH]  %-12s  %s%n", date, status);
    }

    private void printSummary(LocalDate from, LocalDate to, List<DateCollectionResult> results) {
        long collected = results.stream().filter(r -> r.status() == CollectionStatus.COLLECTED).count();
        long noData    = results.stream().filter(r -> r.status() == CollectionStatus.NO_DATA).count();
        long errors    = results.stream().filter(r -> r.status() == CollectionStatus.ERROR).count();

        System.out.printf("""

                === After-hours futures backfill complete: %s → %s ===
                  Days collected : %d
                  No-data days   : %d
                  Errors         : %d
                %n""", from, to, collected, noData, errors);
    }
}
