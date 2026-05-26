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

        List<LocalDate> alignedDates = taiexList.stream()
            .map(TaiexIndex::getTradeDate)
            .filter(d -> flowMap.containsKey(d) && futMap.containsKey(d)
                      && optMap.containsKey(d) && mgnMap.containsKey(d))
            .toList();

        int start = Math.max(0, alignedDates.size() - days);
        List<LocalDate> dates = alignedDates.subList(start, alignedDates.size());

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

            dateLabels.add(date.format(LABEL_FMT));
            taiexClose.add(ti.getClose() / 100.0);

            spotNetFlow.add(fl.getForeignNet() != null ? fl.getForeignNet() : 0L);

            long mBalance = mg.getMarginBalance()     != null ? mg.getMarginBalance()     : 0L;
            long mPrev    = mg.getMarginPrevBalance() != null ? mg.getMarginPrevBalance() : 0L;
            marginChange.add(mBalance - mPrev);

            long sBalance = mg.getShortBalance()     != null ? mg.getShortBalance()     : 0L;
            long sPrev    = mg.getShortPrevBalance() != null ? mg.getShortPrevBalance() : 0L;
            shortChange.add(sBalance - sPrev);

            futuresLongOI.add(fp.getOiLongVolume()  != null ? fp.getOiLongVolume()  : 0L);
            futuresShortOI.add(fp.getOiShortVolume() != null ? fp.getOiShortVolume() : 0L);

            optionsCallOI.add(op.getOiLongVolume()  != null ? op.getOiLongVolume()  : 0L);
            optionsPutOI.add(op.getOiShortVolume()  != null ? op.getOiShortVolume() : 0L);
        }

        return new DashboardViewModel(
            dateLabels, taiexClose, spotNetFlow,
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
