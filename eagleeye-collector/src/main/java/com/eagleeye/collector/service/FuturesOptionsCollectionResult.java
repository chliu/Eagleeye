package com.eagleeye.collector.service;

import java.time.LocalDate;
import java.util.Objects;

public sealed interface FuturesOptionsCollectionResult {

    LocalDate date();

    record Collected(LocalDate date, int futuresCount, int optionsCount) implements FuturesOptionsCollectionResult {}

    record NoData(LocalDate date) implements FuturesOptionsCollectionResult {}

    record Error(LocalDate date, String message) implements FuturesOptionsCollectionResult {
        public Error {
            Objects.requireNonNull(message, "message");
        }
    }
}
