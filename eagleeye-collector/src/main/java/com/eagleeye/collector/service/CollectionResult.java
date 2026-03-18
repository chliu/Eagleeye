package com.eagleeye.collector.service;

import java.time.LocalDate;

public record CollectionResult(
        LocalDate date,
        int futuresCount,
        int optionsCount,
        Status status,
        String errorMessage
) {
    public enum Status { COLLECTED, NO_DATA, ERROR }

    public static CollectionResult collected(LocalDate date, int futures, int options) {
        return new CollectionResult(date, futures, options, Status.COLLECTED, null);
    }

    public static CollectionResult noData(LocalDate date) {
        return new CollectionResult(date, 0, 0, Status.NO_DATA, null);
    }

    public static CollectionResult error(LocalDate date, String message) {
        return new CollectionResult(date, 0, 0, Status.ERROR, message);
    }

    public boolean isTradeDay() { return status == Status.COLLECTED; }
}
