package com.eagleeye.collector.taifex;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Parses TAIFEX's daily market report (期貨每日交易行情) — total open interest
 * per contract across all trader types, summed across contract months via the
 * report's own "Subtotal:" row. Distinct report/row shape from {@link TaifexParser},
 * which handles the three-major-institutional-investors tables.
 */
@Component
public class TaifexMarketReportParser {

    private static final Logger log = LoggerFactory.getLogger(TaifexMarketReportParser.class);

    // 17-cell row: ... [11] Settlement Price [12] Open Interest [13] Best Bid ...
    private static final int OPEN_INTEREST_COLUMN = 12;

    public boolean isNoDataPage(String html) {
        return html != null && (html.contains("No Data") || html.contains("查無資料"));
    }

    /**
     * Returns the total open interest across all contract months for {@code contract}
     * on {@code tradeDate} — the report's "Subtotal:" row, Open Interest column — or
     * {@code null} if the table or that row can't be found.
     */
    public Long parseTotalOi(String html, LocalDate tradeDate, String contract) {
        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("table.table_f");
        if (table == null) {
            log.warn("Could not find data table in TAIFEX daily market report HTML for {}/{}", contract, tradeDate);
            return null;
        }

        for (Element row : table.select("tr")) {
            Elements cells = row.select("td");
            boolean isSubtotal = cells.stream().anyMatch(c -> c.text().trim().equals("Subtotal:"));
            if (!isSubtotal) continue;

            if (cells.size() <= OPEN_INTEREST_COLUMN) {
                log.warn("Subtotal row has too few cells ({}) for {}/{}", cells.size(), contract, tradeDate);
                return null;
            }
            String text = cells.get(OPEN_INTEREST_COLUMN).text().trim().replace(",", "");
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                log.warn("Could not parse Open Interest subtotal '{}' for {}/{}", text, contract, tradeDate);
                return null;
            }
        }

        log.warn("No Subtotal row found in TAIFEX daily market report for {}/{}", contract, tradeDate);
        return null;
    }
}
