package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.InstitutionalFlowParser;
import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class InstitutionalFlowService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalFlowService.class);

    private final TwseClient twseClient;
    private final InstitutionalFlowParser parser;
    private final InstitutionalFlowRepository repository;

    public InstitutionalFlowService(TwseClient twseClient,
                                    InstitutionalFlowParser parser,
                                    InstitutionalFlowRepository repository) {
        this.twseClient = twseClient;
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public DateCollectionResult collectDate(LocalDate date) {
        try {
            String json = twseClient.fetchInstitutionalFlowJson(date);
            log.debug("Institutional flow raw JSON for {}: {}", date,
                    json != null && json.length() > 300 ? json.substring(0, 300) + "..." : json);
            InstitutionalFlow parsed = parser.parse(json, date);
            if (parsed == null) {
                log.info("No institutional flow data for {}", date);
                return DateCollectionResult.noData(date);
            }
            upsert(parsed);
            log.info("Collected institutional flow data for {}", date);
            return DateCollectionResult.collected(date);
        } catch (Exception e) {
            log.error("Institutional flow collection failed for {}: {}", date, e.getMessage(), e);
            return DateCollectionResult.error(date, e.getMessage());
        }
    }

    private void upsert(InstitutionalFlow source) {
        var existing = repository.findByTradeDate(source.getTradeDate());
        InstitutionalFlow flow = existing.orElseGet(() -> new InstitutionalFlow(source.getTradeDate()));

        flow.setForeignBuy(source.getForeignBuy());
        flow.setForeignSell(source.getForeignSell());
        flow.setForeignNet(source.getForeignNet());
        flow.setInvestmentTrustBuy(source.getInvestmentTrustBuy());
        flow.setInvestmentTrustSell(source.getInvestmentTrustSell());
        flow.setInvestmentTrustNet(source.getInvestmentTrustNet());
        flow.setDealerBuy(source.getDealerBuy());
        flow.setDealerSell(source.getDealerSell());
        flow.setDealerNet(source.getDealerNet());

        repository.save(flow);
        log.info("{} institutional flow for {}", existing.isPresent() ? "Updated" : "Inserted", source.getTradeDate());
    }
}
