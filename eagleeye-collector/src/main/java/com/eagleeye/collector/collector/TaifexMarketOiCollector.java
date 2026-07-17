package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.FuturesMarketOiService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TaifexMarketOiCollector implements ScheduledCollector {

    private final FuturesMarketOiService service;

    public TaifexMarketOiCollector(FuturesMarketOiService service) {
        this.service = service;
    }

    @Override public String name() { return "MKTOI"; }

    @Override
    public CollectorOutcome collect(LocalDate date) {
        var result = service.collectDate(date);
        return switch (result) {
            case DateCollectionResult.Collected c -> CollectorOutcome.collected("ok");
            case DateCollectionResult.NoData n    -> CollectorOutcome.noData();
            case DateCollectionResult.Error e     -> CollectorOutcome.error(e.message());
        };
    }
}
