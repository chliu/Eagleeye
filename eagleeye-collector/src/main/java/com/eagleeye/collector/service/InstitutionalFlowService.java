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
    public InstitutionalFlowResult collectDate(LocalDate date) {
        try {
            String json = twseClient.fetchInstitutionalFlowJson(date);
            log.debug("Institutional flow raw JSON for {}: {}", date,
                    json != null && json.length() > 300 ? json.substring(0, 300) + "..." : json);
            InstitutionalFlow parsed = parser.parse(json, date);
            if (parsed == null) {
                log.info("No institutional flow data for {}", date);
                return InstitutionalFlowResult.noData(date);
            }
            upsert(parsed);
            log.info("Collected institutional flow data for {}", date);
            return InstitutionalFlowResult.collected(date);
        } catch (Exception e) {
            log.error("Institutional flow collection failed for {}: {}", date, e.getMessage(), e);
            return InstitutionalFlowResult.error(date, e.getMessage());
        }
    }

    private void upsert(InstitutionalFlow parsed) {
        var existing = repository.findByTradeDate(parsed.getTradeDate());
        InstitutionalFlow flow = existing.orElseGet(() -> new InstitutionalFlow(parsed.getTradeDate()));

        flow.setForeignBuy(parsed.getForeignBuy());
        flow.setForeignSell(parsed.getForeignSell());
        flow.setForeignNet(parsed.getForeignNet());
        flow.setInvestmentTrustBuy(parsed.getInvestmentTrustBuy());
        flow.setInvestmentTrustSell(parsed.getInvestmentTrustSell());
        flow.setInvestmentTrustNet(parsed.getInvestmentTrustNet());
        flow.setDealerBuy(parsed.getDealerBuy());
        flow.setDealerSell(parsed.getDealerSell());
        flow.setDealerNet(parsed.getDealerNet());

        repository.save(flow);
        log.info("{} institutional flow for {}", existing.isPresent() ? "Updated" : "Inserted", parsed.getTradeDate());
    }
}
