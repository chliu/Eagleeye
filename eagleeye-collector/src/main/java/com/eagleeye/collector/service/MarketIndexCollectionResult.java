package com.eagleeye.collector.service;

import java.time.YearMonth;

public record MarketIndexCollectionResult(
        YearMonth yearMonth,
        int barsCount,
        CollectionStatus status,
        String errorMessage
) {
    public static MarketIndexCollectionResult collected(YearMonth yearMonth, int barsCount) {
        return new MarketIndexCollectionResult(yearMonth, barsCount, CollectionStatus.COLLECTED, null);
    }

    public static MarketIndexCollectionResult noData(YearMonth yearMonth) {
        return new MarketIndexCollectionResult(yearMonth, 0, CollectionStatus.NO_DATA, null);
    }

    public static MarketIndexCollectionResult error(YearMonth yearMonth, String message) {
        return new MarketIndexCollectionResult(yearMonth, 0, CollectionStatus.ERROR, message);
    }

    public boolean isTradeMonth() { return status == CollectionStatus.COLLECTED; }
}
