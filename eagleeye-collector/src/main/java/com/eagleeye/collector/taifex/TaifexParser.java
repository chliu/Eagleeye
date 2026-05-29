package com.eagleeye.collector.taifex;

import com.eagleeye.domain.dto.PositionDto;
import com.eagleeye.domain.entity.TraderType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses TAIFEX institutional position HTML tables (futures and options).
 *
 * Both tables share an identical structure per contract group:
 *
 *   DOM cells:
 *   Row 1 (Dealer):           [SN rowspan=3] [Contract rowspan=3] [Dealers]          [12 data cells]
 *   Row 2 (Investment Trust):                                      [Investment Trust] [12 data cells]
 *   Row 3 (FINI):                                                  [FINI]             [12 data cells]
 *
 * Because the SN cell (rowspan=3) is a number, extractNumbers() on the Dealer row yields
 * 13 values: [SN, d1..d12]. We always take the LAST 12 to skip the SN.
 *
 * Contract identification: select rowspan=3 cell whose text matches [A-Z][A-Z0-9]{1,4}
 * (e.g. TX, MTX, TXO, TEO) — excludes the pure-numeric SN cell.
 *
 * Data column layout (12 values, all in TWD thousands for value fields):
 *   [0]  tradingLongVolume    [1]  tradingLongValue
 *   [2]  tradingShortVolume   [3]  tradingShortValue
 *   [4]  tradingNetVolume     [5]  tradingNetValue
 *   [6]  oiLongVolume         [7]  oiLongValue
 *   [8]  oiShortVolume        [9]  oiShortValue
 *   [10] oiNetVolume          [11] oiNetValue
 */
@Component
public class TaifexParser {

    private static final Logger log = LoggerFactory.getLogger(TaifexParser.class);

    // Contract codes are 2–5 uppercase alphanumeric characters starting with a letter
    private static final String CONTRACT_CODE_PATTERN = "[A-Z][A-Z0-9]{1,4}";

    /**
     * Returns true when TAIFEX responded with "No Data" — weekend or public holiday.
     * Works for both futures and options pages.
     */
    public boolean isNoDataPage(String html) {
        return html != null && (html.contains("No Data") || html.contains("查無資料"));
    }

    public List<PositionDto> parse(String html, LocalDate tradeDate) {
        return parseColumns(html, tradeDate, 12);
    }

    /**
     * Parses the after-hours (夜盤) table, which has 6 data columns instead of 12.
     * The AH table contains only trading data (long/short/net volume+value); OI is absent.
     */
    public List<PositionDto> parseAh(String html, LocalDate tradeDate) {
        return parseColumns(html, tradeDate, 6);
    }

    private List<PositionDto> parseColumns(String html, LocalDate tradeDate, int numDataCols) {
        Document doc = Jsoup.parse(html);
        List<PositionDto> results = new ArrayList<>();

        Element table = findDataTable(doc);
        if (table == null) {
            log.warn("Could not find data table in TAIFEX HTML for date {}", tradeDate);
            return results;
        }

        Elements rows = table.select("tr");

        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);

            String contract = findContractCode(row);
            if (contract == null) continue;

            for (int offset = 0; offset < 3 && (i + offset) < rows.size(); offset++) {
                Element traderRow = rows.get(i + offset);

                String traderLabel = findTraderLabel(traderRow.select("td"));
                if (traderLabel == null) {
                    log.debug("No trader label in row {} for contract {}", i + offset, contract);
                    continue;
                }

                TraderType traderType;
                try {
                    traderType = TraderType.fromLabel(traderLabel);
                } catch (IllegalArgumentException e) {
                    log.debug("Unknown trader label '{}' for contract {}", traderLabel, contract);
                    continue;
                }

                long[] all = extractNumbers(traderRow.select("td"));
                if (all.length < numDataCols) {
                    log.warn("Expected ≥{} numeric cells, got {} for {}/{} on {}",
                            numDataCols, all.length, contract, traderType, tradeDate);
                    continue;
                }

                // Take the last numDataCols — the Dealer row has an extra leading SN number
                long[] nums = Arrays.copyOfRange(all, all.length - numDataCols, all.length);

                results.add(numDataCols == 12
                        ? new PositionDto(tradeDate, contract, traderType,
                                nums[0],  nums[1],   // trading long  vol, val
                                nums[2],  nums[3],   // trading short vol, val
                                nums[4],  nums[5],   // trading net   vol, val
                                nums[6],  nums[7],   // OI     long   vol, val
                                nums[8],  nums[9],   // OI     short  vol, val
                                nums[10], nums[11])  // OI     net    vol, val
                        : new PositionDto(tradeDate, contract, traderType,
                                nums[0],  nums[1],   // trading long  vol, val
                                nums[2],  nums[3],   // trading short vol, val
                                nums[4],  nums[5],   // trading net   vol, val
                                0L, 0L, 0L, 0L, 0L, 0L)); // OI not published for after-hours
            }
        }

        log.info("Parsed {} positions for {}", results.size(), tradeDate);
        return results;
    }

    private Element findDataTable(Document doc) {
        // TAIFEX data tables use class "table_f"
        Element table = doc.selectFirst("table.table_f");
        if (table != null) return table;
        // Fallback: find by content heuristic
        return doc.select("table").stream()
                .filter(t -> t.text().contains("Dealer") && t.text().contains("FINI"))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the contract code from a rowspan=3 cell that matches the contract code pattern.
     * Ignores the SN cell (a pure integer like "1", "2", "3"…).
     */
    private String findContractCode(Element row) {
        for (Element cell : row.select("td[rowspan=3]")) {
            String text = cell.text().trim();
            if (text.matches(CONTRACT_CODE_PATTERN)) {
                return text;
            }
        }
        return null;
    }

    /**
     * Finds the trader-type cell by attempting to parse each cell's text via TraderType.fromLabel().
     * This correctly handles "Dealers" (TAIFEX uses the plural form) and "Investment Trust".
     */
    private String findTraderLabel(Elements cells) {
        for (Element cell : cells) {
            String text = cell.text().trim();
            try {
                TraderType.fromLabel(text);
                return text;
            } catch (IllegalArgumentException ignored) {
                // Not a trader label cell
            }
        }
        return null;
    }

    /**
     * Extracts all parseable long values from a row's cells.
     * Handles parenthesised negatives: (1,234) → -1234
     */
    private long[] extractNumbers(Elements cells) {
        List<Long> numbers = new ArrayList<>();
        for (Element cell : cells) {
            String text = cell.text().trim().replace(",", "").replace(" ", "");
            if (text.isEmpty()) continue;
            if (text.startsWith("(") && text.endsWith(")")) {
                text = "-" + text.substring(1, text.length() - 1);
            }
            try {
                numbers.add(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                // Label cells (contract name, trader name, headers) — skip
            }
        }
        return numbers.stream().mapToLong(Long::longValue).toArray();
    }
}
