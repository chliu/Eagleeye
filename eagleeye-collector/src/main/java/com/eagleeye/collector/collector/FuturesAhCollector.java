package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.FuturesAhService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class FuturesAhCollector implements ScheduledCollector {

    // After-hours session ends 05:00 on the attributed date; 09:05 gives a safe margin
    private static final LocalTime SCHEDULED_AT = LocalTime.of(9, 5);

    private final FuturesAhService service;

    public FuturesAhCollector(FuturesAhService service) {
        this.service = service;
    }

    @Override public LocalTime scheduledAt() { return SCHEDULED_AT; }
    @Override public String name() { return "FUTAH"; }

    @Override
    public CollectResult collect(LocalDate date) {
        var result = service.collectDate(date);
        return switch (result.status()) {
            case COLLECTED -> CollectResult.collected("ok");
            case NO_DATA   -> CollectResult.noData();
            case ERROR     -> CollectResult.error(result.errorMessage());
        };
    }
}
