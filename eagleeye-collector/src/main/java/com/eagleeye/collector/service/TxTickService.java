package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TxTickDownloader;
import com.eagleeye.collector.taifex.TxTickParser;
import com.eagleeye.domain.entity.TxTick;
import com.eagleeye.domain.repository.TxTickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TxTickService {

    private static final Logger log = LoggerFactory.getLogger(TxTickService.class);

    private final TxTickDownloader downloader;
    private final TxTickParser parser;
    private final TxTickRepository repository;

    public TxTickService(TxTickDownloader downloader, TxTickParser parser, TxTickRepository repository) {
        this.downloader = downloader;
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public DateCollectionResult collectDate(LocalDate date) {
        try {
            List<String> lines = downloader.downloadLines(date);
            if (lines == null) {
                log.info("No tick data for {}", date);
                return new DateCollectionResult.NoData(date);
            }
            List<TxTick> ticks = parser.parse(lines, date);
            if (ticks.isEmpty()) {
                log.info("No TX ticks after cleaning for {}", date);
                return new DateCollectionResult.NoData(date);
            }
            repository.deleteByTradeDate(date);
            repository.saveAll(ticks);
            log.info("Collected {} TX ticks for {}", ticks.size(), date);
            return new DateCollectionResult.Collected(date);
        } catch (Exception e) {
            log.error("TX tick collection failed for {}: {}", date, e.getMessage(), e);
            return new DateCollectionResult.Error(date, e.getMessage());
        }
    }
}
