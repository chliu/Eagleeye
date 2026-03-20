package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import com.eagleeye.domain.entity.TaiexIndex;
import com.eagleeye.domain.repository.TaiexIndexRepository;
import com.eagleeye.shell.formatter.TableFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketIndexCommandsTest {

    @Mock private MarketIndexService marketIndexService;
    @Mock private TaiexIndexRepository repository;
    @Mock private TableFormatter formatter;

    private MarketIndexCommands commands;

    @BeforeEach
    void setUp() {
        commands = new MarketIndexCommands(marketIndexService, repository, formatter);
    }

    // ── market-index list ───────────────────────────────────────────────────────

    @Test
    void list_defaultsToToday() {
        when(repository.findByTradeDate(LocalDate.now())).thenReturn(Optional.empty());

        commands.list("");

        verify(repository).findByTradeDate(LocalDate.now());
    }

    @Test
    void list_parsesExplicitDate() {
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 3))).thenReturn(Optional.empty());

        commands.list("2026-03-03");

        verify(repository).findByTradeDate(LocalDate.of(2026, 3, 3));
    }

    @Test
    void list_barFound_returnsFormattedTable() {
        TaiexIndex bar = new TaiexIndex(LocalDate.of(2026, 3, 3));
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 3))).thenReturn(Optional.of(bar));
        when(formatter.formatMarketIndex(List.of(bar))).thenReturn("rendered");

        String result = commands.list("2026-03-03");

        assertThat(result).contains("rendered");
    }

    @Test
    void list_noBarFound_returnsNoData() {
        when(repository.findByTradeDate(any())).thenReturn(Optional.empty());

        String result = commands.list("2026-03-03");

        assertThat(result).containsIgnoringCase("no data");
    }

    // ── market-index show ───────────────────────────────────────────────────────

    @Test
    void show_defaultRange_queriesLast30Days() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
                .thenReturn(List.of());
        when(formatter.formatMarketIndex(any())).thenReturn("table");

        commands.show("", "");

        LocalDate today = LocalDate.now();
        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(today.minusDays(30), today);
    }

    @Test
    void show_explicitRange_queriesGivenRange() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
                .thenReturn(List.of());
        when(formatter.formatMarketIndex(any())).thenReturn("table");

        commands.show("2026-01-01", "2026-03-18");

        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 18));
    }

    @Test
    void show_returnsFormattedTableWithHeader() {
        TaiexIndex bar = new TaiexIndex(LocalDate.of(2026, 3, 3));
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
                .thenReturn(List.of(bar));
        when(formatter.formatMarketIndex(List.of(bar))).thenReturn("rendered-table");

        String result = commands.show("2026-03-01", "2026-03-18");

        assertThat(result).contains("rendered-table");
        assertThat(result).contains("1"); // row count
    }

    // ── market-index collect ────────────────────────────────────────────────────

    @Test
    void collect_defaultsToToday() {
        when(marketIndexService.collectDate(any()))
                .thenReturn(MarketIndexCollectionResult.collected(YearMonth.now(), 20));

        commands.collect("");

        verify(marketIndexService).collectDate(LocalDate.now());
    }
}
