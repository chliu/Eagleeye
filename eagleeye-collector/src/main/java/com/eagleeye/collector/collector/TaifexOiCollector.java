package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.CollectionService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class TaifexOiCollector implements ScheduledCollector {

    private static final LocalTime SCHEDULED_AT = LocalTime.of(15, 30);

    private final CollectionService service;

    public TaifexOiCollector(CollectionService service) {
        this.service = service;
    }

    @Override public LocalTime scheduledAt() { return SCHEDULED_AT; }
    @Override public String name() { return "TAIFEX"; }

    @Override
    public CollectResult collect(LocalDate date) {
        var result = service.collectAll(date);
        return switch (result) {
            case com.eagleeye.collector.service.CollectionResult.Collected c -> CollectResult.collected(
                    "futures: " + c.futuresCount() + "  options: " + c.optionsCount());
            case com.eagleeye.collector.service.CollectionResult.NoData n   -> CollectResult.noData();
            case com.eagleeye.collector.service.CollectionResult.Error e    -> CollectResult.error(e.message());
        };
    }
}
