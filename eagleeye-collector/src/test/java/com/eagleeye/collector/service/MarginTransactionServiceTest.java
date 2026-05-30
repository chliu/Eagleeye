package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.MarginTransactionParser;
import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.repository.MarginTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarginTransactionServiceTest {

    @Mock private TwseClient twseClient;
    @Mock private MarginTransactionParser marginParser;
    @Mock private MarginTransactionRepository repository;

    private MarginTransactionService service;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 18);
    private static final String MARGIN_JSON = "{\"stat\":\"OK\",\"data\":[[\"row0\",\"1\",\"2\",\"3\",\"4\",\"5\"],[\"row1\",\"6\",\"7\",\"8\",\"9\",\"10\"]]}";

    @BeforeEach
    void setUp() {
        service = new MarginTransactionService(twseClient, marginParser, repository);
    }

    @Test
    void collectDate_success_savesAndReturnsCollected() {
        MarginTransaction tx = new MarginTransaction(DATE);
        when(twseClient.fetchMarginJson(DATE)).thenReturn(MARGIN_JSON);
        when(marginParser.parse(MARGIN_JSON, DATE)).thenReturn(tx);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.empty());

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
        assertThat(result.tradeDate()).isEqualTo(DATE);
        verify(repository).save(any(MarginTransaction.class));
    }

    @Test
    void collectDate_noData_returnsNoData() {
        when(twseClient.fetchMarginJson(DATE)).thenReturn("{\"stat\":\"NO DATA\"}");
        when(marginParser.parse(any(), eq(DATE))).thenReturn(null);

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_clientThrows_returnsError() {
        when(twseClient.fetchMarginJson(DATE)).thenThrow(new RuntimeException("timeout"));

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
        DateCollectionResult.Error error = (DateCollectionResult.Error) result;
        assertThat(error.message()).contains("timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_existingRecord_upserts() {
        MarginTransaction existing = new MarginTransaction(DATE);
        MarginTransaction parsed   = new MarginTransaction(DATE);
        parsed.setMarginBalance(8_109_024L);
        parsed.setShortBalance(204_948L);

        when(twseClient.fetchMarginJson(DATE)).thenReturn(MARGIN_JSON);
        when(marginParser.parse(MARGIN_JSON, DATE)).thenReturn(parsed);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.of(existing));

        service.collectDate(DATE);

        ArgumentCaptor<MarginTransaction> captor = ArgumentCaptor.forClass(MarginTransaction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMarginBalance()).isEqualTo(8_109_024L);
        assertThat(captor.getValue().getShortBalance()).isEqualTo(204_948L);
    }
}
