package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.MarginTransaction;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MarginTransactionParserTest {

    private MarginTransactionParser parser;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 18);

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

    @BeforeEach
    void setUp() {
        parser = new MarginTransactionParser(new ObjectMapper());
    }

    @Test
    void parse_validJson_returnsAllFields() {
        MarginTransaction result = parser.parse(MARGIN_JSON, DATE);

        assertThat(result).isNotNull();
        assertThat(result.getTradeDate()).isEqualTo(DATE);
        assertThat(result.getMarginPurchase()).isEqualTo(526_296L);
        assertThat(result.getMarginSale()).isEqualTo(485_038L);
        assertThat(result.getMarginCashRedemption()).isEqualTo(6_678L);
        assertThat(result.getMarginPrevBalance()).isEqualTo(8_074_444L);
        assertThat(result.getMarginBalance()).isEqualTo(8_109_024L);
        assertThat(result.getShortCovering()).isEqualTo(31_407L);
        assertThat(result.getShortSale()).isEqualTo(23_277L);
        assertThat(result.getShortStockRedemption()).isEqualTo(1_999L);
        assertThat(result.getShortPrevBalance()).isEqualTo(215_077L);
        assertThat(result.getShortBalance()).isEqualTo(204_948L);
    }

    @Test
    void parse_statNotOk_returnsNull() {
        assertThat(parser.parse("{\"stat\":\"Search date greater than today, please retry!\"}", DATE)).isNull();
    }

    @Test
    void parse_missingRows_returnsNull() {
        String json = "{\"stat\":\"OK\",\"tables\":[{\"data\":[[\"only one row\",\"1\",\"2\",\"3\",\"4\",\"5\"]]}]}";
        assertThat(parser.parse(json, DATE)).isNull();
    }

    @Test
    void parse_invalidJson_returnsNull() {
        assertThat(parser.parse("not-json", DATE)).isNull();
    }
}
