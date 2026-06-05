package com.eagleeye.collector.runner;

import com.eagleeye.collector.collector.CollectorOutcome;
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
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Name-addressed one-shot dispatcher.
 *
 * <p>Each launchd job names which collector(s) to run via {@code --collector=NAME}
 * (comma-separated, or {@code ALL}). launchd owns the schedule; this code owns the
 * routing. Adding a data source is a new {@code @Component} + a new launchd job —
 * the dispatcher never changes.
 *
 * <p>Example launchd argument: {@code --collector=MARGIN} or {@code --collector=TAIEX,IFLOW,TAIFEX}.
 */
@Component
@ConditionalOnProperty(name = "eagleeye.collector.enabled", havingValue = "true")
@ConditionalOnExpression(
    "!environment.containsProperty('backfill.from') && " +
    "!environment.containsProperty('combined.backfill.from') && " +
    "!environment.containsProperty('market-index.backfill.from') && " +
    "!environment.containsProperty('futures-ah.backfill.from') && " +
    "!environment.containsProperty('txtick.backfill.from')"
)
public class CollectorDispatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectorDispatchRunner.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final String ALL = "ALL";

    /** Keyed by upper-cased name, insertion order preserved for deterministic ALL runs. */
    private final Map<String, ScheduledCollector> byName;
    private final CollectorExecutor executor;
    private final ApplicationContext applicationContext;

    public CollectorDispatchRunner(List<ScheduledCollector> collectors,
                                   CollectorExecutor executor,
                                   ApplicationContext applicationContext) {
        this.byName = new LinkedHashMap<>();
        collectors.forEach(c -> byName.put(c.name().toUpperCase(Locale.ROOT), c));
        this.executor = executor;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(TAIPEI);

        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            System.out.printf("Skipping weekend: %s%n", today);
            exit();
            return;
        }

        List<String> names = resolveNames(args.getOptionValues("collector"), byName.keySet());
        if (names.isEmpty()) {
            log.warn("No --collector specified; nothing to do. Known: {}", byName.keySet());
            exit();
            return;
        }

        for (String name : names) {
            ScheduledCollector collector = byName.get(name);
            if (collector == null) {
                log.error("Unknown collector '{}'. Known: {}", name, byName.keySet());
                continue;
            }
            CollectorOutcome outcome = executor.run(collector, today);
            System.out.printf("  [%-6s]  %s  %s%n", collector.name(), today, outcome.detail());
        }

        System.out.println();
        exit();
    }

    /**
     * Parses {@code --collector} option values into the canonical (upper-cased) names to run.
     * Splits comma-separated values, trims, de-duplicates, and expands {@code ALL} to every
     * known collector. Unknown names are passed through for the caller to reject at lookup.
     */
    static List<String> resolveNames(List<String> optionValues, Set<String> known) {
        if (optionValues == null) {
            return List.of();
        }
        List<String> tokens = optionValues.stream()
                .flatMap(v -> Arrays.stream(v.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        if (tokens.contains(ALL)) {
            return known.stream()
                    .map(s -> s.toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();
        }
        return tokens;
    }

    private void exit() {
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
