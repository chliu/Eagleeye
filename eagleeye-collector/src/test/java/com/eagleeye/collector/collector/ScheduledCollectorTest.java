package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledCollectorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 27);

    @Mock MarketIndexService       marketIndexService;
    @Mock InstitutionalFlowService institutionalFlowService;
    @Mock CollectionService        collectionService;
    @Mock MarginTransactionService marginTransactionService;

    // ── MarketIndexCollector ──────────────────────────────────────────────────

    @Test
    void marketIndex_scheduledAt_15_05() {
        assertThat(new MarketIndexCollector(marketIndexService).scheduledAt())
                .isEqualTo(LocalTime.of(15, 5));
    }

    @Test
    void marketIndex_collected_returnsBarCount() {
        when(marketIndexService.collectMonth(YearMonth.from(DATE)))
                .thenReturn(MarketIndexCollectionResult.collected(YearMonth.from(DATE), 18));

        CollectResult result = new MarketIndexCollector(marketIndexService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("bars: 18");
    }

    @Test
    void marketIndex_noData_returnsNoData() {
        when(marketIndexService.collectMonth(any()))
                .thenReturn(MarketIndexCollectionResult.noData(YearMonth.from(DATE)));

        CollectResult result = new MarketIndexCollector(marketIndexService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void marketIndex_error_returnsError() {
        when(marketIndexService.collectMonth(any()))
                .thenReturn(MarketIndexCollectionResult.error(YearMonth.from(DATE), "timeout"));

        CollectResult result = new MarketIndexCollector(marketIndexService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("timeout");
    }

    // ── InstitutionalFlowCollector ────────────────────────────────────────────

    @Test
    void iflow_scheduledAt_15_15() {
        assertThat(new InstitutionalFlowCollector(institutionalFlowService).scheduledAt())
                .isEqualTo(LocalTime.of(15, 15));
    }

    @Test
    void iflow_collected_returnsOk() {
        when(institutionalFlowService.collectDate(DATE))
                .thenReturn(DateCollectionResult.collected(DATE));

        CollectResult result = new InstitutionalFlowCollector(institutionalFlowService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("ok");
    }

    @Test
    void iflow_noData_returnsNoData() {
        when(institutionalFlowService.collectDate(DATE))
                .thenReturn(DateCollectionResult.noData(DATE));

        assertThat(new InstitutionalFlowCollector(institutionalFlowService).collect(DATE).status())
                .isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void iflow_error_returnsError() {
        when(institutionalFlowService.collectDate(DATE))
                .thenReturn(DateCollectionResult.error(DATE, "connection refused"));

        CollectResult result = new InstitutionalFlowCollector(institutionalFlowService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("connection refused");
    }

    // ── TaifexOiCollector ─────────────────────────────────────────────────────

    @Test
    void taifex_scheduledAt_15_30() {
        assertThat(new TaifexOiCollector(collectionService).scheduledAt())
                .isEqualTo(LocalTime.of(15, 30));
    }

    @Test
    void taifex_collected_returnsFuturesAndOptionsCount() {
        when(collectionService.collectAll(DATE))
                .thenReturn(CollectionResult.collected(DATE, 9, 12));

        CollectResult result = new TaifexOiCollector(collectionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("futures: 9  options: 12");
    }

    @Test
    void taifex_noData_returnsNoData() {
        when(collectionService.collectAll(DATE))
                .thenReturn(CollectionResult.noData(DATE));

        assertThat(new TaifexOiCollector(collectionService).collect(DATE).status())
                .isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void taifex_error_returnsError() {
        when(collectionService.collectAll(DATE))
                .thenReturn(CollectionResult.error(DATE, "parse failed"));

        CollectResult result = new TaifexOiCollector(collectionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("parse failed");
    }

    // ── MarginCollector ───────────────────────────────────────────────────────

    @Test
    void margin_scheduledAt_21_35() {
        assertThat(new MarginCollector(marginTransactionService).scheduledAt())
                .isEqualTo(LocalTime.of(21, 35));
    }

    @Test
    void margin_collected_returnsOk() {
        when(marginTransactionService.collectDate(DATE))
                .thenReturn(DateCollectionResult.collected(DATE));

        CollectResult result = new MarginCollector(marginTransactionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("ok");
    }

    @Test
    void margin_noData_returnsNoData() {
        when(marginTransactionService.collectDate(DATE))
                .thenReturn(DateCollectionResult.noData(DATE));

        assertThat(new MarginCollector(marginTransactionService).collect(DATE).status())
                .isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void margin_error_returnsError() {
        when(marginTransactionService.collectDate(DATE))
                .thenReturn(DateCollectionResult.error(DATE, "HTTP 503"));

        CollectResult result = new MarginCollector(marginTransactionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("HTTP 503");
    }
}
