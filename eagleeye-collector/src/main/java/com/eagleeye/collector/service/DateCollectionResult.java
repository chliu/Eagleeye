package com.eagleeye.collector.service;

import java.time.LocalDate;
import java.util.Objects;

public sealed interface DateCollectionResult {

    LocalDate tradeDate();

    record Collected(LocalDate tradeDate) implements DateCollectionResult {}

    record NoData(LocalDate tradeDate) implements DateCollectionResult {}

    record Error(LocalDate tradeDate, String message) implements DateCollectionResult {
        public Error {
            Objects.requireNonNull(message, "message");
        }
    }
}
