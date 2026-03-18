package com.eagleeye.collector.runner;

import com.eagleeye.collector.service.CollectionResult;
import com.eagleeye.collector.service.CollectionService;
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
 * One-shot backfill runner — activate by setting backfill.from property.
 *
 * Usage:
 *   java -jar eagleeye-collector-exec.jar \
 *        --backfill.from=2026-02-01 \
 *        --backfill.to=2026-02-28 \
 *        --spring.main.web-application-type=none
 */
@Component
@ConditionalOnProperty(name = "backfill.from")
public class BackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillRunner.class);

    // Millis to wait between HTTP requests — be respectful to TAIFEX
    private static final long REQUEST_DELAY_MS = 500;

    @Value("${backfill.from}")
    private String fromStr;

    @Value("${backfill.to:#{null}}")
    private String toStr;

    private final CollectionService collectionService;
    private final ApplicationContext applicationContext;

    public BackfillRunner(CollectionService collectionService, ApplicationContext applicationContext) {
        this.collectionService = collectionService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to   = (toStr != null) ? LocalDate.parse(toStr) : LocalDate.now();

        log.info("=== Backfill start: {} → {} ===", from, to);
        System.out.printf("Backfill: %s → %s%n%n", from, to);

        List<CollectionResult> results = new ArrayList<>();

        LocalDate current = from;
        while (!current.isAfter(to)) {
            DayOfWeek dow = current.getDayOfWeek();

            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                printRow(current, "WEEKEND", "-", "-");
                current = current.plusDays(1);
                continue;
            }

            CollectionResult result = collectionService.collectAll(current);
            results.add(result);

            switch (result.status()) {
                case COLLECTED -> printRow(current, "OK",
                        result.futuresCount() + " rows",
                        result.optionsCount() + " rows");
                case NO_DATA   -> printRow(current, "HOLIDAY", "-", "-");
                case ERROR     -> printRow(current, "ERROR", result.errorMessage(), "");
            }

            Thread.sleep(REQUEST_DELAY_MS);
            current = current.plusDays(1);
        }

        printSummary(from, to, results);
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    private void printRow(LocalDate date, String status, String futures, String options) {
        System.out.printf("  %-12s  %-8s  futures: %-10s  options: %s%n",
                date, status, futures, options);
    }

    private void printSummary(LocalDate from, LocalDate to, List<CollectionResult> results) {
        long tradeDays  = results.stream().filter(CollectionResult::isTradeDay).count();
        long holidays   = results.stream().filter(r -> r.status() == CollectionResult.Status.NO_DATA).count();
        long errors     = results.stream().filter(r -> r.status() == CollectionResult.Status.ERROR).count();
        long totalFut   = results.stream().mapToLong(CollectionResult::futuresCount).sum();
        long totalOpt   = results.stream().mapToLong(CollectionResult::optionsCount).sum();

        System.out.printf("""

                === Backfill complete: %s → %s ===
                  Trading days collected : %d
                  Holidays / no-data     : %d
                  Errors                 : %d
                  Total futures rows     : %d
                  Total options rows     : %d
                %n""", from, to, tradeDays, holidays, errors, totalFut, totalOpt);
    }
}
