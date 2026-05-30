package com.eagleeye.collector.runner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CollectorDispatchRunnerTest {

    private static final Set<String> KNOWN = Set.of("FUTAH", "TAIEX", "IFLOW", "TAIFEX", "MARGIN");

    @Test
    void singleName_resolvesToThatCollector() {
        assertThat(CollectorDispatchRunner.resolveNames(List.of("MARGIN"), KNOWN))
                .containsExactly("MARGIN");
    }

    @Test
    void csvList_resolvesToEachName_inOrder() {
        assertThat(CollectorDispatchRunner.resolveNames(List.of("TAIEX,IFLOW,TAIFEX"), KNOWN))
                .containsExactly("TAIEX", "IFLOW", "TAIFEX");
    }

    @Test
    void name_isCaseInsensitive() {
        assertThat(CollectorDispatchRunner.resolveNames(List.of("margin"), KNOWN))
                .containsExactly("MARGIN");
    }

    @Test
    void all_expandsToEveryKnownCollector() {
        assertThat(CollectorDispatchRunner.resolveNames(List.of("ALL"), KNOWN))
                .containsExactlyInAnyOrderElementsOf(KNOWN);
    }

    @Test
    void duplicates_areCollapsed() {
        assertThat(CollectorDispatchRunner.resolveNames(List.of("MARGIN", "MARGIN"), KNOWN))
                .containsExactly("MARGIN");
    }

    @Test
    void noArgs_resolvesToEmpty() {
        assertThat(CollectorDispatchRunner.resolveNames(List.of(), KNOWN)).isEmpty();
        assertThat(CollectorDispatchRunner.resolveNames(null, KNOWN)).isEmpty();
    }

    @Test
    void unknownName_isPassedThrough_forCallerToReject() {
        // resolveNames parses intent; the runner reports unknowns at lookup time.
        assertThat(CollectorDispatchRunner.resolveNames(List.of("BOGUS"), KNOWN))
                .containsExactly("BOGUS");
    }
}
