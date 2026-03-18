package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
import com.eagleeye.domain.entity.TaiexDailyBar;
import com.eagleeye.domain.repository.TaiexDailyBarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketIndexServiceTest {

    @Mock
    private TwseClient twseClient;

    @Mock
    private TaiexDailyBarRepository repository;

    private MarketIndexService service;

    private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);

    // Use Jackson 3.x ObjectMapper for the real parser
    private static final String ONE_BAR_JSON = """
            {
              "stat": "OK",
              "data": [
                ["115/03/03", "20,234.56", "20,456.78", "20,100.23", "20,300.45", "3,456,789", "123,456,789,012"]
              ]
            }
            """;

    private static final String EMPTY_JSON = """
            { "stat": "OK", "data": [] }
            """;

    @BeforeEach
    void setUp() {
        // Use real TwseParser with Jackson 3.x ObjectMapper
        TwseParser realParser = new TwseParser(new tools.jackson.databind.ObjectMapper());
        service = new MarketIndexService(twseClient, realParser, repository);
    }

    @Test
    void collectMonth_whenDataPresent_upsertsBarAndReturnsCollected() {
        when(twseClient.fetchMonthJson(MARCH_2026)).thenReturn(ONE_BAR_JSON);
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 3))).thenReturn(Optional.empty());

        MarketIndexCollectionResult result = service.collectMonth(MARCH_2026);

        assertThat(result.status()).isEqualTo(MarketIndexCollectionResult.Status.COLLECTED);
        assertThat(result.barsCount()).isEqualTo(1);
        assertThat(result.yearMonth()).isEqualTo(MARCH_2026);
        verify(repository, times(1)).save(any(TaiexDailyBar.class));
    }

    @Test
    void collectMonth_whenNoData_returnsNoData() {
        when(twseClient.fetchMonthJson(MARCH_2026)).thenReturn(EMPTY_JSON);

        MarketIndexCollectionResult result = service.collectMonth(MARCH_2026);

        assertThat(result.status()).isEqualTo(MarketIndexCollectionResult.Status.NO_DATA);
        assertThat(result.barsCount()).isEqualTo(0);
        verify(repository, never()).save(any());
    }

    @Test
    void collectMonth_whenClientThrows_returnsError() {
        when(twseClient.fetchMonthJson(MARCH_2026)).thenThrow(new RuntimeException("connection timeout"));

        MarketIndexCollectionResult result = service.collectMonth(MARCH_2026);

        assertThat(result.status()).isEqualTo(MarketIndexCollectionResult.Status.ERROR);
        assertThat(result.errorMessage()).contains("connection timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_delegatesToCollectMonth() {
        when(twseClient.fetchMonthJson(MARCH_2026)).thenReturn(ONE_BAR_JSON);
        when(repository.findByTradeDate(any())).thenReturn(Optional.empty());

        MarketIndexCollectionResult result = service.collectDate(LocalDate.of(2026, 3, 15));

        assertThat(result.yearMonth()).isEqualTo(MARCH_2026);
        assertThat(result.status()).isEqualTo(MarketIndexCollectionResult.Status.COLLECTED);
        verify(twseClient, times(1)).fetchMonthJson(MARCH_2026);
    }

    @Test
    void collectMonth_existingBar_isUpdatedNotDuplicated() {
        TaiexDailyBar existing = new TaiexDailyBar(LocalDate.of(2026, 3, 3));
        existing.setClose(1000000L);

        when(twseClient.fetchMonthJson(MARCH_2026)).thenReturn(ONE_BAR_JSON);
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 3))).thenReturn(Optional.of(existing));

        service.collectMonth(MARCH_2026);

        ArgumentCaptor<TaiexDailyBar> captor = ArgumentCaptor.forClass(TaiexDailyBar.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getClose()).isEqualTo(2030045L);
    }
}
