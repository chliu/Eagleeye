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
    public CollectorOutcome collect(LocalDate date) {
        var result = service.collectAll(date);
        return switch (result) {
            case com.eagleeye.collector.service.FuturesOptionsCollectionResult.Collected c -> CollectorOutcome.collected(
                    "futures: " + c.futuresCount() + "  options: " + c.optionsCount());
            case com.eagleeye.collector.service.FuturesOptionsCollectionResult.NoData n   -> CollectorOutcome.noData();
            case com.eagleeye.collector.service.FuturesOptionsCollectionResult.Error e    -> CollectorOutcome.error(e.message());
        };
    }
}
