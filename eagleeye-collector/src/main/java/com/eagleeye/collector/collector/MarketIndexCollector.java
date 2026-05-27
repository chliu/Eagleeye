package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.MarketIndexService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

@Component
public class MarketIndexCollector implements ScheduledCollector {

    private static final LocalTime SCHEDULED_AT = LocalTime.of(15, 5);

    private final MarketIndexService service;

    public MarketIndexCollector(MarketIndexService service) {
        this.service = service;
    }

    @Override public LocalTime scheduledAt() { return SCHEDULED_AT; }
    @Override public String name() { return "TAIEX"; }

    @Override
    public CollectResult collect(LocalDate date) {
        var result = service.collectMonth(YearMonth.from(date));
        return switch (result.status()) {
            case COLLECTED -> CollectResult.collected("bars: " + result.barsCount());
            case NO_DATA   -> CollectResult.noData();
            case ERROR     -> CollectResult.error(result.errorMessage());
        };
    }
}
