package com.eagleeye.collector.service;

import java.time.LocalDate;

/**
 * Result of an institutional flow daily collection operation.
 */
public record InstitutionalFlowResult(
        LocalDate tradeDate,
        Status status,
        String errorMessage
) {
    public enum Status { COLLECTED, NO_DATA, ERROR }

    public static InstitutionalFlowResult collected(LocalDate date) {
        return new InstitutionalFlowResult(date, Status.COLLECTED, null);
    }

    public static InstitutionalFlowResult noData(LocalDate date) {
        return new InstitutionalFlowResult(date, Status.NO_DATA, null);
    }

    public static InstitutionalFlowResult error(LocalDate date, String message) {
        return new InstitutionalFlowResult(date, Status.ERROR, message);
    }
}
