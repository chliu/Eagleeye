package com.eagleeye.web;

import com.eagleeye.domain.entity.*;
import com.eagleeye.domain.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DashboardService {

    private static final Set<Integer> ALLOWED_DAYS = Set.of(20, 40, 60);
    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("M/d");

    private final TaiexIndexRepository taiexRepo;
    private final InstitutionalFlowRepository flowRepo;
    private final FuturesPositionRepository futuresRepo;
    private final OptionsPositionRepository optionsRepo;
    private final MarginTransactionRepository marginRepo;

    public DashboardService(TaiexIndexRepository taiexRepo,
                            InstitutionalFlowRepository flowRepo,
                            FuturesPositionRepository futuresRepo,
                            OptionsPositionRepository optionsRepo,
                            MarginTransactionRepository marginRepo) {
        this.taiexRepo = taiexRepo;
        this.flowRepo = flowRepo;
        this.futuresRepo = futuresRepo;
        this.optionsRepo = optionsRepo;
        this.marginRepo = marginRepo;
    }

    public DashboardViewModel buildViewModel(int days) {
        if (!ALLOWED_DAYS.contains(days)) days = 40;

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days * 2L); // fetch extra to account for weekends/holidays

        // Query all 5 repositories
        List<TaiexIndex> taiexList = taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<InstitutionalFlow> flowList = flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<FuturesPosition> futuresList = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TX", TraderType.FINI, from, to);
        List<OptionsPosition> optionsList = optionsRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TXO", TraderType.FINI, from, to);
        List<MarginTransaction> marginList = marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);

        // Align by trade date — build maps for O(1) lookup on all sources
        Map<LocalDate, TaiexIndex>        taiexMap = indexByDate(taiexList,   TaiexIndex::getTradeDate);
        Map<LocalDate, InstitutionalFlow> flowMap  = indexByDate(flowList,    InstitutionalFlow::getTradeDate);
        Map<LocalDate, FuturesPosition>   futMap   = indexByDate(futuresList, FuturesPosition::getTradeDate);
        Map<LocalDate, OptionsPosition>   optMap   = indexByDate(optionsList, OptionsPosition::getTradeDate);
        Map<LocalDate, MarginTransaction> mgnMap   = indexByDate(marginList,  MarginTransaction::getTradeDate);

        // Keep only dates where ALL 5 sources have data, then take last `days` entries
        List<LocalDate> alignedDates = taiexList.stream()
            .map(TaiexIndex::getTradeDate)
            .filter(d -> flowMap.containsKey(d) && futMap.containsKey(d)
                      && optMap.containsKey(d) && mgnMap.containsKey(d))
            .toList();

        // Take last `days` entries
        int start = Math.max(0, alignedDates.size() - days);
        List<LocalDate> dates = alignedDates.subList(start, alignedDates.size());

        // Build series
        List<String>  dateLabels   = new ArrayList<>();
        List<Double>  taiexClose   = new ArrayList<>();
        List<Long>    spotNetFlow  = new ArrayList<>();
        List<Long>    spotCumul    = new ArrayList<>();
        List<Double>  futLSRatio   = new ArrayList<>();
        List<Long>    optNetOI     = new ArrayList<>();
        List<Double>  marginChange = new ArrayList<>();

        long cumulative = 0L;

        for (LocalDate date : dates) {
            TaiexIndex ti = taiexMap.get(date);
            InstitutionalFlow fl = flowMap.get(date);
            FuturesPosition   fp = futMap.get(date);
            OptionsPosition   op = optMap.get(date);
            MarginTransaction mg = mgnMap.get(date);

            dateLabels.add(date.format(LABEL_FMT));
            taiexClose.add(ti.getClose() / 100.0);

            long net = fl.getForeignNet() != null ? fl.getForeignNet() : 0L;
            cumulative += net;
            spotNetFlow.add(net);
            spotCumul.add(cumulative);

            long oiLong = fp.getOiLongVolume() != null ? fp.getOiLongVolume() : 0L;
            long oiShort = fp.getOiShortVolume() != null ? fp.getOiShortVolume() : 0L;
            double ratio = (oiLong + oiShort) == 0 ? 0.0
                : (double)(oiLong - oiShort) / (oiLong + oiShort);
            futLSRatio.add(ratio);

            optNetOI.add(op.getOiNetVolume() != null ? op.getOiNetVolume() : 0L);

            long balance = mg.getMarginBalance() != null ? mg.getMarginBalance() : 0L;
            long prev    = mg.getMarginPrevBalance() != null ? mg.getMarginPrevBalance() : 1L;
            marginChange.add(prev == 0 ? 0.0 : (double)(balance - prev) / prev);
        }

        List<DashboardViewModel.AlertItem> alerts = detectAlerts(
            taiexClose, spotNetFlow, futLSRatio, optNetOI, marginChange);

        return new DashboardViewModel(
            dateLabels, taiexClose, spotNetFlow, spotCumul,
            futLSRatio, optNetOI, marginChange, alerts, days);
    }

    // ── Signal detection ──────────────────────────────────────────────────────

    private List<DashboardViewModel.AlertItem> detectAlerts(
            List<Double> taiexClose,
            List<Long>   spotNetFlow,
            List<Double> futLSRatio,
            List<Long>   optNetOI,
            List<Double> marginChange) {

        List<DashboardViewModel.AlertItem> alerts = new ArrayList<>();

        // 1. 外資現貨 divergence: sign(foreignNet) ≠ sign(taiexReturn) for ≥ 2 consecutive days
        int spotDivergeDays = 0;
        for (int i = 1; i < taiexClose.size(); i++) {
            double ret = taiexClose.get(i) - taiexClose.get(i - 1);
            if (ret == 0) continue; // skip flat days
            long net = spotNetFlow.get(i);
            boolean diverges = (net > 0 && ret < 0) || (net < 0 && ret > 0);
            spotDivergeDays = diverges ? spotDivergeDays + 1 : 0;
        }
        if (spotDivergeDays >= 2) {
            alerts.add(new DashboardViewModel.AlertItem(
                "外資現貨", DashboardViewModel.Severity.RED,
                "外資現貨 買賣方向與 TAIEX 走勢背離 " + spotDivergeDays + " 日"));
        }

        // 2. 外資期貨 L/S ratio: 3-day MA slope < -0.05 while TAIEX 3-day return > 0
        if (futLSRatio.size() >= 3) {
            int n = futLSRatio.size();
            double ratioSlope = futLSRatio.get(n - 1) - futLSRatio.get(n - 3);
            double taiexReturn3d = taiexClose.get(n - 1) - taiexClose.get(n - 3);
            if (ratioSlope < -0.05 && taiexReturn3d > 0) {
                alerts.add(new DashboardViewModel.AlertItem(
                    "外資期貨", DashboardViewModel.Severity.YELLOW,
                    "期貨多空比走弱，TAIEX 仍上漲"));
            }
        }

        // 3. 外資選擇權: oiNetOI 5-day trend contradicts TAIEX 5-day direction
        if (optNetOI.size() >= 5 && taiexClose.size() >= 5) {
            int n = optNetOI.size();
            long optTrend = optNetOI.get(n - 1) - optNetOI.get(n - 5);
            double taiexTrend = taiexClose.get(n - 1) - taiexClose.get(n - 5);
            if ((optTrend < 0 && taiexTrend > 0) || (optTrend > 0 && taiexTrend < 0)) {
                alerts.add(new DashboardViewModel.AlertItem(
                    "外資選擇權", DashboardViewModel.Severity.YELLOW,
                    "選擇權部位方向與 TAIEX 走勢相反"));
            }
        }

        // 4. 融資: margin growing > 1.5% while 外資 net selling on last day
        if (!marginChange.isEmpty() && !spotNetFlow.isEmpty()) {
            double lastMargin = marginChange.get(marginChange.size() - 1);
            long lastNet = spotNetFlow.get(spotNetFlow.size() - 1);
            if (lastMargin > 0.015 && lastNet < 0) {
                alerts.add(new DashboardViewModel.AlertItem(
                    "融資券", DashboardViewModel.Severity.YELLOW,
                    "融資大增，外資同步賣超，零售風險上升"));
            }
        }

        // 5. Combined escalation: 外資現貨 + 期貨 both diverge → RED (escalate)
        boolean spotDiverged = alerts.stream()
            .anyMatch(a -> a.signal().contains("現貨") && a.severity() == DashboardViewModel.Severity.RED);
        boolean futuresDiverged = alerts.stream()
            .anyMatch(a -> a.signal().contains("期貨") && a.severity() == DashboardViewModel.Severity.YELLOW);
        if (spotDiverged && futuresDiverged) {
            alerts.add(new DashboardViewModel.AlertItem(
                "綜合警示", DashboardViewModel.Severity.RED,
                "外資現貨 + 期貨同步背離 TAIEX，高度警戒"));
        }

        // 6. If no divergence alerts → GREEN
        boolean hasRed = alerts.stream()
            .anyMatch(a -> a.severity() == DashboardViewModel.Severity.RED);
        boolean hasYellow = alerts.stream()
            .anyMatch(a -> a.severity() == DashboardViewModel.Severity.YELLOW);
        if (!hasRed && !hasYellow) {
            alerts.add(new DashboardViewModel.AlertItem(
                "總覽", DashboardViewModel.Severity.GREEN, "各訊號與 TAIEX 走勢一致"));
        }

        return alerts;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private <T> Map<LocalDate, T> indexByDate(List<T> list,
                                               java.util.function.Function<T, LocalDate> keyFn) {
        Map<LocalDate, T> map = new LinkedHashMap<>();
        for (T item : list) map.put(keyFn.apply(item), item);
        return map;
    }
}
