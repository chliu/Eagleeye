package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
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
    private final TwseParser twseParser;
    private final MarginTransactionRepository repository;

    public MarginTransactionService(TwseClient twseClient,
                                    TwseParser twseParser,
                                    MarginTransactionRepository repository) {
        this.twseClient = twseClient;
        this.twseParser = twseParser;
        this.repository = repository;
    }

    @Transactional
    public MarginCollectionResult collectDate(LocalDate date) {
        try {
            String json = twseClient.fetchMarginJson(date);
            log.debug("Margin raw JSON for {}: {}", date,
                    json != null && json.length() > 300 ? json.substring(0, 300) + "..." : json);
            MarginTransaction parsed = twseParser.parseMargin(json, date);
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
        MarginTransaction bar = repository
                .findByTradeDate(parsed.getTradeDate())
                .orElseGet(() -> new MarginTransaction(parsed.getTradeDate()));

        bar.setMarginPurchase(parsed.getMarginPurchase());
        bar.setMarginSale(parsed.getMarginSale());
        bar.setMarginCashRedemption(parsed.getMarginCashRedemption());
        bar.setMarginPrevBalance(parsed.getMarginPrevBalance());
        bar.setMarginBalance(parsed.getMarginBalance());
        bar.setShortCovering(parsed.getShortCovering());
        bar.setShortSale(parsed.getShortSale());
        bar.setShortStockRedemption(parsed.getShortStockRedemption());
        bar.setShortPrevBalance(parsed.getShortPrevBalance());
        bar.setShortBalance(parsed.getShortBalance());

        repository.save(bar);
    }
}
