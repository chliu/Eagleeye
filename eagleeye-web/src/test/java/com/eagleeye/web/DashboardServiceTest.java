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

    OptionsPosition options(LocalDate date, long oiLong, long oiShort) {
        OptionsPosition op = new OptionsPosition(date, "TXO", TraderType.FINI);
        op.setOiLongVolume(oiLong);
        op.setOiShortVolume(oiShort);
        return op;
    }

    MarginTransaction margin(LocalDate date, long mBalance, long mPrev, long sBalance, long sPrev) {
        MarginTransaction m = new MarginTransaction(date);
        m.setMarginBalance(mBalance);
        m.setMarginPrevBalance(mPrev);
        m.setShortBalance(sBalance);
        m.setShortPrevBalance(sPrev);
        return m;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void buildViewModel_returnsCorrectDateLabels() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        stubTwoDays(d1, d2);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.dateLabels()).containsExactly("3/3", "3/4");
        assertThat(vm.days()).isEqualTo(20);
    }

    @Test
    void buildViewModel_convertsTaiexCloseToDouble() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        stubSingleDay(d, 2145678L, 1_000_000_000L, 1000L, 800L, 500L, 300L, 1_010_000L, 1_000_000L, 5000L, 4000L);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.taiexClose()).containsExactly(21456.78);
    }

    @Test
    void buildViewModel_computesMarginChange() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        // marginBalance=1_010_000, marginPrev=1_000_000 → delta=10_000
        stubSingleDay(d, 2100000L, 1_000_000_000L, 1000L, 800L, 500L, 300L, 1_010_000L, 1_000_000L, 0L, 0L);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.marginChange()).containsExactly(10_000L);
    }

    @Test
    void buildViewModel_computesShortChange() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        // shortBalance=5000, shortPrev=4000 → delta=1000
        stubSingleDay(d, 2100000L, 1_000_000_000L, 1000L, 800L, 500L, 300L, 1_010_000L, 1_000_000L, 5000L, 4000L);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.shortChange()).containsExactly(1000L);
    }

    @Test
    void buildViewModel_computesFuturesOI() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        stubSingleDay(d, 2100000L, 1_000_000_000L, 1000L, 600L, 500L, 300L, 1_010_000L, 1_000_000L, 0L, 0L);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.futuresLongOI()).containsExactly(1000L);
        assertThat(vm.futuresShortOI()).containsExactly(600L);
    }

    @Test
    void buildViewModel_computesOptionsOI() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        stubSingleDay(d, 2100000L, 1_000_000_000L, 1000L, 800L, 500L, 300L, 1_010_000L, 1_000_000L, 0L, 0L);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.optionsCallOI()).containsExactly(500L);
        assertThat(vm.optionsPutOI()).containsExactly(300L);
    }

    @Test
    void buildViewModel_excludesDatesWithMissingData() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        // taiex has d1 and d2, but flow only has d1
        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2100000L), taiex(d2, 2110000L)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 1_000_000_000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 800L), futures(d2, 900L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(options(d1, 500L, 300L), options(d2, 400L, 200L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(
                margin(d1, 1_010_000L, 1_000_000L, 5000L, 4000L),
                margin(d2, 1_005_000L, 1_010_000L, 4500L, 5000L)
            ));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.dateLabels()).containsExactly("3/3");
    }

    // ── Stub helpers ──────────────────────────────────────────────────────────

    private void stubTwoDays(LocalDate d1, LocalDate d2) {
        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2100000L), taiex(d2, 2110000L)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 1_000_000_000L), flow(d2, -500_000_000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 800L), futures(d2, 900L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TXO"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(options(d1, 500L, 300L), options(d2, 400L, 200L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(
                margin(d1, 1_010_000L, 1_000_000L, 5000L, 4000L),
                margin(d2, 1_005_000L, 1_010_000L, 4500L, 5000L)
            ));
    }

    private void stubSingleDay(LocalDate d, long taiexClose, long foreignNet,
                               long oiLong, long oiShort,
                               long optCall, long optPut,
                               long mBalance, long mPrev,
                               long sBalance, long sPrev) {
        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, taiexClose)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d, foreignNet)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, oiLong, oiShort)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TXO"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(options(d, optCall, optPut)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d, mBalance, mPrev, sBalance, sPrev)));
    }
}
