package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
import com.eagleeye.domain.entity.TaiexDailyBar;
import com.eagleeye.domain.repository.TaiexDailyBarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
public class MarketIndexService {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexService.class);

    private final TwseClient twseClient;
    private final TwseParser twseParser;
    private final TaiexDailyBarRepository repository;

    public MarketIndexService(TwseClient twseClient,
                              TwseParser twseParser,
                              TaiexDailyBarRepository repository) {
        this.twseClient = twseClient;
        this.twseParser = twseParser;
        this.repository = repository;
    }

    @Transactional
    public MarketIndexCollectionResult collectMonth(YearMonth yearMonth) {
        try {
            String ohlcJson = twseClient.fetchMonthJson(yearMonth);
            List<TaiexDailyBar> bars = twseParser.parse(ohlcJson);

            if (bars.isEmpty()) {
                log.info("No TAIEX data for {} — skipping", yearMonth);
                return MarketIndexCollectionResult.noData(yearMonth);
            }

            String statsJson = twseClient.fetchMarketStatsJson(yearMonth);
            Map<LocalDate, long[]> volumeByDate = twseParser.parseVolumeByDate(statsJson);
            bars.forEach(bar -> {
                long[] vt = volumeByDate.get(bar.getTradeDate());
                if (vt != null) {
                    bar.setVolume(vt[0]);
                    bar.setTurnover(vt[1]);
                }
            });

            bars.forEach(this::upsert);
            log.info("Collected {} TAIEX bars for {}", bars.size(), yearMonth);
            return MarketIndexCollectionResult.collected(yearMonth, bars.size());

        } catch (Exception e) {
            log.error("TAIEX collection failed for {}: {}", yearMonth, e.getMessage(), e);
            return MarketIndexCollectionResult.error(yearMonth, e.getMessage());
        }
    }

    public MarketIndexCollectionResult collectDate(LocalDate date) {
        return collectMonth(YearMonth.from(date));
    }

    private void upsert(TaiexDailyBar parsed) {
        TaiexDailyBar bar = repository
                .findByTradeDate(parsed.getTradeDate())
                .orElseGet(() -> new TaiexDailyBar(parsed.getTradeDate()));

        bar.setOpen(parsed.getOpen());
        bar.setHigh(parsed.getHigh());
        bar.setLow(parsed.getLow());
        bar.setClose(parsed.getClose());
        bar.setVolume(parsed.getVolume());
        bar.setTurnover(parsed.getTurnover());

        repository.save(bar);
    }
}
