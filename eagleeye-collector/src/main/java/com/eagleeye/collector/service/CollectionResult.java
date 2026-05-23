package com.eagleeye.collector.service;

import java.time.LocalDate;

public record CollectionResult(
        LocalDate date,
        int futuresCount,
        int optionsCount,
        CollectionStatus status,
        String errorMessage
) {
    public static CollectionResult collected(LocalDate date, int futures, int options) {
        return new CollectionResult(date, futures, options, CollectionStatus.COLLECTED, null);
    }

    public static CollectionResult noData(LocalDate date) {
        return new CollectionResult(date, 0, 0, CollectionStatus.NO_DATA, null);
    }

    public static CollectionResult error(LocalDate date, String message) {
        return new CollectionResult(date, 0, 0, CollectionStatus.ERROR, message);
    }

    public boolean isTradeDay() { return status == CollectionStatus.COLLECTED; }
}
