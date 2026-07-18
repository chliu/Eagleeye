package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TaifexClient;
import com.eagleeye.collector.taifex.TaifexMarketReportParser;
import com.eagleeye.domain.entity.FuturesMarketOi;
import com.eagleeye.domain.repository.FuturesMarketOiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuturesMarketOiServiceTest {

    @Mock private TaifexClient client;
    @Mock private TaifexMarketReportParser parser;
    @Mock private FuturesMarketOiRepository repository;

    private FuturesMarketOiService service;

    private static final LocalDate DATE = LocalDate.of(2026, 7, 16);

    @BeforeEach
    void setUp() {
        service = new FuturesMarketOiService(client, parser, repository);
    }

    @Test
    void collectDate_bothContractsPresent_savesEachAndReturnsCollected() {
        when(client.fetchDailyMarketReportHtml(DATE, "MTX")).thenReturn("<html>mtx</html>");
        when(client.fetchDailyMarketReportHtml(DATE, "TMF")).thenReturn("<html>tmf</html>");
        when(parser.isNoDataPage(any())).thenReturn(false);
        when(parser.parseTotalOi("<html>mtx</html>", DATE, "MTX")).thenReturn(35655L);
        when(parser.parseTotalOi("<html>tmf</html>", DATE, "TMF")).thenReturn(74882L);
        when(repository.findByTradeDateAndContract(eq(DATE), any())).thenReturn(Optional.empty());

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
        assertThat(result.tradeDate()).isEqualTo(DATE);

        ArgumentCaptor<FuturesMarketOi> captor = ArgumentCaptor.forClass(FuturesMarketOi.class);
        verify(repository, times(2)).save(captor.capture());
        List<FuturesMarketOi> saved = captor.getAllValues();
        assertThat(saved).extracting(FuturesMarketOi::getContract).containsExactlyInAnyOrder("MTX", "TMF");
        FuturesMarketOi mtx = saved.stream().filter(o -> o.getContract().equals("MTX")).findFirst().orElseThrow();
        assertThat(mtx.getTotalOi()).isEqualTo(35655L);
    }

    @Test
    void collectDate_bothContractsNoData_returnsNoData() {
        when(client.fetchDailyMarketReportHtml(eq(DATE), any())).thenReturn("<html>No Data</html>");
        when(parser.isNoDataPage(any())).thenReturn(true);

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_oneContractNoData_stillCollectsTheOther() {
        when(client.fetchDailyMarketReportHtml(DATE, "MTX")).thenReturn("<html>mtx</html>");
        when(client.fetchDailyMarketReportHtml(DATE, "TMF")).thenReturn("<html>No Data</html>");
        when(parser.isNoDataPage("<html>mtx</html>")).thenReturn(false);
        when(parser.isNoDataPage("<html>No Data</html>")).thenReturn(true);
        when(parser.parseTotalOi("<html>mtx</html>", DATE, "MTX")).thenReturn(35655L);
        when(repository.findByTradeDateAndContract(DATE, "MTX")).thenReturn(Optional.empty());

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
        verify(repository, times(1)).save(any());
    }

    @Test
    void collectDate_clientThrows_returnsError() {
        when(client.fetchDailyMarketReportHtml(eq(DATE), any())).thenThrow(new RuntimeException("timeout"));

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
        DateCollectionResult.Error error = (DateCollectionResult.Error) result;
        assertThat(error.message()).contains("timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_existingRecord_upserts() {
        FuturesMarketOi existing = new FuturesMarketOi(DATE, "MTX");
        when(client.fetchDailyMarketReportHtml(DATE, "MTX")).thenReturn("<html>mtx</html>");
        when(client.fetchDailyMarketReportHtml(DATE, "TMF")).thenReturn("<html>tmf</html>");
        when(parser.isNoDataPage(any())).thenReturn(false);
        when(parser.parseTotalOi("<html>mtx</html>", DATE, "MTX")).thenReturn(40000L);
        when(parser.parseTotalOi("<html>tmf</html>", DATE, "TMF")).thenReturn(75000L);
        when(repository.findByTradeDateAndContract(DATE, "MTX")).thenReturn(Optional.of(existing));
        when(repository.findByTradeDateAndContract(DATE, "TMF")).thenReturn(Optional.empty());

        service.collectDate(DATE);

        ArgumentCaptor<FuturesMarketOi> captor = ArgumentCaptor.forClass(FuturesMarketOi.class);
        verify(repository, times(2)).save(captor.capture());
        FuturesMarketOi mtx = captor.getAllValues().stream()
                .filter(o -> o.getContract().equals("MTX")).findFirst().orElseThrow();
        assertThat(mtx.getTotalOi()).isEqualTo(40000L);
        assertThat(mtx).isSameAs(existing);
    }
}
