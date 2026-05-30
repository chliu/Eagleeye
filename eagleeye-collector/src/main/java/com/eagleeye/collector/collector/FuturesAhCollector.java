package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.FuturesAhService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class FuturesAhCollector implements ScheduledCollector {

    private static final LocalTime SCHEDULED_AT = LocalTime.of(7, 0);

    private final FuturesAhService service;

    public FuturesAhCollector(FuturesAhService service) {
        this.service = service;
    }

    @Override public LocalTime scheduledAt() { return SCHEDULED_AT; }
    @Override public String name() { return "FUTAH"; }

    @Override
    public CollectResult collect(LocalDate date) {
        var result = service.collectDate(date);
        return switch (result) {
            case DateCollectionResult.Collected c -> CollectResult.collected("ok");
            case DateCollectionResult.NoData n    -> CollectResult.noData();
            case DateCollectionResult.Error e     -> CollectResult.error(e.message());
        };
    }
}
