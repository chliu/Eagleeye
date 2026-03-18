package com.eagleeye.collector.taifex;

import com.eagleeye.domain.dto.PositionDto;
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

    private PositionDto findBy(List<PositionDto> positions, TraderType type) {
        return positions.stream()
                .filter(p -> p.traderType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No position for " + type));
    }
}
