package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class InstitutionalFlowCollector implements ScheduledCollector {

    private static final LocalTime SCHEDULED_AT = LocalTime.of(15, 15);

    private final InstitutionalFlowService service;

    public InstitutionalFlowCollector(InstitutionalFlowService service) {
        this.service = service;
    }

    @Override public LocalTime scheduledAt() { return SCHEDULED_AT; }
    @Override public String name() { return "IFLOW"; }

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
