package com.eagleeye.web;

import com.eagleeye.domain.entity.*;
import com.eagleeye.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock TaiexIndexRepository taiexRepo;
    @Mock InstitutionalFlowRepository flowRepo;
    @Mock FuturesPositionRepository futuresRepo;
    @Mock OptionsPositionRepository optionsRepo;
    @Mock MarginTransactionRepository marginRepo;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(taiexRepo, flowRepo, futuresRepo, optionsRepo, marginRepo);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    TaiexIndex taiex(LocalDate date, long close) {
        TaiexIndex t = new TaiexIndex(date);
        t.setClose(close);
        return t;
    }

    InstitutionalFlow flow(LocalDate date, long foreignNet) {
        InstitutionalFlow f = new InstitutionalFlow(date);
        f.setForeignNet(foreignNet);
        return f;
    }

    FuturesPosition futures(LocalDate date, long oiLong, long oiShort) {
        FuturesPosition fp = new FuturesPosition(date, "TX", TraderType.FINI);
        fp.setOiLongVolume(oiLong);
        fp.setOiShortVolume(oiShort);
        fp.setOiNetVolume(oiLong - oiShort);
        return fp;
    }

    OptionsPosition options(LocalDate date, long oiNet) {
        OptionsPosition op = new OptionsPosition(date, "TXO", TraderType.FINI);
        op.setOiNetVolume(oiNet);
        op.setOiLongVolume(oiNet > 0 ? oiNet : 0);
        op.setOiShortVolume(oiNet < 0 ? -oiNet : 0);
        return op;
    }

    MarginTransaction margin(LocalDate date, long balance, long prevBalance) {
        MarginTransaction m = new MarginTransaction(date);
        m.setMarginBalance(balance);
        m.setMarginPrevBalance(prevBalance);
        return m;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void buildViewModel_returnsCorrectDateLabels() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2100000L), taiex(d2, 2110000L)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 1_000_000_000L), flow(d2, -500_000_000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 800L), futures(d2, 900L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TXO"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(options(d1, 500L), options(d2, 300L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d1, 1_010_000L, 1_000_000L), margin(d2, 1_005_000L, 1_010_000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.dateLabels()).containsExactly("3/3", "3/4");
        assertThat(vm.days()).isEqualTo(20);
    }

    @Test
    void buildViewModel_convertsTaiexCloseToDouble() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        stubSingleDay(d, 2145678L, 1_000_000_000L, 1000L, 800L, 500L, 1_010_000L, 1_000_000L);

        DashboardViewModel vm = service.buildViewModel(20);

        // 2145678 / 100.0 = 21456.78
        assertThat(vm.taiexClose()).containsExactly(21456.78);
    }

    @Test
    void buildViewModel_computesCumulativeSpotFlow() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);
        LocalDate d3 = LocalDate.of(2025, 3, 5);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2100000L), taiex(d2, 2110000L), taiex(d3, 2105000L)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 3_000_000_000L), flow(d2, -1_000_000_000L), flow(d3, 2_000_000_000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 800L), futures(d2, 900L, 900L), futures(d3, 950L, 850L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(options(d1, 500L), options(d2, 300L), options(d3, 400L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(
                margin(d1, 1_010_000L, 1_000_000L),
                margin(d2, 1_005_000L, 1_010_000L),
                margin(d3, 1_020_000L, 1_005_000L)
            ));

        DashboardViewModel vm = service.buildViewModel(20);

        // cumulative: 3B, 3B-1B=2B, 2B+2B=4B
        assertThat(vm.spotCumulative()).containsExactly(3_000_000_000L, 2_000_000_000L, 4_000_000_000L);
    }

    @Test
    void buildViewModel_computesFuturesLSRatio() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        // oiLong=1000, oiShort=600 → (1000-600)/(1000+600) = 400/1600 = 0.25
        stubSingleDay(d, 2100000L, 1_000_000_000L, 1000L, 600L, 500L, 1_010_000L, 1_000_000L);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.futuresLSRatio()).containsExactly(0.25);
    }

    @Test
    void buildViewModel_computesMarginChangeRate() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        // balance=1_010_000, prevBalance=1_000_000 → (10000/1000000) = 0.01
        stubSingleDay(d, 2100000L, 1_000_000_000L, 1000L, 600L, 500L, 1_010_000L, 1_000_000L);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.marginChangeRate().get(0)).isCloseTo(0.01, within(0.0001));
    }

    @Test
    void buildViewModel_detectsSpotDivergenceAfterTwoConsecutiveDays() {
        // d0: TAIEX high baseline
        // d1: foreignNet > 0 (buying) but TAIEX falls from d0 → divergence day 1
        // d2: foreignNet > 0 (buying) but TAIEX falls from d1 → divergence day 2 → RED alert
        LocalDate d0 = LocalDate.of(2025, 3, 2);
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d0, 2300000L), taiex(d1, 2200000L), taiex(d2, 2100000L)));  // TAIEX falling
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d0, 6_000_000_000L), flow(d1, 5_000_000_000L), flow(d2, 3_000_000_000L)));  // 外資 buying
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(futures(d0, 1000L, 900L), futures(d1, 1000L, 900L), futures(d2, 1000L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(options(d0, 200L), options(d1, 200L), options(d2, 200L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d0, 1_015_000L, 1_005_000L), margin(d1, 1_010_000L, 1_000_000L), margin(d2, 1_005_000L, 1_010_000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.alerts())
            .anySatisfy(a -> {
                assertThat(a.severity()).isEqualTo(DashboardViewModel.Severity.RED);
                assertThat(a.signal()).contains("現貨");
            });
    }

    @Test
    void buildViewModel_doesNotTriggerDivergenceOnSingleDay() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2200000L), taiex(d2, 2250000L)));  // TAIEX rising on d2
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 5_000_000_000L), flow(d2, -1_000_000_000L)));  // selling on d2
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 900L), futures(d2, 1000L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(options(d1, 200L), options(d2, 200L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d1, 1_010_000L, 1_000_000L), margin(d2, 1_005_000L, 1_010_000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.alerts())
            .noneMatch(a -> a.severity() == DashboardViewModel.Severity.RED
                         && a.signal().contains("現貨"));
    }

    @Test
    void buildViewModel_skipsZeroReturnDaysInDivergenceCount() {
        // d1: TAIEX flat (return=0) + 外資 buying → skip (zero return)
        // d2: TAIEX down + 外資 buying → divergence day 1 only → no RED
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2200000L), taiex(d2, 2100000L)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 5_000_000_000L), flow(d2, 3_000_000_000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 900L), futures(d2, 1000L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(options(d1, 200L), options(d2, 200L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d1, 1_010_000L, 1_000_000L), margin(d2, 1_005_000L, 1_010_000L)));

        // The service computes daily return using consecutive pairs: return[i] = close[i] - close[i-1]
        // With only 2 points: return[0] is undefined (no prev), return[1] = close[1]-close[0]
        // So d1 is never evaluated for divergence; only d2 is → 1 divergence day → no RED
        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.alerts())
            .noneMatch(a -> a.severity() == DashboardViewModel.Severity.RED
                         && a.signal().contains("現貨"));
    }

    // ── Private stub helper ────────────────────────────────────────────────────

    private void stubSingleDay(LocalDate d, long taiexClose, long foreignNet,
                               long oiLong, long oiShort, long optionsOiNet,
                               long marginBalance, long marginPrev) {
        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, taiexClose)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d, foreignNet)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, oiLong, oiShort)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TXO"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(options(d, optionsOiNet)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d, marginBalance, marginPrev)));
    }
}
