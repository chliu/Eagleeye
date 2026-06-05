package com.eagleeye.collector.runner;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.TxTickService;
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

@Component
@ConditionalOnProperty(name = "txtick.backfill.from")
public class TxTickBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TxTickBackfillRunner.class);
    private static final long REQUEST_DELAY_MS = 1_000;

    @Value("${txtick.backfill.from}")
    private String fromStr;

    @Value("${txtick.backfill.to:#{null}}")
    private String toStr;

    private final TxTickService service;
    private final ApplicationContext applicationContext;

    public TxTickBackfillRunner(TxTickService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to   = toStr != null ? LocalDate.parse(toStr) : LocalDate.now();

        System.out.printf("TX Tick backfill: %s → %s%n%n", from, to);
        int ok = 0, holidays = 0, errors = 0;

        LocalDate current = from;
        while (!current.isAfter(to)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                current = current.plusDays(1);
                continue;
            }
            DateCollectionResult result = service.collectDate(current);
            switch (result) {
                case DateCollectionResult.Collected c -> { ok++;       System.out.printf("  %-12s  OK%n", current); }
                case DateCollectionResult.NoData n    -> { holidays++; System.out.printf("  %-12s  HOLIDAY%n", current); }
                case DateCollectionResult.Error e     -> { errors++;   System.out.printf("  %-12s  ERROR: %s%n", current, e.message()); }
            }
            Thread.sleep(REQUEST_DELAY_MS);
            current = current.plusDays(1);
        }

        System.out.printf("%n=== TX Tick backfill complete: %d collected, %d holidays, %d errors ===%n",
            ok, holidays, errors);
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
