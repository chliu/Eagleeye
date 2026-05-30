package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TaifexClient;
import com.eagleeye.collector.taifex.TaifexParser;
import com.eagleeye.domain.dto.PositionDto;
import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.FuturesPositionRepository;
import com.eagleeye.domain.repository.OptionsPositionRepository;
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
class CollectionServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 27);

    @Mock TaifexClient              taifexClient;
    @Mock TaifexParser              taifexParser;
    @Mock FuturesPositionRepository futuresRepo;
    @Mock OptionsPositionRepository optionsRepo;

    @InjectMocks CollectionService service;

    // ── collectAll ────────────────────────────────────────────────────────────

    @Test
    void collectAll_noData_whenFuturesPageHasNoData() {
        when(taifexClient.fetchFuturesHtml(DATE)).thenReturn("<html>No Data</html>");
        when(taifexParser.isNoDataPage("<html>No Data</html>")).thenReturn(true);

        FuturesOptionsCollectionResult result = service.collectAll(DATE);

        assertThat(result).isInstanceOf(FuturesOptionsCollectionResult.NoData.class);
        assertThat(result.date()).isEqualTo(DATE);
    }

    @Test
    void collectAll_collected_returnsFuturesAndOptionsCount() {
        when(taifexClient.fetchFuturesHtml(DATE)).thenReturn("<html>futures</html>");
        when(taifexParser.isNoDataPage("<html>futures</html>")).thenReturn(false);
        when(taifexParser.parse("<html>futures</html>", DATE)).thenReturn(List.of(
                dto("TX",  TraderType.DEALER),
                dto("TX",  TraderType.FINI)
        ));
        when(futuresRepo.findByTradeDateAndContractAndTraderType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(futuresRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        when(taifexClient.fetchOptionsHtml(DATE)).thenReturn("<html>options</html>");
        when(taifexParser.parse("<html>options</html>", DATE)).thenReturn(List.of(
                dto("TXO", TraderType.DEALER),
                dto("TXO", TraderType.FINI),
                dto("TXO", TraderType.INVESTMENT_TRUST)
        ));
        when(optionsRepo.findByTradeDateAndContractAndTraderType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(optionsRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        FuturesOptionsCollectionResult result = service.collectAll(DATE);

        assertThat(result).isInstanceOf(FuturesOptionsCollectionResult.Collected.class);
        FuturesOptionsCollectionResult.Collected collected = (FuturesOptionsCollectionResult.Collected) result;
        assertThat(collected.futuresCount()).isEqualTo(2);
        assertThat(collected.optionsCount()).isEqualTo(3);
    }

    @Test
    void collectAll_error_whenClientThrows() {
        when(taifexClient.fetchFuturesHtml(DATE)).thenThrow(new RuntimeException("connection refused"));

        FuturesOptionsCollectionResult result = service.collectAll(DATE);

        assertThat(result).isInstanceOf(FuturesOptionsCollectionResult.Error.class);
        FuturesOptionsCollectionResult.Error error = (FuturesOptionsCollectionResult.Error) result;
        assertThat(error.message()).contains("connection refused");
    }

    // ── collectFutures ────────────────────────────────────────────────────────

    @Test
    void collectFutures_returnsZero_whenNoDataPage() {
        when(taifexClient.fetchFuturesHtml(DATE)).thenReturn("<html>No Data</html>");
        when(taifexParser.isNoDataPage("<html>No Data</html>")).thenReturn(true);

        assertThat(service.collectFutures(DATE)).isZero();
    }

    @Test
    void collectFutures_returnsPositionCount_whenDataFound() {
        when(taifexClient.fetchFuturesHtml(DATE)).thenReturn("<html>data</html>");
        when(taifexParser.isNoDataPage("<html>data</html>")).thenReturn(false);
        when(taifexParser.parse("<html>data</html>", DATE)).thenReturn(List.of(
                dto("TX", TraderType.DEALER),
                dto("TX", TraderType.FINI),
                dto("TX", TraderType.INVESTMENT_TRUST)
        ));
        when(futuresRepo.findByTradeDateAndContractAndTraderType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(futuresRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.collectFutures(DATE)).isEqualTo(3);
    }

    // ── collectOptions ────────────────────────────────────────────────────────

    @Test
    void collectOptions_returnsZero_whenNoDataPage() {
        when(taifexClient.fetchOptionsHtml(DATE)).thenReturn("<html>No Data</html>");
        when(taifexParser.isNoDataPage("<html>No Data</html>")).thenReturn(true);

        assertThat(service.collectOptions(DATE)).isZero();
    }

    @Test
    void collectOptions_returnsPositionCount_whenDataFound() {
        when(taifexClient.fetchOptionsHtml(DATE)).thenReturn("<html>data</html>");
        when(taifexParser.isNoDataPage("<html>data</html>")).thenReturn(false);
        when(taifexParser.parse("<html>data</html>", DATE)).thenReturn(List.of(
                dto("TXO", TraderType.DEALER),
                dto("TXO", TraderType.FINI)
        ));
        when(optionsRepo.findByTradeDateAndContractAndTraderType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(optionsRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.collectOptions(DATE)).isEqualTo(2);
    }

    // ── upsert: insert vs update ──────────────────────────────────────────────

    @Test
    void collectFutures_insertsNewEntity_whenNoExistingRecord() {
        when(taifexClient.fetchFuturesHtml(DATE)).thenReturn("<html>data</html>");
        when(taifexParser.isNoDataPage(any())).thenReturn(false);
        when(taifexParser.parse(any(), any())).thenReturn(List.of(dto("TX", TraderType.DEALER)));
        when(futuresRepo.findByTradeDateAndContractAndTraderType(DATE, "TX", TraderType.DEALER))
                .thenReturn(Optional.empty());
        when(futuresRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.collectFutures(DATE);

        // A brand-new FuturesPosition has no id; verify data was applied
        verify(futuresRepo).save(argThat(p ->
                p.getTradeDate().equals(DATE)
                && "TX".equals(p.getContract())
                && p.getTradingLongVolume() == 100L
        ));
    }

    @Test
    void collectFutures_updatesExistingEntity_whenRecordAlreadyExists() {
        FuturesPosition existing = new FuturesPosition(DATE, "TX", TraderType.DEALER);

        when(taifexClient.fetchFuturesHtml(DATE)).thenReturn("<html>data</html>");
        when(taifexParser.isNoDataPage(any())).thenReturn(false);
        when(taifexParser.parse(any(), any())).thenReturn(List.of(dto("TX", TraderType.DEALER)));
        when(futuresRepo.findByTradeDateAndContractAndTraderType(DATE, "TX", TraderType.DEALER))
                .thenReturn(Optional.of(existing));
        when(futuresRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.collectFutures(DATE);

        verify(futuresRepo).save(same(existing));
    }

    @Test
    void collectOptions_updatesExistingEntity_whenRecordAlreadyExists() {
        OptionsPosition existing = new OptionsPosition(DATE, "TXO", TraderType.FINI);

        when(taifexClient.fetchOptionsHtml(DATE)).thenReturn("<html>data</html>");
        when(taifexParser.isNoDataPage(any())).thenReturn(false);
        when(taifexParser.parse(any(), any())).thenReturn(List.of(dto("TXO", TraderType.FINI)));
        when(optionsRepo.findByTradeDateAndContractAndTraderType(DATE, "TXO", TraderType.FINI))
                .thenReturn(Optional.of(existing));
        when(optionsRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.collectOptions(DATE);

        verify(optionsRepo).save(same(existing));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private PositionDto dto(String contract, TraderType traderType) {
        return new PositionDto(DATE, contract, traderType,
                100L, 1000L, 80L, 800L, 20L, 200L,
                500L, 5000L, 400L, 4000L, 100L, 1000L);
    }
}
