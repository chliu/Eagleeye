package com.eagleeye.collector.service;

import java.time.LocalDate;
import java.util.Objects;

public sealed interface CollectionResult {

    LocalDate date();

    record Collected(LocalDate date, int futuresCount, int optionsCount) implements CollectionResult {}

    record NoData(LocalDate date) implements CollectionResult {}

    record Error(LocalDate date, String message) implements CollectionResult {
        public Error {
            Objects.requireNonNull(message, "message");
        }
    }
}
