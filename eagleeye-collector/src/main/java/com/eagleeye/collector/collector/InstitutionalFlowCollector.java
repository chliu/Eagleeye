package com.eagleeye.collector.collector;

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
        return switch (result.status()) {
            case COLLECTED -> CollectResult.collected("ok");
            case NO_DATA   -> CollectResult.noData();
            case ERROR     -> CollectResult.error(result.errorMessage());
        };
    }
}
