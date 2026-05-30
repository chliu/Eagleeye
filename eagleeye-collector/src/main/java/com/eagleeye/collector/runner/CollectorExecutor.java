package com.eagleeye.collector.runner;

import com.eagleeye.collector.collector.CollectorOutcome;
import com.eagleeye.collector.collector.ScheduledCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Template wrapper around a single {@link ScheduledCollector} run — the one place
 * cross-cutting concerns live: timing, structured logging, and exception safety.
 *
 * <p>Collectors stay pure adapters: they map their domain service's result to a
 * {@link CollectorOutcome} and nothing more. Any unexpected exception escaping a
 * collector is caught here and reported as an ERROR outcome, so one failing
 * collector never aborts the dispatching process.
 */
@Component
public class CollectorExecutor {

    private static final Logger log = LoggerFactory.getLogger(CollectorExecutor.class);

    public CollectorOutcome run(ScheduledCollector collector, LocalDate date) {
        log.info("=== Collecting {} for {} ===", collector.name(), date);
        long startNanos = System.nanoTime();
        try {
            CollectorOutcome outcome = collector.collect(date);
            long ms = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("[{}] {} {} ({} ms)", collector.name(), date, outcome.detail(), ms);
            return outcome;
        } catch (Exception e) {
            log.error("[{}] {} failed: {}", collector.name(), date, e.getMessage(), e);
            return CollectorOutcome.error(e.getMessage());
        }
    }
}
