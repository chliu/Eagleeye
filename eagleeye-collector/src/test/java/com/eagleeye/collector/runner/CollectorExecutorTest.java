package com.eagleeye.collector.runner;

import com.eagleeye.collector.collector.CollectorOutcome;
import com.eagleeye.collector.collector.ScheduledCollector;
import com.eagleeye.collector.service.CollectionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class CollectorExecutorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 27);

    private record FakeCollector(String name, Supplier<CollectorOutcome> body) implements ScheduledCollector {
        @Override public CollectorOutcome collect(LocalDate date) { return body.get(); }
    }

    @Test
    void returnsTheCollectorOutcome_onSuccess() {
        CollectorOutcome outcome = new CollectorExecutor()
                .run(new FakeCollector("X", () -> CollectorOutcome.collected("bars: 3")), DATE);

        assertThat(outcome.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(outcome.detail()).isEqualTo("bars: 3");
    }

    @Test
    void unexpectedException_isMappedToErrorOutcome_notPropagated() {
        CollectorOutcome outcome = new CollectorExecutor()
                .run(new FakeCollector("X", () -> { throw new RuntimeException("boom"); }), DATE);

        assertThat(outcome.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(outcome.detail()).contains("boom");
    }
}
