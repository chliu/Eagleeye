package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.CollectionStatus;

public record CollectorOutcome(CollectionStatus status, String detail) {

    public static CollectorOutcome collected(String detail) {
        return new CollectorOutcome(CollectionStatus.COLLECTED, detail);
    }

    public static CollectorOutcome noData() {
        return new CollectorOutcome(CollectionStatus.NO_DATA, "no data");
    }

    public static CollectorOutcome error(String message) {
        return new CollectorOutcome(CollectionStatus.ERROR, "ERROR: " + message);
    }
}
