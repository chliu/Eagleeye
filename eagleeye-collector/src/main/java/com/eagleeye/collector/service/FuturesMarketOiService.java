package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TaifexClient;
import com.eagleeye.collector.taifex.TaifexMarketReportParser;
import com.eagleeye.domain.entity.FuturesMarketOi;
import com.eagleeye.domain.repository.FuturesMarketOiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class FuturesMarketOiService {

    private static final Logger log = LoggerFactory.getLogger(FuturesMarketOiService.class);
    private static final List<String> CONTRACTS = List.of("MTX", "TMF");

    private final TaifexClient client;
    private final TaifexMarketReportParser parser;
    private final FuturesMarketOiRepository repository;

    public FuturesMarketOiService(TaifexClient client, TaifexMarketReportParser parser,
                                   FuturesMarketOiRepository repository) {
        this.client = client;
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public DateCollectionResult collectDate(LocalDate date) {
        try {
            int collected = 0;
            for (String contract : CONTRACTS) {
                String html = client.fetchDailyMarketReportHtml(date, contract);
                if (parser.isNoDataPage(html)) {
                    log.info("No daily market report data for {}/{}", contract, date);
                    continue;
                }
                Long totalOi = parser.parseTotalOi(html, date, contract);
                if (totalOi == null) continue;
                upsert(date, contract, totalOi);
                collected++;
            }
            if (collected == 0) {
                log.info("No market OI data for {}", date);
                return new DateCollectionResult.NoData(date);
            }
            log.info("Collected market OI for {} ({} of {} contracts)", date, collected, CONTRACTS.size());
            return new DateCollectionResult.Collected(date);
        } catch (Exception e) {
            log.error("Market OI collection failed for {}: {}", date, e.getMessage(), e);
            return new DateCollectionResult.Error(date, e.getMessage());
        }
    }

    private void upsert(LocalDate date, String contract, Long totalOi) {
        var existing = repository.findByTradeDateAndContract(date, contract);
        FuturesMarketOi entity = existing.orElseGet(() -> new FuturesMarketOi(date, contract));
        entity.setTotalOi(totalOi);
        repository.save(entity);
        log.info("{} market OI for {}/{}: {}", existing.isPresent() ? "Updated" : "Inserted", contract, date, totalOi);
    }
}
