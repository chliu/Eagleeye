package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.TaiexIndex;
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
 * Parses TWSE MI_5MINS_HIST (OHLC) and FMTQIK (volume/turnover) responses.
 *
 * Column order for MI_5MINS_HIST:
 *   [0] Date (ROC "YYY/MM/DD"), [1] Open, [2] High, [3] Low, [4] Close
 * OHLC stored as fixed-point integers (×100): "20,234.56" → 2023456L
 *
 * Column order for FMTQIK:
 *   [0] Date, [1] Volume (shares), [2] Turnover (NTD), [3-5] ignored
 */
@Component
public class TaiexIndexParser {

    private static final Logger log = LoggerFactory.getLogger(TaiexIndexParser.class);
    private final ObjectMapper objectMapper;

    public TaiexIndexParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TaiexIndex> parse(String json) {
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

        List<TaiexIndex> bars = new ArrayList<>();
        for (JsonNode row : dataNode) {
            TaiexIndex bar = parseRow(row);
            if (bar != null) bars.add(bar);
        }

        log.info("Parsed {} TAIEX bars", bars.size());
        return bars;
    }

    private TaiexIndex parseRow(JsonNode row) {
        try {
            LocalDate tradeDate = parseRocDate(row.get(0).asText());
            TaiexIndex bar = new TaiexIndex(tradeDate);
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

    // ROC year 115 = 2026 (115 + 1911)
    private LocalDate parseRocDate(String rocDate) {
        String[] parts = rocDate.split("/");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Unexpected ROC date format: " + rocDate);
        }
        return LocalDate.of(Integer.parseInt(parts[0]) + 1911,
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
    }

    private long toFixedPoint(String value) {
        return new BigDecimal(value.replace(",", "")).multiply(new BigDecimal("100")).longValueExact();
    }

    private long toLong(String value) {
        return Long.parseLong(value.replace(",", ""));
    }

    private String truncate(String s) {
        if (s == null) return "<null>";
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
