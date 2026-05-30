package com.eagleeye.collector.collector;

import java.time.LocalDate;

/**
 * A single data source the collector knows how to fetch.
 *
 * <p>Implementations are pure adapters: map a domain service's result to a
 * {@link CollectorOutcome} and nothing more. <em>When</em> a collector runs is
 * owned by launchd (one job per collector); this type only declares <em>what</em>
 * runs ({@link #name()} is the dispatch key) and <em>how</em> ({@link #collect}).
 */
public interface ScheduledCollector {

    /** Short, stable name — the dispatch key matched against {@code --collector}. */
    String name();

    CollectorOutcome collect(LocalDate date);
}
