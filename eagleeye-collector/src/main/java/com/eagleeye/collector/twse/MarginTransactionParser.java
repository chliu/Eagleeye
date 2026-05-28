package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.MarginTransaction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Parses TWSE MI_MARGN market-wide margin transaction JSON for a single date.
 *
 * Row 0 = Margin Purchase (融資): cols [1-5]
 * Row 1 = Short Sale (融券):     cols [1-5]
 */
@Component
public class MarginTransactionParser {

    private static final Logger log = LoggerFactory.getLogger(MarginTransactionParser.class);
    private final ObjectMapper objectMapper;

    public MarginTransactionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MarginTransaction parse(String json, LocalDate date) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse margin JSON: {}", ParseUtils.truncate(json));
            return null;
        }

        String stat = root.path("stat").asText("");
        if (!"OK".equals(stat)) {
            log.info("Margin stat='{}' — no data for {} (raw: {})", stat, date, ParseUtils.truncate(json));
            return null;
        }

        JsonNode data = ParseUtils.extractTableData(root);
        if (!data.isArray() || data.size() < 2) {
            log.info("Margin data missing or incomplete for {} — {} rows (raw: {})",
                    date, data.size(), ParseUtils.truncate(json));
            return null;
        }

        log.debug("Margin raw row[0]: {}", data.get(0));
        log.debug("Margin raw row[1]: {}", data.get(1));
        try {
            JsonNode marginRow = data.get(0);
            JsonNode shortRow  = data.get(1);

            MarginTransaction tx = new MarginTransaction(date);
            tx.setMarginPurchase(ParseUtils.toLong(marginRow.get(1).asText()));
            tx.setMarginSale(ParseUtils.toLong(marginRow.get(2).asText()));
            tx.setMarginCashRedemption(ParseUtils.toLong(marginRow.get(3).asText()));
            tx.setMarginPrevBalance(ParseUtils.toLong(marginRow.get(4).asText()));
            tx.setMarginBalance(ParseUtils.toLong(marginRow.get(5).asText()));
            tx.setShortCovering(ParseUtils.toLong(shortRow.get(1).asText()));
            tx.setShortSale(ParseUtils.toLong(shortRow.get(2).asText()));
            tx.setShortStockRedemption(ParseUtils.toLong(shortRow.get(3).asText()));
            tx.setShortPrevBalance(ParseUtils.toLong(shortRow.get(4).asText()));
            tx.setShortBalance(ParseUtils.toLong(shortRow.get(5).asText()));
            return tx;
        } catch (Exception e) {
            log.warn("Failed to parse margin data rows for {}: {}", date, e.getMessage());
            return null;
        }
    }

}
