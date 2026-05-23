package com.eagleeye.collector.service;

import java.time.LocalDate;

public record InstitutionalFlowResult(
        LocalDate tradeDate,
        CollectionStatus status,
        String errorMessage
) {
    public static InstitutionalFlowResult collected(LocalDate date) {
        return new InstitutionalFlowResult(date, CollectionStatus.COLLECTED, null);
    }

    public static InstitutionalFlowResult noData(LocalDate date) {
        return new InstitutionalFlowResult(date, CollectionStatus.NO_DATA, null);
    }

    public static InstitutionalFlowResult error(LocalDate date, String message) {
        return new InstitutionalFlowResult(date, CollectionStatus.ERROR, message);
    }
}
