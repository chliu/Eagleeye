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
    @Mock FuturesAhPositionRepository futuresAhRepo;
    @Mock OptionsPositionRepository optionsRepo;
    @Mock OptionsCallPutPositionRepository callPutRepo;
    @Mock MarginTransactionRepository marginRepo;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(taiexRepo, flowRepo, futuresRepo, futuresAhRepo, optionsRepo, callPutRepo, marginRepo);
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

    FuturesPosition futures(LocalDate date, String contract, long oiLong, long oiShort) {
        FuturesPosition fp = new FuturesPosition(date, contract, TraderType.FINI);
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

    OptionsCallPutPosition callPut(LocalDate date, RightType right, long oiNetValue) {
        OptionsCallPutPosition cp = new OptionsCallPutPosition(date, "TXO", TraderType.FINI, right);
        cp.setOiNetValue(oiNetValue);
        return cp;
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
    void buildViewModel_combinesTxMtxTmfIntoTxEquivalentNetPosition() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "TX", 1000L, 600L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "MTX", 400L, 200L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "TMF", 200L, 100L)));

        DashboardViewModel vm = service.buildViewModel(20);

        // long:  1000 + 400/4  + 200/20 = 1000 + 100 + 10 = 1110
        // short:  600 + 200/4  + 100/20 =  600 +  50 +  5 =  655
        assertThat(vm.futuresLongOI()).containsExactly(1110L);
        assertThat(vm.futuresShortOI()).containsExactly(655L);
    }

    @Test
    void buildViewModel_roundsFractionalTxEquivalentToNearestLot() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "TX", 1000L, 600L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "MTX", 3L, 1L))); // 3/4=0.75, 1/4=0.25
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of());

        DashboardViewModel vm = service.buildViewModel(20);

        // long:  1000 + 0.75 = 1000.75 -> rounds to 1001
        // short:  600 + 0.25 =  600.25 -> rounds to  600
        assertThat(vm.futuresLongOI()).containsExactly(1001L);
        assertThat(vm.futuresShortOI()).containsExactly(600L);
    }

    @Test
    void buildViewModel_futuresRowStaysTxOnlyWhenMtxTmfMissingForOneDate() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2100000L), taiex(d2, 2110000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d1, "TX", 1000L, 600L), futures(d2, "TX", 900L, 900L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d1, "MTX", 400L, 200L))); // only d1 has MTX data
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of());

        DashboardViewModel vm = service.buildViewModel(20);

        // d1: 1000 + 400/4 = 1100 long, 600 + 200/4 = 650 short
        // d2: TX only that day (no MTX/TMF row) -> 900 long, 900 short
        assertThat(vm.futuresLongOI()).containsExactly(1100L, 900L);
        assertThat(vm.futuresShortOI()).containsExactly(650L, 900L);
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
    void buildViewModel_computesOptionsCallPutNetValue() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(callPutRepo.findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
                eq("TXO"), eq(TraderType.FINI), eq(RightType.CALL), any(), any()))
            .thenReturn(List.of(callPut(d, RightType.CALL, 569_039L)));
        when(callPutRepo.findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
                eq("TXO"), eq(TraderType.FINI), eq(RightType.PUT), any(), any()))
            .thenReturn(List.of(callPut(d, RightType.PUT, 101_887L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.optionsCallNetValue()).containsExactly(569_039L);
        assertThat(vm.optionsPutNetValue()).containsExactly(101_887L);
    }

    @Test
    void buildViewModel_includesDatesWithPartialData_nullForMissingSources() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        // taiex has d1 and d2; flow only has d1 — d2 should still appear with null spotNetFlow
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

        // Both dates visible — TaiexIndex is the x-axis base
        assertThat(vm.dateLabels()).containsExactly("3/3", "3/4");
        // d1 has flow data; d2 is missing flow → null
        assertThat(vm.spotNetFlow()).containsExactly(1_000_000_000L, null);
    }

    // ── Stub helpers ──────────────────────────────────────────────────────────

    private void stubTwoDays(LocalDate d1, LocalDate d2) {
        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2100000L), taiex(d2, 2110000L)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 1_000_000_000L), flow(d2, -500_000_000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 800L), futures(d2, 900L, 900L)));
        when(futuresAhRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of());
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
        when(futuresAhRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of());
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TXO"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(options(d, optCall, optPut)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d, mBalance, mPrev, sBalance, sPrev)));
    }
}
