package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
    @Mock FuturesMarketOiService   futuresMarketOiService;

    // ── MarketIndexCollector ──────────────────────────────────────────────────

    @Test
    void marketIndex_collected_returnsBarCount() {
        when(marketIndexService.collectMonth(YearMonth.from(DATE)))
                .thenReturn(new MarketIndexCollectionResult.Collected(YearMonth.from(DATE), 18));

        CollectorOutcome result = new MarketIndexCollector(marketIndexService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("bars: 18");
    }

    @Test
    void marketIndex_noData_returnsNoData() {
        when(marketIndexService.collectMonth(any()))
                .thenReturn(new MarketIndexCollectionResult.NoData(YearMonth.from(DATE)));

        CollectorOutcome result = new MarketIndexCollector(marketIndexService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void marketIndex_error_returnsError() {
        when(marketIndexService.collectMonth(any()))
                .thenReturn(new MarketIndexCollectionResult.Error(YearMonth.from(DATE), "timeout"));

        CollectorOutcome result = new MarketIndexCollector(marketIndexService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("timeout");
    }

    // ── InstitutionalFlowCollector ────────────────────────────────────────────

    @Test
    void iflow_collected_returnsOk() {
        when(institutionalFlowService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Collected(DATE));

        CollectorOutcome result = new InstitutionalFlowCollector(institutionalFlowService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("ok");
    }

    @Test
    void iflow_noData_returnsNoData() {
        when(institutionalFlowService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.NoData(DATE));

        assertThat(new InstitutionalFlowCollector(institutionalFlowService).collect(DATE).status())
                .isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void iflow_error_returnsError() {
        when(institutionalFlowService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Error(DATE, "connection refused"));

        CollectorOutcome result = new InstitutionalFlowCollector(institutionalFlowService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("connection refused");
    }

    // ── TaifexOiCollector ─────────────────────────────────────────────────────

    @Test
    void taifex_collected_returnsFuturesAndOptionsCount() {
        when(collectionService.collectAll(DATE))
                .thenReturn(new FuturesOptionsCollectionResult.Collected(DATE, 9, 12));

        CollectorOutcome result = new TaifexOiCollector(collectionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("futures: 9  options: 12");
    }

    @Test
    void taifex_noData_returnsNoData() {
        when(collectionService.collectAll(DATE))
                .thenReturn(new FuturesOptionsCollectionResult.NoData(DATE));

        assertThat(new TaifexOiCollector(collectionService).collect(DATE).status())
                .isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void taifex_error_returnsError() {
        when(collectionService.collectAll(DATE))
                .thenReturn(new FuturesOptionsCollectionResult.Error(DATE, "parse failed"));

        CollectorOutcome result = new TaifexOiCollector(collectionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("parse failed");
    }

    // ── MarginCollector ───────────────────────────────────────────────────────

    @Test
    void margin_collected_returnsOk() {
        when(marginTransactionService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Collected(DATE));

        CollectorOutcome result = new MarginCollector(marginTransactionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("ok");
    }

    @Test
    void margin_noData_returnsNoData() {
        when(marginTransactionService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.NoData(DATE));

        assertThat(new MarginCollector(marginTransactionService).collect(DATE).status())
                .isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void margin_error_returnsError() {
        when(marginTransactionService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Error(DATE, "HTTP 503"));

        CollectorOutcome result = new MarginCollector(marginTransactionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("HTTP 503");
    }

    // ── TaifexMarketOiCollector ───────────────────────────────────────────────

    @Test
    void marketOi_collected_returnsOk() {
        when(futuresMarketOiService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Collected(DATE));

        CollectorOutcome result = new TaifexMarketOiCollector(futuresMarketOiService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("ok");
    }

    @Test
    void marketOi_noData_returnsNoData() {
        when(futuresMarketOiService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.NoData(DATE));

        assertThat(new TaifexMarketOiCollector(futuresMarketOiService).collect(DATE).status())
                .isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void marketOi_error_returnsError() {
        when(futuresMarketOiService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Error(DATE, "parse failed"));

        CollectorOutcome result = new TaifexMarketOiCollector(futuresMarketOiService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("parse failed");
    }
}
