package com.eagleeye.collector.taifex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TaifexMarketReportParserTest {

    private TaifexMarketReportParser parser;
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 7, 16);

    @BeforeEach
    void setUp() {
        parser = new TaifexMarketReportParser();
    }

    @Test
    void parseTotalOi_extractsSubtotalOpenInterest() {
        String html = buildReport(
                dataRow("MTX", "202607W4", "44"),
                dataRow("MTX", "202607W5", "2"),
                dataRow("MTX", "202608", "30580"),
                subtotalRow("35655")
        );

        Long totalOi = parser.parseTotalOi(html, TEST_DATE, "MTX");

        assertThat(totalOi).isEqualTo(35655L);
    }

    @Test
    void parseTotalOi_stripsThousandsSeparators() {
        String html = buildReport(
                dataRow("TMF", "202608", "59759"),
                subtotalRow("1,234,567")
        );

        Long totalOi = parser.parseTotalOi(html, TEST_DATE, "TMF");

        assertThat(totalOi).isEqualTo(1_234_567L);
    }

    @Test
    void parseTotalOi_returnsNull_whenNoSubtotalRow() {
        String html = buildReport(dataRow("MTX", "202608", "30580"));

        assertThat(parser.parseTotalOi(html, TEST_DATE, "MTX")).isNull();
    }

    @Test
    void parseTotalOi_returnsNull_whenNoTableFound() {
        assertThat(parser.parseTotalOi("<html><body>No Data</body></html>", TEST_DATE, "MTX")).isNull();
    }

    @Test
    void isNoDataPage_true_whenEnglishMarker() {
        assertThat(parser.isNoDataPage("<html><body>No Data</body></html>")).isTrue();
    }

    @Test
    void isNoDataPage_true_whenChineseMarker() {
        assertThat(parser.isNoDataPage("<html><body>查無資料</body></html>")).isTrue();
    }

    @Test
    void isNoDataPage_false_forNormalHtml() {
        assertThat(parser.isNoDataPage("<html><body><table class='table_f'></table></body></html>")).isFalse();
    }

    // -----------------------------------------------------------------------
    // HTML builders — mirror the real report's 17-cell row shape (verified live);
    // only Contract, Contract Month, and Open Interest (index 12) vary per test.
    // -----------------------------------------------------------------------

    private String buildReport(String... rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><table class='table_f table-fixed w-1000'><tbody>");
        for (String row : rows) sb.append(row);
        sb.append("</tbody></table></body></html>");
        return sb.toString();
    }

    private String dataRow(String contract, String month, String openInterest) {
        StringBuilder sb = new StringBuilder("<tr>");
        sb.append("<td>").append(contract).append("</td>");
        sb.append("<td>").append(month).append("</td>");
        for (int i = 0; i < 9; i++) sb.append("<td>0</td>"); // Open/High/Low/Last/Change/%/VolAH/VolReg/VolTotal
        sb.append("<td>0</td>");                              // Settlement Price
        sb.append("<td>").append(openInterest).append("</td>"); // Open Interest (index 12)
        for (int i = 0; i < 4; i++) sb.append("<td>0</td>"); // BestBid/BestAsk/HistHigh/HistLow
        sb.append("</tr>");
        return sb.toString();
    }

    private String subtotalRow(String openInterestSubtotal) {
        StringBuilder sb = new StringBuilder("<tr>");
        for (int i = 0; i < 7; i++) sb.append("<td>&nbsp;</td>");
        sb.append("<td>Subtotal:</td>");
        sb.append("<td>0</td><td>0</td><td>0</td>"); // volume subtotals
        sb.append("<td>&nbsp;</td>");                 // settlement price (blank)
        sb.append("<td>").append(openInterestSubtotal).append("</td>"); // OI subtotal (index 12)
        for (int i = 0; i < 4; i++) sb.append("<td>&nbsp;</td>");
        sb.append("</tr>");
        return sb.toString();
    }
}
