package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.MarginDailyBar;
import com.eagleeye.domain.entity.TaiexDailyBar;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses TWSE TAIEX monthly JSON into TaiexDailyBar entities.
 *
 * Column order (positional):
 *   [0] Date (ROC calendar "YYY/MM/DD")
 *   [1] Open, [2] High, [3] Low, [4] Close  (fractional, comma-separated)
 *
 * Volume and turnover are not provided by this endpoint and will be null.
 *
 * OHLC values are stored as fixed-point integers (×100):
 *   "20,234.56" → strip commas → BigDecimal("20234.56") × 100 → 2023456L
 */
@Component
public class TwseParser {

    private static final Logger log = LoggerFactory.getLogger(TwseParser.class);
    private final ObjectMapper objectMapper;

    public TwseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TaiexDailyBar> parse(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse TWSE JSON: " + truncate(json), e);
        }

        String stat = root.path("stat").asText("");
        if (!"OK".equals(stat)) {
            log.info("TWSE stat='{}' — no data", stat);
            return List.of();
        }

        JsonNode dataNode = root.path("data");
        if (!dataNode.isArray() || dataNode.isEmpty()) {
            log.info("TWSE data array is empty");
            return List.of();
        }

        List<TaiexDailyBar> bars = new ArrayList<>();
        for (JsonNode row : dataNode) {
            TaiexDailyBar bar = parseRow(row);
            if (bar != null) bars.add(bar);
        }

        log.info("Parsed {} TAIEX bars", bars.size());
        return bars;
    }

    private TaiexDailyBar parseRow(JsonNode row) {
        try {
            LocalDate tradeDate = parseRocDate(row.get(0).asText());
            TaiexDailyBar bar = new TaiexDailyBar(tradeDate);
            bar.setOpen(toFixedPoint(row.get(1).asText()));
            bar.setHigh(toFixedPoint(row.get(2).asText()));
            bar.setLow(toFixedPoint(row.get(3).asText()));
            bar.setClose(toFixedPoint(row.get(4).asText()));
            return bar;
        } catch (Exception e) {
            log.warn("Skipping unparseable row {}: {}", row, e.getMessage());
            return null;
        }
    }

    /**
     * Parses FMTQIK market statistics JSON into a map of date → [volume, turnover].
     *
     * Column order:
     *   [0] Date (ROC "YYY/MM/DD")
     *   [1] Volume — 成交股數 (shares traded)
     *   [2] Turnover — 成交金額 (NTD value)
     *   [3] Transaction count (ignored)
     *   [4] TAIEX close (ignored — already from MI_5MINS_HIST)
     *   [5] Change (ignored)
     */
    public Map<LocalDate, long[]> parseVolumeByDate(String json) {
        Map<LocalDate, long[]> result = new HashMap<>();
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse market stats JSON: {}", truncate(json));
            return result;
        }

        if (!"OK".equals(root.path("stat").asText(""))) {
            return result;
        }

        for (JsonNode row : root.path("data")) {
            try {
                LocalDate date = parseRocDate(row.get(0).asText());
                long volume   = toLong(row.get(1).asText());
                long turnover = toLong(row.get(2).asText());
                result.put(date, new long[]{volume, turnover});
            } catch (Exception e) {
                log.warn("Skipping unparseable market stats row {}: {}", row, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Parses MI_MARGN market-wide margin transaction JSON for a single date.
     *
     * Row 0 = Margin Purchase (融資): cols [1-5]
     * Row 1 = Short Sale (融券):     cols [1-5]
     * All values are plain trading units (lots/張) — no fixed-point conversion.
     */
    public MarginDailyBar parseMargin(String json, LocalDate date) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse margin JSON: {}", truncate(json));
            return null;
        }

        if (!"OK".equals(root.path("stat").asText(""))) {
            log.info("Margin stat='{}' — no data for {}", root.path("stat").asText(""), date);
            return null;
        }

        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() < 2) {
            log.info("Margin data missing or incomplete for {}", date);
            return null;
        }

        try {
            JsonNode marginRow = data.get(0);
            JsonNode shortRow  = data.get(1);

            MarginDailyBar bar = new MarginDailyBar(date);
            bar.setMarginPurchase(toLong(marginRow.get(1).asText()));
            bar.setMarginSale(toLong(marginRow.get(2).asText()));
            bar.setMarginCashRedemption(toLong(marginRow.get(3).asText()));
            bar.setMarginPrevBalance(toLong(marginRow.get(4).asText()));
            bar.setMarginBalance(toLong(marginRow.get(5).asText()));
            bar.setShortCovering(toLong(shortRow.get(1).asText()));
            bar.setShortSale(toLong(shortRow.get(2).asText()));
            bar.setShortStockRedemption(toLong(shortRow.get(3).asText()));
            bar.setShortPrevBalance(toLong(shortRow.get(4).asText()));
            bar.setShortBalance(toLong(shortRow.get(5).asText()));
            return bar;
        } catch (Exception e) {
            log.warn("Failed to parse margin data rows for {}: {}", date, e.getMessage());
            return null;
        }
    }

    /**
     * Parses Republic of China calendar date string "YYY/MM/DD" to LocalDate.
     * ROC year 115 = 2026 (115 + 1911 = 2026).
     */
    private LocalDate parseRocDate(String rocDate) {
        String[] parts = rocDate.split("/");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Unexpected ROC date format: " + rocDate);
        }
        int year = Integer.parseInt(parts[0]) + 1911;
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);
        return LocalDate.of(year, month, day);
    }

    /**
     * Converts a comma-formatted fractional string to a fixed-point Long (×100).
     * "20,234.56" → 2023456L
     */
    private long toFixedPoint(String value) {
        String clean = value.replace(",", "");
        return new BigDecimal(clean).multiply(new BigDecimal("100")).longValueExact();
    }

    /**
     * Converts a comma-formatted integer string to Long.
     * "3,456,789" → 3456789L
     */
    private long toLong(String value) {
        return Long.parseLong(value.replace(",", ""));
    }

    private String truncate(String s) {
        if (s == null) return "<null>";
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
