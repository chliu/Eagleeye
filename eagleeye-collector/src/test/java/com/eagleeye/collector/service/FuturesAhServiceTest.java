package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TaifexClient;
import com.eagleeye.collector.taifex.TaifexParser;
import com.eagleeye.domain.dto.PositionDto;
import com.eagleeye.domain.entity.FuturesAhPosition;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.FuturesAhPositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuturesAhServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 28);

    @Mock TaifexClient                  taifexClient;
    @Mock TaifexParser                  taifexParser;
    @Mock FuturesAhPositionRepository   repository;

    @InjectMocks FuturesAhService service;

    @Test
    void collectDate_noData_whenNoDataPage() {
        when(taifexClient.fetchFuturesAhHtml(DATE)).thenReturn("<html>No Data</html>");
        when(taifexParser.isNoDataPage("<html>No Data</html>")).thenReturn(true);

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
        assertThat(result.tradeDate()).isEqualTo(DATE);
        verifyNoInteractions(repository);
    }

    @Test
    void collectDate_collected_whenDataFound() {
        when(taifexClient.fetchFuturesAhHtml(DATE)).thenReturn("<html>data</html>");
        when(taifexParser.isNoDataPage("<html>data</html>")).thenReturn(false);
        when(taifexParser.parseAh("<html>data</html>", DATE)).thenReturn(List.of(
                dto("TX", TraderType.DEALER),
                dto("TX", TraderType.FINI),
                dto("TX", TraderType.INVESTMENT_TRUST)
        ));
        when(repository.findByTradeDateAndContractAndTraderType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
        assertThat(result.tradeDate()).isEqualTo(DATE);
        verify(repository, times(3)).save(any());
    }

    @Test
    void collectDate_error_whenClientThrows() {
        when(taifexClient.fetchFuturesAhHtml(DATE)).thenThrow(new RuntimeException("timeout"));

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
        DateCollectionResult.Error error = (DateCollectionResult.Error) result;
        assertThat(error.message()).contains("timeout");
        verifyNoInteractions(repository);
    }

    @Test
    void collectDate_insertsNewEntity_whenNoExistingRecord() {
        when(taifexClient.fetchFuturesAhHtml(DATE)).thenReturn("<html>data</html>");
        when(taifexParser.isNoDataPage(any())).thenReturn(false);
        when(taifexParser.parseAh(any(), any())).thenReturn(List.of(dto("TX", TraderType.DEALER)));
        when(repository.findByTradeDateAndContractAndTraderType(DATE, "TX", TraderType.DEALER))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.collectDate(DATE);

        verify(repository).save(argThat(p ->
                p.getTradeDate().equals(DATE)
                && "TX".equals(p.getContract())
                && p.getTradingLongVolume() == 100L
        ));
    }

    @Test
    void collectDate_updatesExistingEntity_whenRecordAlreadyExists() {
        FuturesAhPosition existing = new FuturesAhPosition(DATE, "TX", TraderType.DEALER);

        when(taifexClient.fetchFuturesAhHtml(DATE)).thenReturn("<html>data</html>");
        when(taifexParser.isNoDataPage(any())).thenReturn(false);
        when(taifexParser.parseAh(any(), any())).thenReturn(List.of(dto("TX", TraderType.DEALER)));
        when(repository.findByTradeDateAndContractAndTraderType(DATE, "TX", TraderType.DEALER))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.collectDate(DATE);

        verify(repository).save(same(existing));
    }

    private PositionDto dto(String contract, TraderType traderType) {
        return new PositionDto(DATE, contract, traderType,
                100L, 1000L, 80L, 800L, 20L, 200L,
                0L, 0L, 0L, 0L, 0L, 0L);
    }
}
