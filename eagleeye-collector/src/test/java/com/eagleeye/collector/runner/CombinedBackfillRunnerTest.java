package com.eagleeye.collector.runner;

import com.eagleeye.collector.service.CollectionResult;
import com.eagleeye.collector.service.CollectionService;
import com.eagleeye.collector.service.InstitutionalFlowResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.collector.service.MarginCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CombinedBackfillRunnerTest {

    @Mock private MarketIndexService marketIndexService;
    @Mock private CollectionService collectionService;
    @Mock private MarginTransactionService marginTransactionService;
    @Mock private InstitutionalFlowService institutionalFlowService;

    private CombinedBackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new CombinedBackfillRunner(marketIndexService, collectionService, null,
                marginTransactionService, institutionalFlowService, 0);
    }

    // ── Market index: once per month ────────────────────────────────────────────

    @Test
    void executeBackfill_collectsMarketIndexOnceForSingleMonth() throws Exception {
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 7));

        verify(marketIndexService, times(1)).collectMonth(YearMonth.of(2026, 3));
    }

    @Test
    void executeBackfill_collectsMarketIndexOncePerMonthAcrossRange() throws Exception {
        stubMarketIndex(YearMonth.of(2026, 1));
        stubMarketIndex(YearMonth.of(2026, 2));
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        verify(marketIndexService, times(1)).collectMonth(YearMonth.of(2026, 1));
        verify(marketIndexService, times(1)).collectMonth(YearMonth.of(2026, 2));
        verify(marketIndexService, times(1)).collectMonth(YearMonth.of(2026, 3));
        verify(marketIndexService, times(3)).collectMonth(any());
    }

    // ── TAIFEX: every weekday in range ──────────────────────────────────────────

    @Test
    void executeBackfill_collectsTaifexForEachWeekdayInRange() throws Exception {
        // 2026-03-03 (Tue) to 2026-03-06 (Fri) = 4 weekdays
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 6));

        verify(collectionService, times(4)).collectAll(any(LocalDate.class));
        verify(collectionService).collectAll(LocalDate.of(2026, 3, 3));
        verify(collectionService).collectAll(LocalDate.of(2026, 3, 4));
        verify(collectionService).collectAll(LocalDate.of(2026, 3, 5));
        verify(collectionService).collectAll(LocalDate.of(2026, 3, 6));
    }

    @Test
    void executeBackfill_skipsWeekends() throws Exception {
        // 2026-03-06 (Fri) to 2026-03-09 (Mon) = Fri + Mon = 2 weekdays, Sat/Sun skipped
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 9));

        verify(collectionService, times(2)).collectAll(any(LocalDate.class));
        verify(collectionService).collectAll(LocalDate.of(2026, 3, 6));
        verify(collectionService).collectAll(LocalDate.of(2026, 3, 9));
        verify(collectionService, never()).collectAll(LocalDate.of(2026, 3, 7)); // Sat
        verify(collectionService, never()).collectAll(LocalDate.of(2026, 3, 8)); // Sun
    }

    // ── Month boundaries ────────────────────────────────────────────────────────

    @Test
    void executeBackfill_taifexOnlyWithinGivenRange() throws Exception {
        // Range starts mid-month: only days from 2026-03-25 onward collected
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        // 2026-03-25 (Wed) to 2026-03-26 (Thu) = 2 days
        runner.executeBackfill(LocalDate.of(2026, 3, 25), LocalDate.of(2026, 3, 26));

        verify(collectionService, times(2)).collectAll(any(LocalDate.class));
        verify(collectionService, never()).collectAll(LocalDate.of(2026, 3, 24));
        verify(collectionService, never()).collectAll(LocalDate.of(2026, 3, 27));
    }

    // ── Margin: every weekday in range ──────────────────────────────────────────

    @Test
    void executeBackfill_collectsMarginForEachWeekday() throws Exception {
        // 2026-03-03 (Tue) to 2026-03-06 (Fri) = 4 weekdays
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 6));

        verify(marginTransactionService, times(4)).collectDate(any(LocalDate.class));
        verify(marginTransactionService).collectDate(LocalDate.of(2026, 3, 3));
        verify(marginTransactionService).collectDate(LocalDate.of(2026, 3, 4));
        verify(marginTransactionService).collectDate(LocalDate.of(2026, 3, 5));
        verify(marginTransactionService).collectDate(LocalDate.of(2026, 3, 6));
    }

    @Test
    void executeBackfill_skipsMarginOnWeekends() throws Exception {
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 9));

        verify(marginTransactionService, times(2)).collectDate(any(LocalDate.class));
        verify(marginTransactionService, never()).collectDate(LocalDate.of(2026, 3, 7)); // Sat
        verify(marginTransactionService, never()).collectDate(LocalDate.of(2026, 3, 8)); // Sun
    }

    // ── Institutional flow: every weekday in range ───────────────────────────

    @Test
    void executeBackfill_collectsFlowForEachWeekday() throws Exception {
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 6));

        verify(institutionalFlowService, times(4)).collectDate(any(LocalDate.class));
        verify(institutionalFlowService).collectDate(LocalDate.of(2026, 3, 3));
        verify(institutionalFlowService).collectDate(LocalDate.of(2026, 3, 4));
        verify(institutionalFlowService).collectDate(LocalDate.of(2026, 3, 5));
        verify(institutionalFlowService).collectDate(LocalDate.of(2026, 3, 6));
    }

    @Test
    void executeBackfill_skipsFlowOnWeekends() throws Exception {
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 9));

        verify(institutionalFlowService, times(2)).collectDate(any(LocalDate.class));
        verify(institutionalFlowService, never()).collectDate(LocalDate.of(2026, 3, 7));
        verify(institutionalFlowService, never()).collectDate(LocalDate.of(2026, 3, 8));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void stubMarketIndex(YearMonth ym) {
        when(marketIndexService.collectMonth(ym))
                .thenReturn(MarketIndexCollectionResult.collected(ym, 20));
    }

    private void stubTaifex() {
        when(collectionService.collectAll(any(LocalDate.class)))
                .thenReturn(CollectionResult.collected(LocalDate.now(), 10, 10));
    }

    private void stubMargin() {
        when(marginTransactionService.collectDate(any(LocalDate.class)))
                .thenReturn(MarginCollectionResult.collected(LocalDate.now()));
    }

    private void stubFlow() {
        when(institutionalFlowService.collectDate(any(LocalDate.class)))
                .thenReturn(InstitutionalFlowResult.collected(LocalDate.now()));
    }
}
