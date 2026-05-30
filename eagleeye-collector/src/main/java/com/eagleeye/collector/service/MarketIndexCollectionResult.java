package com.eagleeye.collector.service;

import java.time.YearMonth;
import java.util.Objects;

public sealed interface MarketIndexCollectionResult {

    YearMonth yearMonth();

    record Collected(YearMonth yearMonth, int barsCount) implements MarketIndexCollectionResult {}

    record NoData(YearMonth yearMonth) implements MarketIndexCollectionResult {}

    record Error(YearMonth yearMonth, String message) implements MarketIndexCollectionResult {
        public Error {
            Objects.requireNonNull(message, "message");
        }
    }
}
