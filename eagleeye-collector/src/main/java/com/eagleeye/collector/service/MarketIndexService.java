package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TaiexIndexParser;
import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.domain.entity.TaiexIndex;
import com.eagleeye.domain.repository.TaiexIndexRepository;
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
    private final TaiexIndexParser parser;
    private final TaiexIndexRepository repository;

    public MarketIndexService(TwseClient twseClient,
                              TaiexIndexParser parser,
                              TaiexIndexRepository repository) {
        this.twseClient = twseClient;
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public MarketIndexCollectionResult collectMonth(YearMonth yearMonth) {
        try {
            String ohlcJson = twseClient.fetchMonthJson(yearMonth);
            List<TaiexIndex> bars = parser.parse(ohlcJson);

            if (bars.isEmpty()) {
                log.info("No TAIEX data for {} — skipping", yearMonth);
                return MarketIndexCollectionResult.noData(yearMonth);
            }

            String statsJson = twseClient.fetchMarketStatsJson(yearMonth);
            Map<LocalDate, long[]> volumeByDate = parser.parseVolumeByDate(statsJson);
            bars.forEach(bar -> {
                long[] vt = volumeByDate.get(bar.getTradeDate());
                if (vt != null) {
                    bar.setVolume(vt[0]);
                    bar.setTurnover(vt[1]);
                }
            });

            int inserted = 0, updated = 0;
            for (TaiexIndex bar : bars) {
                if (upsert(bar)) inserted++; else updated++;
            }
            log.info("TAIEX bars for {}: {} inserted, {} updated", yearMonth, inserted, updated);
            return MarketIndexCollectionResult.collected(yearMonth, bars.size());

        } catch (Exception e) {
            log.error("TAIEX collection failed for {}: {}", yearMonth, e.getMessage(), e);
            return MarketIndexCollectionResult.error(yearMonth, e.getMessage());
        }
    }

    public MarketIndexCollectionResult collectMonthContaining(LocalDate date) {
        return collectMonth(YearMonth.from(date));
    }

    private boolean upsert(TaiexIndex source) {
        var existing = repository.findByTradeDate(source.getTradeDate());
        TaiexIndex bar = existing.orElseGet(() -> new TaiexIndex(source.getTradeDate()));

        bar.setOpen(source.getOpen());
        bar.setHigh(source.getHigh());
        bar.setLow(source.getLow());
        bar.setClose(source.getClose());
        bar.setVolume(source.getVolume());
        bar.setTurnover(source.getTurnover());

        repository.save(bar);
        return existing.isEmpty();
    }
}
