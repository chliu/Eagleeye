package com.eagleeye.collector.service;

import java.time.YearMonth;

/**
 * Result of a TAIEX monthly collection operation.
 * Separate from CollectionResult (which is TAIFEX-specific with futuresCount/optionsCount).
 */
public record MarketIndexCollectionResult(
        YearMonth yearMonth,
        int barsCount,
        Status status,
        String errorMessage
) {
    public enum Status { COLLECTED, NO_DATA, ERROR }

    public static MarketIndexCollectionResult collected(YearMonth yearMonth, int barsCount) {
        return new MarketIndexCollectionResult(yearMonth, barsCount, Status.COLLECTED, null);
    }

    public static MarketIndexCollectionResult noData(YearMonth yearMonth) {
        return new MarketIndexCollectionResult(yearMonth, 0, Status.NO_DATA, null);
    }

    public static MarketIndexCollectionResult error(YearMonth yearMonth, String message) {
        return new MarketIndexCollectionResult(yearMonth, 0, Status.ERROR, message);
    }

    public boolean isTradeMonth() { return status == Status.COLLECTED; }
}
