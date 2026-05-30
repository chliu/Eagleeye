package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TaifexClient;
import com.eagleeye.collector.taifex.TaifexParser;
import com.eagleeye.domain.dto.PositionDto;
import com.eagleeye.domain.entity.FuturesAhPosition;
import com.eagleeye.domain.repository.FuturesAhPositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class FuturesAhService {

    private static final Logger log = LoggerFactory.getLogger(FuturesAhService.class);

    private final TaifexClient taifexClient;
    private final TaifexParser taifexParser;
    private final FuturesAhPositionRepository repository;

    public FuturesAhService(TaifexClient taifexClient,
                            TaifexParser taifexParser,
                            FuturesAhPositionRepository repository) {
        this.taifexClient = taifexClient;
        this.taifexParser = taifexParser;
        this.repository = repository;
    }

    @Transactional
    public DateCollectionResult collectDate(LocalDate date) {
        try {
            String html = taifexClient.fetchFuturesAhHtml(date);
            if (taifexParser.isNoDataPage(html)) {
                log.info("No after-hours futures data for {}", date);
                return new DateCollectionResult.NoData(date);
            }
            List<PositionDto> dtos = taifexParser.parseAh(html, date);
            upsertAll(dtos, date);
            log.info("Collected {} after-hours futures positions for {}", dtos.size(), date);
            return new DateCollectionResult.Collected(date);
        } catch (Exception e) {
            log.error("After-hours futures collection failed for {}: {}", date, e.getMessage(), e);
            return new DateCollectionResult.Error(date, e.getMessage());
        }
    }

    private void upsertAll(List<PositionDto> dtos, LocalDate date) {
        int inserted = 0, updated = 0;
        for (PositionDto dto : dtos) {
            if (upsert(dto, date)) inserted++; else updated++;
        }
        log.info("After-hours futures for {}: {} inserted, {} updated", date, inserted, updated);
    }

    private boolean upsert(PositionDto dto, LocalDate date) {
        var existing = repository.findByTradeDateAndContractAndTraderType(date, dto.contract(), dto.traderType());
        FuturesAhPosition pos = existing.orElseGet(() -> new FuturesAhPosition(date, dto.contract(), dto.traderType()));
        applyToPosition(pos, dto);
        repository.save(pos);
        return existing.isEmpty();
    }

    private void applyToPosition(FuturesAhPosition pos, PositionDto dto) {
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
