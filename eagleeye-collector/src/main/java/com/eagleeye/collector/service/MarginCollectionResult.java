package com.eagleeye.collector.service;

import java.time.LocalDate;

// Uses LocalDate (not YearMonth) — MI_MARGN API is per-day
public record MarginCollectionResult(
        LocalDate tradeDate,
        CollectionStatus status,
        String errorMessage
) {
    public static MarginCollectionResult collected(LocalDate date) {
        return new MarginCollectionResult(date, CollectionStatus.COLLECTED, null);
    }

    public static MarginCollectionResult noData(LocalDate date) {
        return new MarginCollectionResult(date, CollectionStatus.NO_DATA, null);
    }

    public static MarginCollectionResult error(LocalDate date, String message) {
        return new MarginCollectionResult(date, CollectionStatus.ERROR, message);
    }
}
