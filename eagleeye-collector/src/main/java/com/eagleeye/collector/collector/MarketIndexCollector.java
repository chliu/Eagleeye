package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class MarketIndexCollector implements ScheduledCollector {

    private final MarketIndexService service;

    public MarketIndexCollector(MarketIndexService service) {
        this.service = service;
    }

    @Override public String name() { return "TAIEX"; }

    @Override
    public CollectorOutcome collect(LocalDate date) {
        var result = service.collectMonth(YearMonth.from(date));
        return switch (result) {
            case MarketIndexCollectionResult.Collected c -> CollectorOutcome.collected("bars: " + c.barsCount());
            case MarketIndexCollectionResult.NoData n    -> CollectorOutcome.noData();
            case MarketIndexCollectionResult.Error e     -> CollectorOutcome.error(e.message());
        };
    }
}
