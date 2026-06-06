package com.eagleeye.web;

import com.eagleeye.domain.entity.FuturesAhPosition;
import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TaiexIndex;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.FuturesAhPositionRepository;
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

    public DashboardService(TaiexIndexRepository taiexRepo,
                            InstitutionalFlowRepository flowRepo,
                            FuturesPositionRepository futuresRepo,
                            FuturesAhPositionRepository futuresAhRepo,
                            OptionsPositionRepository optionsRepo,
                            OptionsCallPutPositionRepository callPutRepo,
                            MarginTransactionRepository marginRepo) {
        this.taiexRepo = taiexRepo;
        this.flowRepo = flowRepo;
        this.futuresRepo = futuresRepo;
        this.futuresAhRepo = futuresAhRepo;
        this.optionsRepo = optionsRepo;
        this.callPutRepo = callPutRepo;
        this.marginRepo = marginRepo;
    }

    public DashboardViewModel buildViewModel(int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days * 2L);

        List<TaiexIndex>        taiexList   = taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<InstitutionalFlow> flowList    = flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<FuturesPosition>   futuresList = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TX", TraderType.FINI, from, to);
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

        Map<LocalDate, TaiexIndex>        taiexMap = indexByDate(taiexList,   TaiexIndex::getTradeDate);
        Map<LocalDate, InstitutionalFlow> flowMap  = indexByDate(flowList,    InstitutionalFlow::getTradeDate);
        Map<LocalDate, FuturesPosition>   futMap   = indexByDate(futuresList, FuturesPosition::getTradeDate);
        Map<LocalDate, OptionsPosition>   optMap   = indexByDate(optionsList, OptionsPosition::getTradeDate);
        Map<LocalDate, OptionsCallPutPosition> callMap = indexByDate(callList, OptionsCallPutPosition::getTradeDate);
        Map<LocalDate, OptionsCallPutPosition> putMap  = indexByDate(putList,  OptionsCallPutPosition::getTradeDate);
        // NavigableMap so we can find the next AH entry after each trading day with higherKey().
        NavigableMap<LocalDate, FuturesAhPosition> ahMap = futuresAhList.stream()
            .collect(toMap(FuturesAhPosition::getTradeDate, Function.identity(), (a, b) -> b, TreeMap::new));
        Map<LocalDate, MarginTransaction> mgnMap   = indexByDate(marginList,  MarginTransaction::getTradeDate);

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
        List<Long>   shortChange    = new ArrayList<>();
        List<Long>   futuresLongOI  = new ArrayList<>();
        List<Long>   futuresShortOI = new ArrayList<>();
        List<Long>   optionsCallOI  = new ArrayList<>();
        List<Long>   optionsPutOI   = new ArrayList<>();
        List<Long>   optionsCallNetValue = new ArrayList<>();
        List<Long>   optionsPutNetValue  = new ArrayList<>();
        List<Long>   futuresAhLong  = new ArrayList<>();
        List<Long>   futuresAhShort = new ArrayList<>();
        List<Long>   futuresAhNet   = new ArrayList<>();

        for (int i = 0; i < dates.size(); i++) {
            LocalDate date     = dates.get(i);

            TaiexIndex        ti = taiexMap.get(date);
            InstitutionalFlow fl = flowMap.get(date);
            FuturesPosition   fp = futMap.get(date);
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
            shortChange.add(mg != null ? balanceDelta(mg.getShortBalance(), mg.getShortPrevBalance()) : null);

            futuresLongOI.add(fp != null ? fp.getOiLongVolume()  : null);
            futuresShortOI.add(fp != null ? fp.getOiShortVolume() : null);

            optionsCallOI.add(op != null ? op.getOiLongVolume()  : null);
            optionsPutOI.add(op != null ? op.getOiShortVolume()  : null);

            optionsCallNetValue.add(cp != null ? cp.getOiNetValue() : null);
            optionsPutNetValue.add(pp != null ? pp.getOiNetValue() : null);

            futuresAhLong.add(ah != null ? ah.getTradingLongVolume()  : null);
            futuresAhShort.add(ah != null ? ah.getTradingShortVolume() : null);
            futuresAhNet.add(ah != null ? ah.getTradingNetVolume()   : null);
        }

        return new DashboardViewModel(
            isoDates, dateLabels, taiexClose, spotNetFlow,
            marginChange, shortChange,
            futuresLongOI, futuresShortOI,
            optionsCallOI, optionsPutOI,
            optionsCallNetValue, optionsPutNetValue,
            futuresAhLong, futuresAhShort, futuresAhNet,
            days);
    }

    private static long balanceDelta(Long balance, Long prevBalance) {
        return (balance != null ? balance : 0L) - (prevBalance != null ? prevBalance : 0L);
    }

    private <T> Map<LocalDate, T> indexByDate(List<T> list, Function<T, LocalDate> keyFn) {
        return list.stream().collect(toMap(keyFn, Function.identity(), (a, b) -> b, LinkedHashMap::new));
    }
}
