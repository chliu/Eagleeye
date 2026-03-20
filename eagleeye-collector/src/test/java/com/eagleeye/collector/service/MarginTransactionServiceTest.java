package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
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
    @Mock private TwseParser twseParser;
    @Mock private MarginTransactionRepository repository;

    private MarginTransactionService service;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 18);
    private static final String MARGIN_JSON = "{\"stat\":\"OK\",\"data\":[[\"row0\",\"1\",\"2\",\"3\",\"4\",\"5\"],[\"row1\",\"6\",\"7\",\"8\",\"9\",\"10\"]]}";

    @BeforeEach
    void setUp() {
        service = new MarginTransactionService(twseClient, twseParser, repository);
    }

    @Test
    void collectDate_success_savesAndReturnsCollected() {
        MarginTransaction bar = new MarginTransaction(DATE);
        when(twseClient.fetchMarginJson(DATE)).thenReturn(MARGIN_JSON);
        when(twseParser.parseMargin(MARGIN_JSON, DATE)).thenReturn(bar);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.empty());

        MarginCollectionResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(MarginCollectionResult.Status.COLLECTED);
        assertThat(result.tradeDate()).isEqualTo(DATE);
        verify(repository).save(any(MarginTransaction.class));
    }

    @Test
    void collectDate_noData_returnsNoData() {
        when(twseClient.fetchMarginJson(DATE)).thenReturn("{\"stat\":\"NO DATA\"}");
        when(twseParser.parseMargin(any(), eq(DATE))).thenReturn(null);

        MarginCollectionResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(MarginCollectionResult.Status.NO_DATA);
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_clientThrows_returnsError() {
        when(twseClient.fetchMarginJson(DATE)).thenThrow(new RuntimeException("timeout"));

        MarginCollectionResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(MarginCollectionResult.Status.ERROR);
        assertThat(result.errorMessage()).contains("timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_existingBar_upserts() {
        MarginTransaction existing = new MarginTransaction(DATE);
        MarginTransaction parsed   = new MarginTransaction(DATE);
        parsed.setMarginBalance(8_109_024L);
        parsed.setShortBalance(204_948L);

        when(twseClient.fetchMarginJson(DATE)).thenReturn(MARGIN_JSON);
        when(twseParser.parseMargin(MARGIN_JSON, DATE)).thenReturn(parsed);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.of(existing));

        service.collectDate(DATE);

        ArgumentCaptor<MarginTransaction> captor = ArgumentCaptor.forClass(MarginTransaction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMarginBalance()).isEqualTo(8_109_024L);
        assertThat(captor.getValue().getShortBalance()).isEqualTo(204_948L);
    }
}
