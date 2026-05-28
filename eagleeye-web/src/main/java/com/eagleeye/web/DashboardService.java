package com.eagleeye.web;

import com.eagleeye.domain.entity.*;
import com.eagleeye.domain.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DashboardService {

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
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days * 2L);

        List<TaiexIndex>        taiexList   = taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<InstitutionalFlow> flowList    = flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<FuturesPosition>   futuresList = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TX", TraderType.FINI, from, to);
        List<OptionsPosition>   optionsList = optionsRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TXO", TraderType.FINI, from, to);
        List<MarginTransaction> marginList  = marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);

        Map<LocalDate, TaiexIndex>        taiexMap = indexByDate(taiexList,   TaiexIndex::getTradeDate);
        Map<LocalDate, InstitutionalFlow> flowMap  = indexByDate(flowList,    InstitutionalFlow::getTradeDate);
        Map<LocalDate, FuturesPosition>   futMap   = indexByDate(futuresList, FuturesPosition::getTradeDate);
        Map<LocalDate, OptionsPosition>   optMap   = indexByDate(optionsList, OptionsPosition::getTradeDate);
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

        for (LocalDate date : dates) {
            TaiexIndex        ti = taiexMap.get(date);
            InstitutionalFlow fl = flowMap.get(date);
            FuturesPosition   fp = futMap.get(date);
            OptionsPosition   op = optMap.get(date);
            MarginTransaction mg = mgnMap.get(date);

            isoDates.add(date.toString());
            dateLabels.add(date.format(LABEL_FMT));
            taiexClose.add(ti.getClose() / 100.0);  // ti always non-null (it's the x-axis base)

            spotNetFlow.add(fl != null ? fl.getForeignNet() : null);

            if (mg != null) {
                long mBalance = mg.getMarginBalance()     != null ? mg.getMarginBalance()     : 0L;
                long mPrev    = mg.getMarginPrevBalance() != null ? mg.getMarginPrevBalance() : 0L;
                marginChange.add(mBalance - mPrev);
                long sBalance = mg.getShortBalance()     != null ? mg.getShortBalance()     : 0L;
                long sPrev    = mg.getShortPrevBalance() != null ? mg.getShortPrevBalance() : 0L;
                shortChange.add(sBalance - sPrev);
            } else {
                marginChange.add(null);
                shortChange.add(null);
            }

            futuresLongOI.add(fp != null ? fp.getOiLongVolume()  : null);
            futuresShortOI.add(fp != null ? fp.getOiShortVolume() : null);

            optionsCallOI.add(op != null ? op.getOiLongVolume()  : null);
            optionsPutOI.add(op != null ? op.getOiShortVolume()  : null);
        }

        return new DashboardViewModel(
            isoDates, dateLabels, taiexClose, spotNetFlow,
            marginChange, shortChange,
            futuresLongOI, futuresShortOI,
            optionsCallOI, optionsPutOI,
            days);
    }

    private <T> Map<LocalDate, T> indexByDate(List<T> list,
                                               java.util.function.Function<T, LocalDate> keyFn) {
        Map<LocalDate, T> map = new LinkedHashMap<>();
        for (T item : list) map.put(keyFn.apply(item), item);
        return map;
    }
}
