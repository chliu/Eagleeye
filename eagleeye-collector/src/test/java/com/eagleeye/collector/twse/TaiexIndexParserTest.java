package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.TaiexIndex;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaiexIndexParserTest {

    private TaiexIndexParser parser;

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
        parser = new TaiexIndexParser(new ObjectMapper());
    }

    @Test
    void parse_validJson_returnsTwoBars() {
        List<TaiexIndex> bars = parser.parse(VALID_JSON);
        assertThat(bars).hasSize(2);
    }

    @Test
    void parse_validJson_firstBarHasCorrectDate() {
        List<TaiexIndex> bars = parser.parse(VALID_JSON);
        assertThat(bars.get(0).getTradeDate()).isEqualTo(LocalDate.of(2026, 3, 3));
    }

    @Test
    void parse_validJson_firstBarHasCorrectOhlcAsFixedPoint() {
        List<TaiexIndex> bars = parser.parse(VALID_JSON);
        TaiexIndex bar = bars.get(0);
        assertThat(bar.getOpen()).isEqualTo(2023456L);
        assertThat(bar.getHigh()).isEqualTo(2045678L);
        assertThat(bar.getLow()).isEqualTo(2010023L);
        assertThat(bar.getClose()).isEqualTo(2030045L);
    }

    @Test
    void parse_validJson_volumeAndTurnoverAreNull() {
        // MI_5MINS_HIST does not provide volume/turnover
        List<TaiexIndex> bars = parser.parse(VALID_JSON);
        TaiexIndex bar = bars.get(0);
        assertThat(bar.getVolume()).isNull();
        assertThat(bar.getTurnover()).isNull();
    }

    @Test
    void parse_emptyDataArray_returnsEmptyList() {
        assertThat(parser.parse(EMPTY_DATA_JSON)).isEmpty();
    }

    @Test
    void parse_nonOkStat_returnsEmptyList() {
        assertThat(parser.parse(NO_OK_STAT_JSON)).isEmpty();
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
                    ["115/03/03", "20,234.56", "20,456.78", "20,100.23", "20,300.45"],
                    ["bad-date", "20,234.56", "20,456.78", "20,100.23", "20,300.45"]
                  ]
                }
                """;
        List<TaiexIndex> bars = parser.parse(mixedJson);
        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).getTradeDate()).isEqualTo(LocalDate.of(2026, 3, 3));
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
}
