package com.eagleeye.collector.runner;

import com.eagleeye.collector.collector.ScheduledCollector;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyCollectionRunnerTest {

    private static ScheduledCollector collector(String name, LocalTime scheduledAt) {
        ScheduledCollector c = mock(ScheduledCollector.class);
        when(c.name()).thenReturn(name);
        when(c.scheduledAt()).thenReturn(scheduledAt);
        return c;
    }

    private final List<ScheduledCollector> allCollectors = List.of(
            collector("TAIEX",  LocalTime.of(15,  5)),
            collector("IFLOW",  LocalTime.of(15, 15)),
            collector("TAIFEX", LocalTime.of(15, 30)),
            collector("MARGIN", LocalTime.of(21, 35))
    );

    @Test
    void selectsNoCollector_beforeFirstScheduledTime() {
        Optional<ScheduledCollector> result = DailyCollectionRunner.selectCollector(allCollectors, LocalTime.of(14, 0));
        assertThat(result).isEmpty();
    }

    @Test
    void selectsFirstCollector_exactlyAtItsTime() {
        Optional<ScheduledCollector> result = DailyCollectionRunner.selectCollector(allCollectors, LocalTime.of(15, 5));
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("TAIEX");
    }

    @Test
    void selectsFirstCollector_betweenFirstAndSecond() {
        Optional<ScheduledCollector> result = DailyCollectionRunner.selectCollector(allCollectors, LocalTime.of(15, 10));
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("TAIEX");
    }

    @Test
    void selectsSecondCollector_exactlyAtItsTime() {
        Optional<ScheduledCollector> result = DailyCollectionRunner.selectCollector(allCollectors, LocalTime.of(15, 15));
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("IFLOW");
    }

    @Test
    void selectsThirdCollector_exactlyAtItsTime() {
        Optional<ScheduledCollector> result = DailyCollectionRunner.selectCollector(allCollectors, LocalTime.of(15, 30));
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("TAIFEX");
    }

    @Test
    void selectsLastCollector_afterAllScheduledTimes() {
        Optional<ScheduledCollector> result = DailyCollectionRunner.selectCollector(allCollectors, LocalTime.of(21, 40));
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("MARGIN");
    }

    @Test
    void selectsCorrectCollector_regardlessOfRegistrationOrder() {
        // Deliberately out of order
        List<ScheduledCollector> shuffled = List.of(
                collector("MARGIN", LocalTime.of(21, 35)),
                collector("TAIEX",  LocalTime.of(15,  5)),
                collector("TAIFEX", LocalTime.of(15, 30)),
                collector("IFLOW",  LocalTime.of(15, 15))
        );
        Optional<ScheduledCollector> result = DailyCollectionRunner.selectCollector(shuffled, LocalTime.of(15, 20));
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("IFLOW");
    }

    @Test
    void selectsNoCollector_fromEmptyList() {
        Optional<ScheduledCollector> result = DailyCollectionRunner.selectCollector(List.of(), LocalTime.of(23, 0));
        assertThat(result).isEmpty();
    }
}
