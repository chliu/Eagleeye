package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TaifexClient;
import com.eagleeye.collector.taifex.TaifexParser;
import com.eagleeye.domain.dto.PositionDto;
import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.repository.FuturesPositionRepository;
import com.eagleeye.domain.repository.OptionsPositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);

    private final TaifexClient taifexClient;
    private final TaifexParser taifexParser;
    private final FuturesPositionRepository futuresRepo;
    private final OptionsPositionRepository optionsRepo;

    public CollectionService(TaifexClient taifexClient,
                             TaifexParser taifexParser,
                             FuturesPositionRepository futuresRepo,
                             OptionsPositionRepository optionsRepo) {
        this.taifexClient = taifexClient;
        this.taifexParser = taifexParser;
        this.futuresRepo = futuresRepo;
        this.optionsRepo = optionsRepo;
    }

    /**
     * Collects futures + options for the given date.
     * Returns NO_DATA when TAIFEX has no records for that date (weekend / holiday).
     */
    public CollectionResult collectAll(LocalDate date) {
        try {
            String futuresHtml = taifexClient.fetchFuturesHtml(date);

            if (taifexParser.isNoDataPage(futuresHtml)) {
                log.info("No trading data for {} — skipping", date);
                return CollectionResult.noData(date);
            }

            int futures = processFutures(futuresHtml, date);

            String optionsHtml = taifexClient.fetchOptionsHtml(date);
            int options = processOptions(optionsHtml, date);

            log.info("Collected {}: {} futures, {} options positions", date, futures, options);
            return CollectionResult.collected(date, futures, options);

        } catch (Exception e) {
            log.error("Collection failed for {}: {}", date, e.getMessage(), e);
            return CollectionResult.error(date, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Single-type collection (kept for shell commands and scheduler)
    // -----------------------------------------------------------------------

    @Transactional
    public int collectFutures(LocalDate date) {
        log.info("Collecting futures for {}", date);
        String html = taifexClient.fetchFuturesHtml(date);
        if (taifexParser.isNoDataPage(html)) {
            log.info("No futures data for {}", date);
            return 0;
        }
        return processFutures(html, date);
    }

    @Transactional
    public int collectOptions(LocalDate date) {
        log.info("Collecting options for {}", date);
        String html = taifexClient.fetchOptionsHtml(date);
        if (taifexParser.isNoDataPage(html)) {
            log.info("No options data for {}", date);
            return 0;
        }
        return processOptions(html, date);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    @Transactional
    protected int processFutures(String html, LocalDate date) {
        List<PositionDto> dtos = taifexParser.parse(html, date);
        int inserted = 0, updated = 0;
        for (PositionDto dto : dtos) {
            if (upsertFutures(dto, date)) inserted++; else updated++;
        }
        log.info("Futures positions for {}: {} inserted, {} updated", date, inserted, updated);
        return dtos.size();
    }

    @Transactional
    protected int processOptions(String html, LocalDate date) {
        List<PositionDto> dtos = taifexParser.parse(html, date);
        int inserted = 0, updated = 0;
        for (PositionDto dto : dtos) {
            if (upsertOptions(dto, date)) inserted++; else updated++;
        }
        log.info("Options positions for {}: {} inserted, {} updated", date, inserted, updated);
        return dtos.size();
    }

    // Returns true if inserted, false if updated
    private boolean upsertFutures(PositionDto dto, LocalDate date) {
        var existing = futuresRepo.findByTradeDateAndContractAndTraderType(date, dto.contract(), dto.traderType());
        FuturesPosition pos = existing.orElseGet(() -> new FuturesPosition(date, dto.contract(), dto.traderType()));
        applyToFutures(pos, dto);
        futuresRepo.save(pos);
        return existing.isEmpty();
    }

    // Returns true if inserted, false if updated
    private boolean upsertOptions(PositionDto dto, LocalDate date) {
        var existing = optionsRepo.findByTradeDateAndContractAndTraderType(date, dto.contract(), dto.traderType());
        OptionsPosition pos = existing.orElseGet(() -> new OptionsPosition(date, dto.contract(), dto.traderType()));
        applyToOptions(pos, dto);
        optionsRepo.save(pos);
        return existing.isEmpty();
    }

    private void applyToFutures(FuturesPosition pos, PositionDto dto) {
        pos.setTradingLongVolume(dto.tradingLongVolume());
        pos.setTradingLongValue(dto.tradingLongValue());
        pos.setTradingShortVolume(dto.tradingShortVolume());
        pos.setTradingShortValue(dto.tradingShortValue());
        pos.setTradingNetVolume(dto.tradingNetVolume());
        pos.setTradingNetValue(dto.tradingNetValue());
        pos.setOiLongVolume(dto.oiLongVolume());
        pos.setOiLongValue(dto.oiLongValue());
        pos.setOiShortVolume(dto.oiShortVolume());
        pos.setOiShortValue(dto.oiShortValue());
        pos.setOiNetVolume(dto.oiNetVolume());
        pos.setOiNetValue(dto.oiNetValue());
    }

    private void applyToOptions(OptionsPosition pos, PositionDto dto) {
        pos.setTradingLongVolume(dto.tradingLongVolume());
        pos.setTradingLongValue(dto.tradingLongValue());
        pos.setTradingShortVolume(dto.tradingShortVolume());
        pos.setTradingShortValue(dto.tradingShortValue());
        pos.setTradingNetVolume(dto.tradingNetVolume());
        pos.setTradingNetValue(dto.tradingNetValue());
        pos.setOiLongVolume(dto.oiLongVolume());
        pos.setOiLongValue(dto.oiLongValue());
        pos.setOiShortVolume(dto.oiShortVolume());
        pos.setOiShortValue(dto.oiShortValue());
        pos.setOiNetVolume(dto.oiNetVolume());
        pos.setOiNetValue(dto.oiNetValue());
    }
}
