package com.eagleeye.web;

import com.eagleeye.domain.entity.FuturesAhPosition;
import com.eagleeye.domain.entity.FuturesMarketOi;
import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TaiexIndex;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.FuturesAhPositionRepository;
import com.eagleeye.domain.repository.FuturesMarketOiRepository;
import com.eagleeye.domain.repository.FuturesPositionRepository;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import com.eagleeye.domain.repository.MarginTransactionRepository;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
import com.eagleeye.domain.repository.OptionsPositionRepository;
import com.eagleeye.domain.repository.TaiexIndexRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Service
public class DashboardService {

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("M/d");

    private final TaiexIndexRepository taiexRepo;
    private final InstitutionalFlowRepository flowRepo;
    private final FuturesPositionRepository futuresRepo;
    private final FuturesAhPositionRepository futuresAhRepo;
    private final OptionsPositionRepository optionsRepo;
    private final OptionsCallPutPositionRepository callPutRepo;
    private final MarginTransactionRepository marginRepo;
    private final FuturesMarketOiRepository futuresMarketOiRepo;

    public DashboardService(TaiexIndexRepository taiexRepo,
                            InstitutionalFlowRepository flowRepo,
                            FuturesPositionRepository futuresRepo,
                            FuturesAhPositionRepository futuresAhRepo,
                            OptionsPositionRepository optionsRepo,
                            OptionsCallPutPositionRepository callPutRepo,
                            MarginTransactionRepository marginRepo,
                            FuturesMarketOiRepository futuresMarketOiRepo) {
        this.taiexRepo = taiexRepo;
        this.flowRepo = flowRepo;
        this.futuresRepo = futuresRepo;
        this.futuresAhRepo = futuresAhRepo;
        this.optionsRepo = optionsRepo;
        this.callPutRepo = callPutRepo;
        this.marginRepo = marginRepo;
        this.futuresMarketOiRepo = futuresMarketOiRepo;
    }

    public DashboardViewModel buildViewModel(int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days * 2L);

        List<TaiexIndex>        taiexList   = taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<InstitutionalFlow> flowList    = flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<FuturesPosition>   futuresTxList  = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TX", TraderType.FINI, from, to);
        List<FuturesPosition>   futuresMtxList = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("MTX", TraderType.FINI, from, to);
        List<FuturesPosition>   futuresTmfList = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TMF", TraderType.FINI, from, to);
        List<OptionsPosition>   optionsList = optionsRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TXO", TraderType.FINI, from, to);
        List<OptionsCallPutPosition> callList = callPutRepo
            .findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
                "TXO", TraderType.FINI, RightType.CALL, from, to);
        List<OptionsCallPutPosition> putList = callPutRepo
            .findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
                "TXO", TraderType.FINI, RightType.PUT, from, to);
        // AH data is stored under the *next* trading day (up to +4 days for Friday nights),
        // so extend the window to catch entries beyond today.
        List<FuturesAhPosition> futuresAhList = futuresAhRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TX", TraderType.FINI, from, to.plusDays(7));
        List<MarginTransaction> marginList  = marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<FuturesPosition>   futuresMtxAllList = futuresRepo
            .findByContractAndTradeDateBetweenOrderByTradeDateAsc("MTX", from, to);
        List<FuturesPosition>   futuresTmfAllList = futuresRepo
            .findByContractAndTradeDateBetweenOrderByTradeDateAsc("TMF", from, to);
        List<FuturesMarketOi>   mtxOiList = futuresMarketOiRepo
            .findByContractAndTradeDateBetweenOrderByTradeDateAsc("MTX", from, to);
        List<FuturesMarketOi>   tmfOiList = futuresMarketOiRepo
            .findByContractAndTradeDateBetweenOrderByTradeDateAsc("TMF", from, to);

        Map<LocalDate, TaiexIndex>        taiexMap = indexByDate(taiexList,   TaiexIndex::getTradeDate);
        Map<LocalDate, InstitutionalFlow> flowMap  = indexByDate(flowList,    InstitutionalFlow::getTradeDate);
        Map<LocalDate, FuturesPosition>   futTxMap  = indexByDate(futuresTxList,  FuturesPosition::getTradeDate);
        Map<LocalDate, FuturesPosition>   futMtxMap = indexByDate(futuresMtxList, FuturesPosition::getTradeDate);
        Map<LocalDate, FuturesPosition>   futTmfMap = indexByDate(futuresTmfList, FuturesPosition::getTradeDate);
        Map<LocalDate, OptionsPosition>   optMap   = indexByDate(optionsList, OptionsPosition::getTradeDate);
        Map<LocalDate, OptionsCallPutPosition> callMap = indexByDate(callList, OptionsCallPutPosition::getTradeDate);
        Map<LocalDate, OptionsCallPutPosition> putMap  = indexByDate(putList,  OptionsCallPutPosition::getTradeDate);
        // NavigableMap so we can find the next AH entry after each trading day with higherKey().
        NavigableMap<LocalDate, FuturesAhPosition> ahMap = futuresAhList.stream()
            .collect(toMap(FuturesAhPosition::getTradeDate, Function.identity(), (a, b) -> b, TreeMap::new));
        Map<LocalDate, MarginTransaction> mgnMap   = indexByDate(marginList,  MarginTransaction::getTradeDate);
        Map<LocalDate, List<FuturesPosition>> mtxByDate = futuresMtxAllList.stream()
            .collect(Collectors.groupingBy(FuturesPosition::getTradeDate));
        Map<LocalDate, List<FuturesPosition>> tmfByDate = futuresTmfAllList.stream()
            .collect(Collectors.groupingBy(FuturesPosition::getTradeDate));
        Map<LocalDate, FuturesMarketOi> mtxOiMap = indexByDate(mtxOiList, FuturesMarketOi::getTradeDate);
        Map<LocalDate, FuturesMarketOi> tmfOiMap = indexByDate(tmfOiList, FuturesMarketOi::getTradeDate);

        // Use taiex dates as the x-axis base (represents trading days).
        // Other sources may arrive at different times — missing dates get null
        // so Chart.js renders a gap instead of 0.
        List<LocalDate> allTradingDates = taiexList.stream()
            .map(TaiexIndex::getTradeDate)
            .toList();

        int start = Math.max(0, allTradingDates.size() - days);
        List<LocalDate> dates = allTradingDates.subList(start, allTradingDates.size());

        List<String> isoDates       = new ArrayList<>();
        List<String> dateLabels     = new ArrayList<>();
        List<Double> taiexClose     = new ArrayList<>();
        List<Long>   spotNetFlow    = new ArrayList<>();
        List<Long>   marginChange   = new ArrayList<>();
        List<Long>   futuresLongOI  = new ArrayList<>();
        List<Long>   futuresShortOI = new ArrayList<>();
        List<Long>   optionsCallOI  = new ArrayList<>();
        List<Long>   optionsPutOI   = new ArrayList<>();
        List<Long>   optionsCallNetValue = new ArrayList<>();
        List<Long>   optionsPutNetValue  = new ArrayList<>();
        List<Long>   futuresAhLong  = new ArrayList<>();
        List<Long>   futuresAhShort = new ArrayList<>();
        List<Long>   futuresAhNet   = new ArrayList<>();
        List<Double> mtxRatio       = new ArrayList<>();
        List<Double> tmfRatio       = new ArrayList<>();

        for (int i = 0; i < dates.size(); i++) {
            LocalDate date     = dates.get(i);

            TaiexIndex        ti = taiexMap.get(date);
            InstitutionalFlow fl = flowMap.get(date);
            FuturesPosition   txFp  = futTxMap.get(date);
            FuturesPosition   mtxFp = futMtxMap.get(date);
            FuturesPosition   tmfFp = futTmfMap.get(date);
            OptionsPosition   op = optMap.get(date);
            OptionsCallPutPosition cp = callMap.get(date);
            OptionsCallPutPosition pp = putMap.get(date);
            // AH data is stored under the next trading day's date (published after the session
            // ends at 05:00). Use higherKey so we find the entry even when TAIEX日盤 for that
            // next date hasn't been collected yet (e.g. Friday 夜盤 stored under Monday).
            LocalDate nextAhDate = ahMap.higherKey(date);
            FuturesAhPosition ah = nextAhDate != null ? ahMap.get(nextAhDate) : null;
            MarginTransaction mg = mgnMap.get(date);

            isoDates.add(date.toString());
            dateLabels.add(date.format(LABEL_FMT));
            taiexClose.add(ti.getClose() / 100.0);

            spotNetFlow.add(fl != null ? fl.getForeignNet() : null);

            marginChange.add(mg != null ? balanceDelta(mg.getMarginBalance(), mg.getMarginPrevBalance()) : null);

            futuresLongOI.add(txEquivalent(txFp, mtxFp, tmfFp, FuturesPosition::getOiLongVolume));
            futuresShortOI.add(txEquivalent(txFp, mtxFp, tmfFp, FuturesPosition::getOiShortVolume));

            optionsCallOI.add(op != null ? op.getOiLongVolume()  : null);
            optionsPutOI.add(op != null ? op.getOiShortVolume()  : null);

            optionsCallNetValue.add(cp != null ? cp.getOiNetValue() : null);
            optionsPutNetValue.add(pp != null ? pp.getOiNetValue() : null);

            futuresAhLong.add(ah != null ? ah.getTradingLongVolume()  : null);
            futuresAhShort.add(ah != null ? ah.getTradingShortVolume() : null);
            futuresAhNet.add(ah != null ? ah.getTradingNetVolume()   : null);

            mtxRatio.add(retailRatio(mtxByDate.get(date), mtxOiMap.get(date)));
            tmfRatio.add(retailRatio(tmfByDate.get(date), tmfOiMap.get(date)));
        }

        return new DashboardViewModel(
            isoDates, dateLabels, taiexClose, spotNetFlow,
            marginChange,
            futuresLongOI, futuresShortOI,
            optionsCallOI, optionsPutOI,
            optionsCallNetValue, optionsPutNetValue,
            futuresAhLong, futuresAhShort, futuresAhNet,
            mtxRatio, tmfRatio,
            days);
    }

    /**
     * 散戶多空比 = (retailLong - retailShort) / totalOi × 100, where retail = market-wide
     * total OI minus the three institutional trader types combined (dealer+trust+FINI).
     * Null when totalOi is unavailable for the date (chart gap); a missing trader type
     * in {@code institutional} contributes 0 (not a null-the-whole-row).
     */
    private static Double retailRatio(List<FuturesPosition> institutional, FuturesMarketOi marketOi) {
        if (marketOi == null || marketOi.getTotalOi() == null) return null;
        long totalOi = marketOi.getTotalOi();
        long institutionalLong  = sumOi(institutional, FuturesPosition::getOiLongVolume);
        long institutionalShort = sumOi(institutional, FuturesPosition::getOiShortVolume);
        long retailLong  = totalOi - institutionalLong;
        long retailShort = totalOi - institutionalShort;
        return (retailLong - retailShort) / (double) totalOi * 100.0;
    }

    private static long sumOi(List<FuturesPosition> positions, Function<FuturesPosition, Long> field) {
        if (positions == null) return 0L;
        return positions.stream()
            .mapToLong(fp -> {
                Long v = field.apply(fp);
                return v != null ? v : 0L;
            })
            .sum();
    }

    private static long balanceDelta(Long balance, Long prevBalance) {
        return (balance != null ? balance : 0L) - (prevBalance != null ? prevBalance : 0L);
    }

    /**
     * Combines TX, MTX (1 TX = 4 MTX), and TMF (1 TX = 20 TMF) into a single
     * TX-equivalent lot count, rounded to the nearest whole lot. Missing MTX/TMF
     * data for a date contributes 0; a missing TX position yields null (no row).
     */
    private static Long txEquivalent(FuturesPosition tx, FuturesPosition mtx, FuturesPosition tmf,
                                      Function<FuturesPosition, Long> field) {
        if (tx == null) return null;
        Long txVal  = field.apply(tx);
        Long mtxVal = mtx != null ? field.apply(mtx) : null;
        Long tmfVal = tmf != null ? field.apply(tmf) : null;
        double total = (txVal  != null ? txVal  : 0L)
            + (mtxVal != null ? mtxVal / 4.0  : 0.0)
            + (tmfVal != null ? tmfVal / 20.0 : 0.0);
        return Math.round(total);
    }

    private <T> Map<LocalDate, T> indexByDate(List<T> list, Function<T, LocalDate> keyFn) {
        return list.stream().collect(toMap(keyFn, Function.identity(), (a, b) -> b, LinkedHashMap::new));
    }
}
