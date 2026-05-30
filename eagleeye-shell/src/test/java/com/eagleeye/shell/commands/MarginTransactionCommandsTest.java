package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.repository.MarginTransactionRepository;
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
class MarginTransactionCommandsTest {

    @Mock private MarginTransactionService marginTransactionService;
    @Mock private MarginTransactionRepository repository;
    @Mock private TableFormatter formatter;

    private MarginTransactionCommands commands;

    @BeforeEach
    void setUp() {
        commands = new MarginTransactionCommands(marginTransactionService, repository, formatter);
    }

    // ── margin list ──────────────────────────────────────────────────────────

    @Test
    void list_defaultsToToday() {
        when(repository.findByTradeDate(LocalDate.now())).thenReturn(Optional.empty());
        commands.list("");
        verify(repository).findByTradeDate(LocalDate.now());
    }

    @Test
    void list_parsesExplicitDate() {
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 18))).thenReturn(Optional.empty());
        commands.list("2026-03-18");
        verify(repository).findByTradeDate(LocalDate.of(2026, 3, 18));
    }

    @Test
    void list_recordFound_returnsFormattedTable() {
        MarginTransaction tx = new MarginTransaction(LocalDate.of(2026, 3, 18));
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 18))).thenReturn(Optional.of(tx));
        when(formatter.formatMarginTransaction(List.of(tx))).thenReturn("rendered");

        String result = commands.list("2026-03-18");
        assertThat(result).contains("rendered");
    }

    @Test
    void list_noRecordFound_returnsNoData() {
        when(repository.findByTradeDate(any())).thenReturn(Optional.empty());
        String result = commands.list("2026-03-18");
        assertThat(result).containsIgnoringCase("no data");
    }

    // ── margin show ──────────────────────────────────────────────────────────

    @Test
    void show_defaultRange_queriesLast30Days() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of());
        when(formatter.formatMarginTransaction(any())).thenReturn("table");

        commands.show("", "");

        LocalDate today = LocalDate.now();
        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(today.minusDays(30), today);
    }

    @Test
    void show_explicitRange_queriesGivenRange() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of());
        when(formatter.formatMarginTransaction(any())).thenReturn("table");

        commands.show("2026-01-01", "2026-03-18");

        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 18));
    }

    @Test
    void show_returnsFormattedTableWithRowCount() {
        MarginTransaction tx = new MarginTransaction(LocalDate.of(2026, 3, 18));
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of(tx));
        when(formatter.formatMarginTransaction(List.of(tx))).thenReturn("rendered-table");

        String result = commands.show("2026-03-01", "2026-03-18");
        assertThat(result).contains("rendered-table");
        assertThat(result).contains("1"); // row count
    }

    // ── margin collect ───────────────────────────────────────────────────────

    @Test
    void collect_defaultsToToday() {
        when(marginTransactionService.collectDate(any()))
                .thenReturn(new DateCollectionResult.Collected(LocalDate.now()));

        commands.collect("");

        verify(marginTransactionService).collectDate(LocalDate.now());
    }
}
