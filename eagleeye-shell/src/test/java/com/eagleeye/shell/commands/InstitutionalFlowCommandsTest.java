package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import com.eagleeye.shell.formatter.TableFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitutionalFlowCommandsTest {

    @Mock private InstitutionalFlowService service;
    @Mock private InstitutionalFlowRepository repository;
    @Mock private TableFormatter formatter;

    private InstitutionalFlowCommands commands;

    @BeforeEach
    void setUp() {
        commands = new InstitutionalFlowCommands(service, repository, formatter);
    }

    // ── institutional-flow list ───────────────────────────────────────────────

    @Test
    void list_defaultsToToday() {
        when(repository.findByTradeDate(LocalDate.now())).thenReturn(Optional.empty());
        commands.list("");
        verify(repository).findByTradeDate(LocalDate.now());
    }

    @Test
    void list_parsesExplicitDate() {
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 19))).thenReturn(Optional.empty());
        commands.list("2026-03-19");
        verify(repository).findByTradeDate(LocalDate.of(2026, 3, 19));
    }

    @Test
    void list_flowFound_returnsFormattedTable() {
        InstitutionalFlow flow = new InstitutionalFlow(LocalDate.of(2026, 3, 19));
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 19))).thenReturn(Optional.of(flow));
        when(formatter.formatInstitutionalFlow(List.of(flow))).thenReturn("rendered");

        String result = commands.list("2026-03-19");
        assertThat(result).contains("rendered");
    }

    @Test
    void list_noFlowFound_returnsNoData() {
        when(repository.findByTradeDate(any())).thenReturn(Optional.empty());
        String result = commands.list("2026-03-19");
        assertThat(result).containsIgnoringCase("no data");
    }

    // ── institutional-flow show ───────────────────────────────────────────────

    @Test
    void show_defaultRange_queriesLast30Days() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of());
        when(formatter.formatInstitutionalFlow(any())).thenReturn("table");

        commands.show("", "");

        LocalDate today = LocalDate.now();
        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(today.minusDays(30), today);
    }

    @Test
    void show_explicitRange_queriesGivenRange() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of());
        when(formatter.formatInstitutionalFlow(any())).thenReturn("table");

        commands.show("2026-01-01", "2026-03-19");

        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 19));
    }

    @Test
    void show_returnsFormattedTableWithRowCount() {
        InstitutionalFlow flow = new InstitutionalFlow(LocalDate.of(2026, 3, 19));
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of(flow));
        when(formatter.formatInstitutionalFlow(List.of(flow))).thenReturn("rendered-table");

        String result = commands.show("2026-03-01", "2026-03-19");
        assertThat(result).contains("rendered-table");
        assertThat(result).contains("1");
    }

    // ── institutional-flow collect ────────────────────────────────────────────

    @Test
    void collect_defaultsToToday() {
        when(service.collectDate(any()))
                .thenReturn(DateCollectionResult.collected(LocalDate.now()));

        commands.collect("");

        verify(service).collectDate(LocalDate.now());
    }

    @Test
    void collect_collected_containsCollected() {
        when(service.collectDate(any()))
                .thenReturn(DateCollectionResult.collected(LocalDate.of(2026, 3, 19)));

        String result = commands.collect("2026-03-19");
        assertThat(result).containsIgnoringCase("collected");
    }

    @Test
    void collect_noData_containsNoData() {
        when(service.collectDate(any()))
                .thenReturn(DateCollectionResult.noData(LocalDate.of(2026, 3, 19)));

        String result = commands.collect("2026-03-19");
        assertThat(result).containsIgnoringCase("no data");
    }

    // ── institutional-flow backfill ───────────────────────────────────────────

    @Test
    void backfill_skipsWeekends_onlyCallsServiceOnWeekdays() {
        // 2026-03-06 (Fri) to 2026-03-09 (Mon) = 2 weekdays
        when(service.collectDate(any()))
                .thenReturn(DateCollectionResult.collected(LocalDate.now()));

        commands.backfill("2026-03-06", "2026-03-09");

        verify(service, times(2)).collectDate(any());
        verify(service).collectDate(LocalDate.of(2026, 3, 6)); // Fri
        verify(service).collectDate(LocalDate.of(2026, 3, 9)); // Mon
        verify(service, never()).collectDate(LocalDate.of(2026, 3, 7)); // Sat
        verify(service, never()).collectDate(LocalDate.of(2026, 3, 8)); // Sun
    }

    @Test
    void backfill_singleDay_returnsResult() {
        when(service.collectDate(LocalDate.of(2026, 3, 19)))
                .thenReturn(DateCollectionResult.collected(LocalDate.of(2026, 3, 19)));

        String result = commands.backfill("2026-03-19", "2026-03-19");
        assertThat(result).containsIgnoringCase("collected");
    }
}
