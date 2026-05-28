package com.eagleeye.collector.service;

import java.time.LocalDate;

public record DateCollectionResult(
        LocalDate tradeDate,
        CollectionStatus status,
        String errorMessage
) {
    public static DateCollectionResult collected(LocalDate date) {
        return new DateCollectionResult(date, CollectionStatus.COLLECTED, null);
    }

    public static DateCollectionResult noData(LocalDate date) {
        return new DateCollectionResult(date, CollectionStatus.NO_DATA, null);
    }

    public static DateCollectionResult error(LocalDate date, String message) {
        return new DateCollectionResult(date, CollectionStatus.ERROR, message);
    }
}
