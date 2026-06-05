package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.TxTickService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TxTickCollector implements ScheduledCollector {

    private final TxTickService service;

    public TxTickCollector(TxTickService service) {
        this.service = service;
    }

    @Override public String name() { return "TXTICK"; }

    @Override
    public CollectorOutcome collect(LocalDate date) {
        return switch (service.collectDate(date)) {
            case DateCollectionResult.Collected c -> CollectorOutcome.collected("ok");
            case DateCollectionResult.NoData n    -> CollectorOutcome.noData();
            case DateCollectionResult.Error e     -> CollectorOutcome.error(e.message());
        };
    }
}
