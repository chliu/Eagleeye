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
    public MarginCollectionResult collectDate(LocalDate date) {
        try {
            String json = twseClient.fetchMarginJson(date);
            log.debug("Margin raw JSON for {}: {}", date,
                    json != null && json.length() > 300 ? json.substring(0, 300) + "..." : json);
            MarginTransaction parsed = parser.parse(json, date);
            if (parsed == null) {
                log.info("No margin data for {}", date);
                return MarginCollectionResult.noData(date);
            }
            upsert(parsed);
            log.info("Collected margin data for {}", date);
            return MarginCollectionResult.collected(date);
        } catch (Exception e) {
            log.error("Margin collection failed for {}: {}", date, e.getMessage(), e);
            return MarginCollectionResult.error(date, e.getMessage());
        }
    }

    private void upsert(MarginTransaction parsed) {
        var found = repository.findByTradeDate(parsed.getTradeDate());
        MarginTransaction existing = found.orElseGet(() -> new MarginTransaction(parsed.getTradeDate()));

        existing.setMarginPurchase(parsed.getMarginPurchase());
        existing.setMarginSale(parsed.getMarginSale());
        existing.setMarginCashRedemption(parsed.getMarginCashRedemption());
        existing.setMarginPrevBalance(parsed.getMarginPrevBalance());
        existing.setMarginBalance(parsed.getMarginBalance());
        existing.setShortCovering(parsed.getShortCovering());
        existing.setShortSale(parsed.getShortSale());
        existing.setShortStockRedemption(parsed.getShortStockRedemption());
        existing.setShortPrevBalance(parsed.getShortPrevBalance());
        existing.setShortBalance(parsed.getShortBalance());

        repository.save(existing);
        log.info("{} margin transaction for {}", found.isPresent() ? "Updated" : "Inserted", parsed.getTradeDate());
    }
}
