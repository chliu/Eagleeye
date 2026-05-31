package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TaifexClient;
import com.eagleeye.collector.taifex.TaifexParser;
import com.eagleeye.domain.dto.OptionsCallPutDto;
import com.eagleeye.domain.dto.PositionDto;
import com.eagleeye.domain.entity.AbstractMarketPosition;
import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.repository.FuturesPositionRepository;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
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
    private final OptionsCallPutPositionRepository callPutRepo;

    public CollectionService(TaifexClient taifexClient,
                             TaifexParser taifexParser,
                             FuturesPositionRepository futuresRepo,
                             OptionsPositionRepository optionsRepo,
                             OptionsCallPutPositionRepository callPutRepo) {
        this.taifexClient = taifexClient;
        this.taifexParser = taifexParser;
        this.futuresRepo = futuresRepo;
        this.optionsRepo = optionsRepo;
        this.callPutRepo = callPutRepo;
    }

    /**
     * Collects futures + options for the given date.
     * Returns NO_DATA when TAIFEX has no records for that date (weekend / holiday).
     */
    @Transactional
    public FuturesOptionsCollectionResult collectAll(LocalDate date) {
        try {
            String futuresHtml = taifexClient.fetchFuturesHtml(date);

            if (taifexParser.isNoDataPage(futuresHtml)) {
                log.info("No trading data for {} — skipping", date);
                return new FuturesOptionsCollectionResult.NoData(date);
            }

            int futures = processFutures(futuresHtml, date);

            String optionsHtml = taifexClient.fetchOptionsHtml(date);
            int options = processOptions(optionsHtml, date);

            String callPutHtml = taifexClient.fetchOptionsCallPutHtml(date);
            processOptionsCallPut(callPutHtml, date);

            log.info("Collected {}: {} futures, {} options positions", date, futures, options);
            return new FuturesOptionsCollectionResult.Collected(date, futures, options);

        } catch (Exception e) {
            log.error("Collection failed for {}: {}", date, e.getMessage(), e);
            return new FuturesOptionsCollectionResult.Error(date, e.getMessage());
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
        int count = processOptions(html, date);
        processOptionsCallPut(taifexClient.fetchOptionsCallPutHtml(date), date);
        return count;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    protected int processFutures(String html, LocalDate date) {
        List<PositionDto> dtos = taifexParser.parse(html, date);
        int inserted = 0, updated = 0;
        for (PositionDto dto : dtos) {
            if (upsertFutures(dto, date)) inserted++; else updated++;
        }
        log.info("Futures positions for {}: {} inserted, {} updated", date, inserted, updated);
        return dtos.size();
    }

    protected int processOptions(String html, LocalDate date) {
        List<PositionDto> dtos = taifexParser.parse(html, date);
        int inserted = 0, updated = 0;
        for (PositionDto dto : dtos) {
            if (upsertOptions(dto, date)) inserted++; else updated++;
        }
        log.info("Options positions for {}: {} inserted, {} updated", date, inserted, updated);
        return dtos.size();
    }

    protected int processOptionsCallPut(String html, LocalDate date) {
        if (taifexParser.isNoDataPage(html)) {
            log.info("No call/put options data for {}", date);
            return 0;
        }
        List<OptionsCallPutDto> dtos = taifexParser.parseCallPut(html, date);
        for (OptionsCallPutDto dto : dtos) {
            upsertOptionsCallPut(dto, date);
        }
        log.info("Call/put net values for {}: {} rows updated", date, dtos.size());
        return dtos.size();
    }

    private void upsertOptionsCallPut(OptionsCallPutDto dto, LocalDate date) {
        PositionDto p = dto.position();
        OptionsCallPutPosition pos = callPutRepo
                .findByTradeDateAndContractAndTraderTypeAndRightType(
                        date, p.contract(), p.traderType(), dto.rightType())
                .orElseGet(() -> new OptionsCallPutPosition(
                        date, p.contract(), p.traderType(), dto.rightType()));
        applyDto(pos, p);
        callPutRepo.save(pos);
    }

    // Returns true if inserted, false if updated
    private boolean upsertFutures(PositionDto dto, LocalDate date) {
        var existing = futuresRepo.findByTradeDateAndContractAndTraderType(date, dto.contract(), dto.traderType());
        FuturesPosition pos = existing.orElseGet(() -> new FuturesPosition(date, dto.contract(), dto.traderType()));
        applyDto(pos, dto);
        futuresRepo.save(pos);
        return existing.isEmpty();
    }

    // Returns true if inserted, false if updated
    private boolean upsertOptions(PositionDto dto, LocalDate date) {
        var existing = optionsRepo.findByTradeDateAndContractAndTraderType(date, dto.contract(), dto.traderType());
        OptionsPosition pos = existing.orElseGet(() -> new OptionsPosition(date, dto.contract(), dto.traderType()));
        applyDto(pos, dto);
        optionsRepo.save(pos);
        return existing.isEmpty();
    }

    private void applyDto(AbstractMarketPosition pos, PositionDto dto) {
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
