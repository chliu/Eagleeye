package com.eagleeye.collector.runner;

import com.eagleeye.collector.collector.CollectResult;
import com.eagleeye.collector.collector.ScheduledCollector;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Daily one-shot runner — picks the right collector for the current Taipei time and exits.
 *
 * launchd fires this process four times per weekday (Taipei time):
 *   15:05  → TAIEX  market index        (FMTQIK afterTrading stats published ~15:00)
 *   15:15  → IFLOW  institutional flow  (三大法人 published ~15:00)
 *   15:30  → TAIFEX TAIFEX OI           (未平倉口數及契約金額 published ~15:00)
 *   21:35  → MARGIN margin transactions (融資融券 published 20:30–21:30)
 *
 * Dispatch: sort all ScheduledCollector beans by scheduledAt(), pick the last
 * one whose time has already passed — that is the collector for this trigger.
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

    private final List<ScheduledCollector> collectors;
    private final ApplicationContext applicationContext;

    public DailyCollectionRunner(List<ScheduledCollector> collectors,
                                 ApplicationContext applicationContext) {
        this.collectors = collectors;
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

        LocalTime now = LocalTime.now(TAIPEI);

        selectCollector(collectors, now)
                .ifPresentOrElse(
                        c -> dispatch(c, today),
                        () -> log.warn("No collector scheduled before {}", now));

        System.out.println();
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    // Package-private for testing
    static java.util.Optional<ScheduledCollector> selectCollector(List<ScheduledCollector> collectors, LocalTime now) {
        return collectors.stream()
                .sorted(Comparator.comparing(ScheduledCollector::scheduledAt))
                .filter(c -> !now.isBefore(c.scheduledAt()))
                .reduce((a, b) -> b);
    }

    private void dispatch(ScheduledCollector collector, LocalDate date) {
        log.info("=== Collecting {} for {} ===", collector.name(), date);
        CollectResult result = collector.collect(date);
        System.out.printf("  [%-6s]  %s  %s%n", collector.name(), date, result.detail());
    }
}
