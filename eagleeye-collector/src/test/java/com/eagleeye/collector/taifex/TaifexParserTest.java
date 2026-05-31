package com.eagleeye.collector.taifex;

import com.eagleeye.domain.dto.OptionsCallPutDto;
import com.eagleeye.domain.dto.PositionDto;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TraderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaifexParserTest {

    private TaifexParser parser;
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 3, 9);

    @BeforeEach
    void setUp() {
        parser = new TaifexParser();
    }

    // -----------------------------------------------------------------------
    // Futures table: SN + Contract (both rowspan=3), then 3 trader rows
    // -----------------------------------------------------------------------
    @Test
    void parse_futures_extracts_all_three_trader_rows() {
        String html = buildTable(
                // SN=1, Contract=TX
                traderRow(true, "1", "TX", "Dealers",          "20634", "132293856", "18585", "119274359", "2049", "13019497", "10099", "64445800", "5096", "32521587", "5003", "31924213"),
                traderRow(false, null, null, "Investment Trust", "100",  "1000000",  "200",   "2000000",   "-100", "-1000000",  "50",   "500000",   "150",  "1500000",  "-100", "-1000000"),
                traderRow(false, null, null, "FINI",             "5000", "50000000", "4000",  "40000000",  "1000", "10000000", "2000", "20000000", "1500", "15000000",  "500",  "5000000")
        );

        List<PositionDto> results = parser.parse(html, TEST_DATE);

        assertThat(results).hasSize(3);

        // Dealer row — SN=1 is an extra leading number; last 12 must be the real data
        PositionDto dealer = findBy(results, TraderType.DEALER);
        assertThat(dealer.contract()).isEqualTo("TX");
        assertThat(dealer.tradingLongVolume()).isEqualTo(20634L);
        assertThat(dealer.tradingLongValue()).isEqualTo(132293856L);
        assertThat(dealer.tradingShortVolume()).isEqualTo(18585L);
        assertThat(dealer.tradingNetVolume()).isEqualTo(2049L);
        assertThat(dealer.oiLongVolume()).isEqualTo(10099L);
        assertThat(dealer.oiNetVolume()).isEqualTo(5003L);
        assertThat(dealer.oiNetValue()).isEqualTo(31924213L);

        // Investment Trust
        PositionDto it = findBy(results, TraderType.INVESTMENT_TRUST);
        assertThat(it.tradingLongVolume()).isEqualTo(100L);
        assertThat(it.tradingNetVolume()).isEqualTo(-100L);

        // FINI
        PositionDto fini = findBy(results, TraderType.FINI);
        assertThat(fini.tradingLongVolume()).isEqualTo(5000L);
    }

    // -----------------------------------------------------------------------
    // Options table: same structure — verify contract code detection still works
    // -----------------------------------------------------------------------
    @Test
    void parse_options_extracts_TXO_contract() {
        String html = buildTable(
                traderRow(true, "1", "TXO", "Dealers",          "152900", "2094285", "155062", "2224578", "-2162", "-130293", "68053", "2351252", "78300", "2554283", "-10247", "-203031"),
                traderRow(false, null, null, "Investment Trust",  "105",   "553",     "160",    "2127",    "-55",   "-1574",   "0",    "0",       "390",   "9643",    "-390",  "-9643"),
                traderRow(false, null, null, "FINI",              "110426","1315990", "112867", "1370700", "-2441", "-54710",  "39979","1757564", "49635", "1895620", "-9656", "-138056")
        );

        List<PositionDto> results = parser.parse(html, TEST_DATE);
        assertThat(results).hasSize(3);

        PositionDto dealer = findBy(results, TraderType.DEALER);
        assertThat(dealer.contract()).isEqualTo("TXO");
        assertThat(dealer.tradingLongVolume()).isEqualTo(152900L);
        assertThat(dealer.tradingNetVolume()).isEqualTo(-2162L);
        assertThat(dealer.oiLongVolume()).isEqualTo(68053L);
        assertThat(dealer.oiNetVolume()).isEqualTo(-10247L);
    }

    // -----------------------------------------------------------------------
    // Calls-and-puts table: contract rowspan=6, CALL/PUT rowspan=3, 3 trader rows each
    // -----------------------------------------------------------------------
    @Test
    void parseCallPut_extracts_all_twelve_columns_per_trader_split_by_right() {
        // 12 data columns: trLongVol, trLongVal, trShortVol, trShortVal, trNetVol, trNetVal,
        //                  oiLongVol, oiLongVal, oiShortVol, oiShortVal, oiNetVol, oiNetVal
        String html = buildTable(
                callPutRow(true, "1", "TXO", "CALL", "Dealers",
                        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "-953601"),
                callPutTraderRow("Investment Trust",
                        "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "-82826"),
                callPutTraderRow("FINI",
                        "73728", "760472", "73387", "834188", "341", "-73717",
                        "10691", "1996813", "9137", "1512653", "1554", "569039"),
                callPutRow(false, null, null, "PUT", "Dealers",
                        "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "-57345"),
                callPutTraderRow("Investment Trust",
                        "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "-9643"),
                callPutTraderRow("FINI",
                        "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "101887")
        );

        List<OptionsCallPutDto> results = parser.parseCallPut(html, TEST_DATE);

        assertThat(results).hasSize(6);

        OptionsCallPutDto finiCall = findCallPut(results, TraderType.FINI, RightType.CALL);
        PositionDto p = finiCall.position();
        assertThat(p.contract()).isEqualTo("TXO");
        assertThat(p.tradingLongVolume()).isEqualTo(73728L);
        assertThat(p.oiLongValue()).isEqualTo(1996813L);
        assertThat(p.oiShortValue()).isEqualTo(1512653L);   // 賣方未平倉契約金額
        assertThat(p.oiNetValue()).isEqualTo(569039L);        // 買賣差額契約金額 — headline metric

        OptionsCallPutDto finiPut = findCallPut(results, TraderType.FINI, RightType.PUT);
        assertThat(finiPut.position().oiNetValue()).isEqualTo(101887L);

        OptionsCallPutDto dealerCall = findCallPut(results, TraderType.DEALER, RightType.CALL);
        assertThat(dealerCall.position().oiNetValue()).isEqualTo(-953601L);
    }

    @Test
    void parseCallPut_returns_empty_when_no_table_found() {
        assertThat(parser.parseCallPut("<html><body>No data</body></html>", TEST_DATE)).isEmpty();
    }

    @Test
    void parse_skips_sub_total_and_total_rows() {
        String html = buildTable(
                traderRow(true, "1", "TX", "Dealers",          "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"),
                traderRow(false, null, null, "Investment Trust", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"),
                traderRow(false, null, null, "FINI",             "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"),
                "<tr><td colspan='15'>Sub Total</td></tr>",
                "<tr><td colspan='15'>Grand Total</td></tr>"
        );

        List<PositionDto> results = parser.parse(html, TEST_DATE);
        assertThat(results).hasSize(3);
    }

    @Test
    void parse_handles_multiple_contracts() {
        String html = buildTable(
                traderRow(true, "1", "TX",  "Dealers",          "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"),
                traderRow(false, null, null, "Investment Trust", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"),
                traderRow(false, null, null, "FINI",             "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"),
                traderRow(true, "2", "MTX", "Dealers",          "9", "8", "7", "6", "5", "4", "3", "2", "1", "0", "-1", "-2"),
                traderRow(false, null, null, "Investment Trust", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"),
                traderRow(false, null, null, "FINI",             "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
        );

        List<PositionDto> results = parser.parse(html, TEST_DATE);
        assertThat(results).hasSize(6);

        List<String> contracts = results.stream().map(PositionDto::contract).distinct().toList();
        assertThat(contracts).containsExactlyInAnyOrder("TX", "MTX");
    }

    @Test
    void parse_returns_empty_when_no_table_found() {
        List<PositionDto> results = parser.parse("<html><body>No data</body></html>", TEST_DATE);
        assertThat(results).isEmpty();
    }

    // -----------------------------------------------------------------------
    // After-hours table: 6-column (trading only, no OI)
    // -----------------------------------------------------------------------

    @Test
    void parseAh_extracts_six_column_trading_data() {
        String html = buildTable(
                traderRowAh(true,  "1", "TX", "Dealers",          "500", "3200000", "300", "1920000", "200", "1280000"),
                traderRowAh(false, null, null, "Investment Trust",  "10",   "64000",  "20",  "128000", "-10",  "-64000"),
                traderRowAh(false, null, null, "FINI",             "200", "1280000", "150",  "960000",  "50",  "320000")
        );

        List<PositionDto> results = parser.parseAh(html, TEST_DATE);

        assertThat(results).hasSize(3);

        PositionDto dealer = findBy(results, TraderType.DEALER);
        assertThat(dealer.contract()).isEqualTo("TX");
        assertThat(dealer.tradingLongVolume()).isEqualTo(500L);
        assertThat(dealer.tradingLongValue()).isEqualTo(3200000L);
        assertThat(dealer.tradingShortVolume()).isEqualTo(300L);
        assertThat(dealer.tradingShortValue()).isEqualTo(1920000L);
        assertThat(dealer.tradingNetVolume()).isEqualTo(200L);
        assertThat(dealer.tradingNetValue()).isEqualTo(1280000L);
        // OI is absent for after-hours — must be zero
        assertThat(dealer.oiLongVolume()).isZero();
        assertThat(dealer.oiLongValue()).isZero();
        assertThat(dealer.oiShortVolume()).isZero();
        assertThat(dealer.oiShortValue()).isZero();
        assertThat(dealer.oiNetVolume()).isZero();
        assertThat(dealer.oiNetValue()).isZero();

        PositionDto it = findBy(results, TraderType.INVESTMENT_TRUST);
        assertThat(it.tradingNetVolume()).isEqualTo(-10L);

        PositionDto fini = findBy(results, TraderType.FINI);
        assertThat(fini.tradingLongVolume()).isEqualTo(200L);
    }

    @Test
    void parseAh_handles_negative_parenthesised_values() {
        String html = buildTable(
                traderRowAh(true, "1", "TX", "Dealers",          "100", "640000", "200", "1280000", "(100)", "(640000)"),
                traderRowAh(false, null, null, "Investment Trust", "10",  "64000",  "20",  "128000",  "(10)",  "(64000)"),
                traderRowAh(false, null, null, "FINI",             "50",  "320000", "80",  "512000",  "(30)",  "(192000)")
        );

        List<PositionDto> results = parser.parseAh(html, TEST_DATE);

        PositionDto dealer = findBy(results, TraderType.DEALER);
        assertThat(dealer.tradingNetVolume()).isEqualTo(-100L);
        assertThat(dealer.tradingNetValue()).isEqualTo(-640000L);
    }

    @Test
    void parseAh_returns_empty_when_no_data() {
        List<PositionDto> results = parser.parseAh("<html><body>No Data</body></html>", TEST_DATE);
        assertThat(results).isEmpty();
    }

    // -----------------------------------------------------------------------
    // isNoDataPage
    // -----------------------------------------------------------------------

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

    @Test
    void isNoDataPage_false_forNull() {
        assertThat(parser.isNoDataPage(null)).isFalse();
    }

    // -----------------------------------------------------------------------
    // HTML builders
    // -----------------------------------------------------------------------

    private String buildTable(String... rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><table class='table_f'><tbody>");
        for (String row : rows) sb.append(row);
        sb.append("</tbody></table></body></html>");
        return sb.toString();
    }

    /**
     * Builds a trader row.
     * @param isFirstInGroup true for the Dealer row — adds SN and Contract cells (rowspan=3)
     */
    private String traderRow(boolean isFirstInGroup, String sn, String contract, String trader,
                              String tlv, String tlval,
                              String tsv, String tsval,
                              String tnv, String tnval,
                              String olv, String olval,
                              String osv, String osval,
                              String onv, String onval) {
        StringBuilder sb = new StringBuilder("<tr>");
        if (isFirstInGroup) {
            sb.append("<td rowspan='3'>").append(sn).append("</td>");
            sb.append("<td rowspan='3'>").append(contract).append("</td>");
        }
        sb.append("<td>").append(trader).append("</td>");
        for (String val : List.of(tlv, tlval, tsv, tsval, tnv, tnval, olv, olval, osv, osval, onv, onval)) {
            sb.append("<td>").append(val).append("</td>");
        }
        sb.append("</tr>");
        return sb.toString();
    }

    /**
     * Builds an after-hours trader row with 6 data columns (trading only, no OI).
     */
    private String traderRowAh(boolean isFirstInGroup, String sn, String contract, String trader,
                                String tlv, String tlval,
                                String tsv, String tsval,
                                String tnv, String tnval) {
        StringBuilder sb = new StringBuilder("<tr>");
        if (isFirstInGroup) {
            sb.append("<td rowspan='3'>").append(sn).append("</td>");
            sb.append("<td rowspan='3'>").append(contract).append("</td>");
        }
        sb.append("<td>").append(trader).append("</td>");
        for (String val : List.of(tlv, tlval, tsv, tsval, tnv, tnval)) {
            sb.append("<td>").append(val).append("</td>");
        }
        sb.append("</tr>");
        return sb.toString();
    }

    /**
     * Builds a call/put trader row carrying 12 data columns. The first row of a contract
     * adds SN + Contract (rowspan=6); a row that begins a CALL/PUT section adds the
     * right-type cell (rowspan=3).
     */
    private String callPutRow(boolean isFirstInContract, String sn, String contract,
                              String rightLabel, String trader, String... vals) {
        StringBuilder sb = new StringBuilder("<tr>");
        if (isFirstInContract) {
            sb.append("<td rowspan='6'>").append(sn).append("</td>");
            sb.append("<td rowspan='6'>").append(contract).append("</td>");
        }
        sb.append("<td rowspan='3'>").append(rightLabel).append("</td>");
        appendCallPutCells(sb, trader, vals);
        return sb.append("</tr>").toString();
    }

    private String callPutTraderRow(String trader, String... vals) {
        StringBuilder sb = new StringBuilder("<tr>");
        appendCallPutCells(sb, trader, vals);
        return sb.append("</tr>").toString();
    }

    private void appendCallPutCells(StringBuilder sb, String trader, String... vals) {
        sb.append("<td>").append(trader).append("</td>");
        for (String v : vals) sb.append("<td>").append(v).append("</td>");
    }

    private OptionsCallPutDto findCallPut(List<OptionsCallPutDto> list, TraderType type, RightType right) {
        return list.stream()
                .filter(p -> p.position().traderType() == type && p.rightType() == right)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No call/put position for " + type + "/" + right));
    }

    private PositionDto findBy(List<PositionDto> positions, TraderType type) {
        return positions.stream()
                .filter(p -> p.traderType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No position for " + type));
    }
}
