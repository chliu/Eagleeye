package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.MarginTransactionParser;
import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.repository.MarginTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class MarginTransactionService {

    private static final Logger log = LoggerFactory.getLogger(MarginTransactionService.class);

    private final TwseClient twseClient;
    private final MarginTransactionParser parser;
    private final MarginTransactionRepository repository;

    public MarginTransactionService(TwseClient twseClient,
                                    MarginTransactionParser parser,
                                    MarginTransactionRepository repository) {
        this.twseClient = twseClient;
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public DateCollectionResult collectDate(LocalDate date) {
        try {
            String json = twseClient.fetchMarginJson(date);
            log.debug("Margin raw JSON for {}: {}", date,
                    json != null && json.length() > 300 ? json.substring(0, 300) + "..." : json);
            MarginTransaction parsed = parser.parse(json, date);
            if (parsed == null) {
                log.info("No margin data for {}", date);
                return new DateCollectionResult.NoData(date);
            }
            upsert(parsed);
            log.info("Collected margin data for {}", date);
            return new DateCollectionResult.Collected(date);
        } catch (Exception e) {
            log.error("Margin collection failed for {}: {}", date, e.getMessage(), e);
            return new DateCollectionResult.Error(date, e.getMessage());
        }
    }

    private void upsert(MarginTransaction source) {
        var existing = repository.findByTradeDate(source.getTradeDate());
        MarginTransaction tx = existing.orElseGet(() -> new MarginTransaction(source.getTradeDate()));

        tx.setMarginPurchase(source.getMarginPurchase());
        tx.setMarginSale(source.getMarginSale());
        tx.setMarginCashRedemption(source.getMarginCashRedemption());
        tx.setMarginPrevBalance(source.getMarginPrevBalance());
        tx.setMarginBalance(source.getMarginBalance());
        tx.setShortCovering(source.getShortCovering());
        tx.setShortSale(source.getShortSale());
        tx.setShortStockRedemption(source.getShortStockRedemption());
        tx.setShortPrevBalance(source.getShortPrevBalance());
        tx.setShortBalance(source.getShortBalance());

        repository.save(tx);
        log.info("{} margin transaction for {}", existing.isPresent() ? "Updated" : "Inserted", source.getTradeDate());
    }
}
