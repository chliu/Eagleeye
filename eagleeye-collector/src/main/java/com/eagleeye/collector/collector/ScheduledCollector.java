package com.eagleeye.collector.collector;

import java.time.LocalDate;
import java.time.LocalTime;

public interface ScheduledCollector {

    /** Taipei time at which this collector should run. */
    LocalTime scheduledAt();

    /** Short display name used in logs and console output. */
    String name();

    CollectorOutcome collect(LocalDate date);
}
