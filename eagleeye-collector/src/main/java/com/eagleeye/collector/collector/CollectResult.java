package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.CollectionStatus;

public record CollectResult(CollectionStatus status, String detail) {

    public static CollectResult collected(String detail) {
        return new CollectResult(CollectionStatus.COLLECTED, detail);
    }

    public static CollectResult noData() {
        return new CollectResult(CollectionStatus.NO_DATA, "no data");
    }

    public static CollectResult error(String message) {
        return new CollectResult(CollectionStatus.ERROR, "ERROR: " + message);
    }
}
