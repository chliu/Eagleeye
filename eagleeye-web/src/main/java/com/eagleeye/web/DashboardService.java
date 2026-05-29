package com.eagleeye.web;

import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.entity.TaiexIndex;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.FuturesPositionRepository;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import com.eagleeye.domain.repository.MarginTransactionRepository;
import com.eagleeye.domain.repository.OptionsPositionRepository;
import com.eagleeye.domain.repository.TaiexIndexRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

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

            marginChange.add(mg != null ? balanceDelta(mg.getMarginBalance(), mg.getMarginPrevBalance()) : null);
            shortChange.add(mg != null ? balanceDelta(mg.getShortBalance(), mg.getShortPrevBalance()) : null);

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

    private static long balanceDelta(Long balance, Long prevBalance) {
        return (balance != null ? balance : 0L) - (prevBalance != null ? prevBalance : 0L);
    }

    private <T> Map<LocalDate, T> indexByDate(List<T> list, Function<T, LocalDate> keyFn) {
        return list.stream().collect(toMap(keyFn, Function.identity(), (a, b) -> b, LinkedHashMap::new));
    }
}
