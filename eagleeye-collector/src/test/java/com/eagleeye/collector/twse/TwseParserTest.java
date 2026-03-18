package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.TaiexDailyBar;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void parse_validJson_firstBarHasCorrectVolumeAndTurnover() {
        List<TaiexDailyBar> bars = parser.parse(VALID_JSON);
        TaiexDailyBar bar = bars.get(0);
        assertThat(bar.getVolume()).isEqualTo(3456789L);
        assertThat(bar.getTurnover()).isEqualTo(123456789012L);
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

    @Test
    void parse_malformedJson_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> parser.parse("not valid json {{{"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid json");
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
