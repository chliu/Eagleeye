package com.eagleeye.collector.service;

import java.time.LocalDate;

/**
 * Result of a margin transaction daily collection operation.
 * Uses LocalDate (not YearMonth) — MI_MARGN API is per-day.
 */
public record MarginCollectionResult(
        LocalDate tradeDate,
        Status status,
        String errorMessage
) {
    public enum Status { COLLECTED, NO_DATA, ERROR }

    public static MarginCollectionResult collected(LocalDate date) {
        return new MarginCollectionResult(date, Status.COLLECTED, null);
    }

    public static MarginCollectionResult noData(LocalDate date) {
        return new MarginCollectionResult(date, Status.NO_DATA, null);
    }

    public static MarginCollectionResult error(LocalDate date, String message) {
        return new MarginCollectionResult(date, Status.ERROR, message);
    }
}
