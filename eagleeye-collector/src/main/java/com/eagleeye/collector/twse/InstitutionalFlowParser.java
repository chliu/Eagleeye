package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.InstitutionalFlow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Parses TWSE BFI82U institutional investor daily trading value JSON.
 *
 * Response: tables[0].data — rows identified by label in column [0]:
 *   "Foreign Investors" → foreignBuy/Sell/Net
 *   "Investment Trust"  → investmentTrustBuy/Sell/Net
 *   "Dealers"           → dealerBuy/Sell/Net
 * Columns: [1] buy, [2] sell, [3] net — raw NTD integers (comma-formatted).
 */
@Component
public class InstitutionalFlowParser {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalFlowParser.class);
    private final ObjectMapper objectMapper;

    public InstitutionalFlowParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public InstitutionalFlow parse(String json, LocalDate date) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse institutional flow JSON: {}", TwseJsonParser.truncate(json));
            return null;
        }

        String stat = root.path("stat").asText("");
        if (!"OK".equals(stat)) {
            log.info("Institutional flow stat='{}' — no data for {} (raw: {})", stat, date, TwseJsonParser.truncate(json));
            return null;
        }

        JsonNode data = TwseJsonParser.extractTableData(root);
        if (!data.isArray() || data.isEmpty()) {
            log.info("Institutional flow data missing for {} (raw: {})", date, TwseJsonParser.truncate(json));
            return null;
        }

        try {
            InstitutionalFlow flow = new InstitutionalFlow(date);
            for (JsonNode row : data) {
                String label = row.get(0).asText();
                long buy  = TwseJsonParser.toLong(row.get(1).asText());
                long sell = TwseJsonParser.toLong(row.get(2).asText());
                long net  = TwseJsonParser.toLong(row.get(3).asText());
                if (label.contains("Foreign Investors")) {
                    flow.setForeignBuy(buy);
                    flow.setForeignSell(sell);
                    flow.setForeignNet(net);
                } else if (label.contains("Investment Trust")) {
                    flow.setInvestmentTrustBuy(buy);
                    flow.setInvestmentTrustSell(sell);
                    flow.setInvestmentTrustNet(net);
                } else if (label.contains("Dealers")) {
                    flow.setDealerBuy(buy);
                    flow.setDealerSell(sell);
                    flow.setDealerNet(net);
                }
            }
            if (flow.getForeignBuy() == null || flow.getInvestmentTrustBuy() == null || flow.getDealerBuy() == null) {
                log.warn("Institutional flow data incomplete for {} — missing one or more investor groups", date);
                log.debug("Institutional flow raw data: {}", data);
                return null;
            }
            return flow;
        } catch (Exception e) {
            log.warn("Failed to parse institutional flow rows for {}: {}", date, e.getMessage());
            log.debug("Institutional flow raw data: {}", data);
            return null;
        }
    }

}
