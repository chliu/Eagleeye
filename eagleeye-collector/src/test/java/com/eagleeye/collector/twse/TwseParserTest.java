package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.entity.MarginDailyBar;
import com.eagleeye.domain.entity.TaiexDailyBar;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwseParserTest {

    private TwseParser parser;

    private static final String VALID_JSON = """
            {
              "stat": "OK",
              "date": "20260301",
              "fields": ["Date","Open","High","Low","Close","Volume","Turnover"],
              "data": [
                ["115/03/03", "20,234.56", "20,456.78", "20,100.23", "20,300.45", "3,456,789", "123,456,789,012"],
                ["115/03/04", "20,300.45", "20,512.34", "20,201.11", "20,488.22", "3,567,890", "124,567,890,123"]
              ]
            }
            """;

    private static final String EMPTY_DATA_JSON = """
            {
              "stat": "OK",
              "date": "20260201",
              "fields": ["Date","Open","High","Low","Close","Volume","Turnover"],
              "data": []
            }
            """;

    private static final String NO_OK_STAT_JSON = """
            {
              "stat": "NO DATA",
              "date": "20260101",
              "data": []
            }
            """;

    @BeforeEach
    void setUp() {
        parser = new TwseParser(new ObjectMapper());
    }

    @Test
    void parse_validJson_returnsTwoBars() {
        List<TaiexDailyBar> bars = parser.parse(VALID_JSON);
        assertThat(bars).hasSize(2);
    }

    @Test
    void parse_validJson_firstBarHasCorrectDate() {
        List<TaiexDailyBar> bars = parser.parse(VALID_JSON);
        assertThat(bars.get(0).getTradeDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 3));
    }

    @Test
    void parse_validJson_firstBarHasCorrectOhlcAsFixedPoint() {
        List<TaiexDailyBar> bars = parser.parse(VALID_JSON);
        TaiexDailyBar bar = bars.get(0);
        assertThat(bar.getOpen()).isEqualTo(2023456L);
        assertThat(bar.getHigh()).isEqualTo(2045678L);
        assertThat(bar.getLow()).isEqualTo(2010023L);
        assertThat(bar.getClose()).isEqualTo(2030045L);
    }

    @Test
    void parse_validJson_volumeAndTurnoverAreNull() {
        // The TWSE /indicesReport/MI_5MINS_HIST endpoint does not provide volume/turnover
        List<TaiexDailyBar> bars = parser.parse(VALID_JSON);
        TaiexDailyBar bar = bars.get(0);
        assertThat(bar.getVolume()).isNull();
        assertThat(bar.getTurnover()).isNull();
    }

    @Test
    void parse_emptyDataArray_returnsEmptyList() {
        List<TaiexDailyBar> bars = parser.parse(EMPTY_DATA_JSON);
        assertThat(bars).isEmpty();
    }

    @Test
    void parse_nonOkStat_returnsEmptyList() {
        List<TaiexDailyBar> bars = parser.parse(NO_OK_STAT_JSON);
        assertThat(bars).isEmpty();
    }

    private static final String MARKET_STATS_JSON = """
            {
              "stat": "OK",
              "fields": ["日期","成交股數","成交金額","成交筆數","發行量加權股價指數","漲跌點數"],
              "data": [
                ["115/03/03", "3,456,789", "123,456,789,012", "1,234,567", "20,300.45", "65.89"],
                ["115/03/04", "3,567,890", "124,567,890,123", "1,345,678", "20,488.22", "187.77"]
              ]
            }
            """;

    @Test
    void parseVolumeByDate_validJson_returnsVolumeAndTurnoverByDate() {
        Map<LocalDate, long[]> result = parser.parseVolumeByDate(MARKET_STATS_JSON);

        assertThat(result).hasSize(2);
        long[] mar3 = result.get(LocalDate.of(2026, 3, 3));
        assertThat(mar3[0]).isEqualTo(3456789L);
        assertThat(mar3[1]).isEqualTo(123456789012L);
    }

    @Test
    void parseVolumeByDate_nonOkStat_returnsEmptyMap() {
        Map<LocalDate, long[]> result = parser.parseVolumeByDate("{\"stat\":\"NO DATA\",\"data\":[]}");
        assertThat(result).isEmpty();
    }

    @Test
    void parse_malformedJson_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> parser.parse("not valid json {{{"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid json");
    }

    // ── parseMargin ─────────────────────────────────────────────────────────────

    private static final String MARGIN_JSON = """
            {
              "stat": "OK",
              "date": "20260313",
              "tables": [
                {
                  "title": "2026/03/13 Margin transaction summary",
                  "fields": ["Item","Margin Purchase/ Short Covering","Margin Sale/Short Sale","Cash Redemption/ Stock Redemption","Balance of Previous Day","Balance of the Day"],
                  "data": [
                    ["Margin Purchase (Trading unit)", "526,296", "485,038", "6,678", "8,074,444", "8,109,024"],
                    ["Short Sale (Trading unit)", "31,407", "23,277", "1,999", "215,077", "204,948"]
                  ]
                }
              ]
            }
            """;

    @Test
    void parseMargin_validJson_returnsBarWithAllFields() {
        LocalDate date = LocalDate.of(2026, 3, 18);
        MarginDailyBar bar = parser.parseMargin(MARGIN_JSON, date);

        assertThat(bar).isNotNull();
        assertThat(bar.getTradeDate()).isEqualTo(date);
        assertThat(bar.getMarginPurchase()).isEqualTo(526_296L);
        assertThat(bar.getMarginSale()).isEqualTo(485_038L);
        assertThat(bar.getMarginCashRedemption()).isEqualTo(6_678L);
        assertThat(bar.getMarginPrevBalance()).isEqualTo(8_074_444L);
        assertThat(bar.getMarginBalance()).isEqualTo(8_109_024L);
        assertThat(bar.getShortCovering()).isEqualTo(31_407L);
        assertThat(bar.getShortSale()).isEqualTo(23_277L);
        assertThat(bar.getShortStockRedemption()).isEqualTo(1_999L);
        assertThat(bar.getShortPrevBalance()).isEqualTo(215_077L);
        assertThat(bar.getShortBalance()).isEqualTo(204_948L);
    }

    @Test
    void parseMargin_statNotOk_returnsNull() {
        assertThat(parser.parseMargin("{\"stat\":\"Search date greater than today, please retry!\"}", LocalDate.of(2026, 3, 18))).isNull();
    }

    @Test
    void parseMargin_missingRows_returnsNull() {
        String json = "{\"stat\":\"OK\",\"tables\":[{\"data\":[[\"only one row\",\"1\",\"2\",\"3\",\"4\",\"5\"]]}]}";
        assertThat(parser.parseMargin(json, LocalDate.of(2026, 3, 18))).isNull();
    }

    @Test
    void parseMargin_invalidJson_returnsNull() {
        assertThat(parser.parseMargin("not-json", LocalDate.of(2026, 3, 18))).isNull();
    }

    // ── parseInstitutionalFlow ────────────────────────────────────────────────

    private static final String FLOW_JSON = """
            {
              "stat": "OK",
              "tables": [
                {
                  "data": [
                    ["Foreign Investors", "100,000,000,000", "80,000,000,000", "20,000,000,000"],
                    ["Investment Trust",   "5,000,000,000",  "4,000,000,000",  "1,000,000,000"],
                    ["Dealers",            "3,000,000,000",  "2,500,000,000",    "500,000,000"]
                  ]
                }
              ]
            }
            """;

    private static final LocalDate FLOW_DATE = LocalDate.of(2026, 3, 19);

    @Test
    void parseInstitutionalFlow_success_returnsAllNineFields() {
        InstitutionalFlow flow = parser.parseInstitutionalFlow(FLOW_JSON, FLOW_DATE);

        assertThat(flow).isNotNull();
        assertThat(flow.getTradeDate()).isEqualTo(FLOW_DATE);
        assertThat(flow.getForeignBuy()).isEqualTo(100_000_000_000L);
        assertThat(flow.getForeignSell()).isEqualTo(80_000_000_000L);
        assertThat(flow.getForeignNet()).isEqualTo(20_000_000_000L);
        assertThat(flow.getInvestmentTrustBuy()).isEqualTo(5_000_000_000L);
        assertThat(flow.getInvestmentTrustSell()).isEqualTo(4_000_000_000L);
        assertThat(flow.getInvestmentTrustNet()).isEqualTo(1_000_000_000L);
        assertThat(flow.getDealerBuy()).isEqualTo(3_000_000_000L);
        assertThat(flow.getDealerSell()).isEqualTo(2_500_000_000L);
        assertThat(flow.getDealerNet()).isEqualTo(500_000_000L);
    }

    @Test
    void parseInstitutionalFlow_statNotOk_returnsNull() {
        String json = """
                {"stat": "NO DATA", "tables": [{"data": []}]}
                """;
        assertThat(parser.parseInstitutionalFlow(json, FLOW_DATE)).isNull();
    }

    @Test
    void parseInstitutionalFlow_emptyData_returnsNull() {
        String json = """
                {"stat": "OK", "tables": [{"data": []}]}
                """;
        assertThat(parser.parseInstitutionalFlow(json, FLOW_DATE)).isNull();
    }

    @Test
    void parseInstitutionalFlow_invalidJson_returnsNull() {
        assertThat(parser.parseInstitutionalFlow("not-json", FLOW_DATE)).isNull();
    }

    @Test
    void parse_oneValidOneInvalidRow_returnsOnlyValidBar() {
        String mixedJson = """
                {
                  "stat": "OK",
                  "data": [
                    ["115/03/03", "20,234.56", "20,456.78", "20,100.23", "20,300.45", "3,456,789", "123,456,789,012"],
                    ["bad-date", "20,234.56", "20,456.78", "20,100.23", "20,300.45", "3,456,789", "123,456,789,012"]
                  ]
                }
                """;
        List<TaiexDailyBar> bars = parser.parse(mixedJson);
        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).getTradeDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 3));
    }
}
