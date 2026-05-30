package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.CollectionService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TaifexOiCollector implements ScheduledCollector {

    private final CollectionService service;

    public TaifexOiCollector(CollectionService service) {
        this.service = service;
    }

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
