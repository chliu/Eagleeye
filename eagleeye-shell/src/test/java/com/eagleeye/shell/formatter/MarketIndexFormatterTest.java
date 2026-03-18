package com.eagleeye.shell.formatter;

import com.eagleeye.domain.entity.TaiexDailyBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketIndexFormatterTest {

    private TableFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new TableFormatter();
    }

    private TaiexDailyBar bar(String date, long openX100, long highX100, long lowX100, long closeX100,
                               Long volume, Long turnover) {
        TaiexDailyBar b = new TaiexDailyBar(LocalDate.parse(date));
        b.setOpen(openX100);
        b.setHigh(highX100);
        b.setLow(lowX100);
        b.setClose(closeX100);
        b.setVolume(volume);
        b.setTurnover(turnover);
        return b;
    }

    @Test
    void formatMarketIndex_emptyList_returnsNoData() {
        assertThat(formatter.formatMarketIndex(List.of())).isEqualTo("No data found.");
    }

    @Test
    void formatMarketIndex_singleBar_containsDate() {
        TaiexDailyBar b = bar("2026-03-03", 2923456L, 2945678L, 2910023L, 2934981L,
                10724729528L, 669781989470L);
        String result = formatter.formatMarketIndex(List.of(b));
        assertThat(result).contains("2026-03-03");
    }

    @Test
    void formatMarketIndex_singleBar_ohlcRenderedAsDecimal() {
        // open = 2923456 → 29,234.56, close = 2934981 → 29,349.81
        TaiexDailyBar b = bar("2026-03-03", 2923456L, 2945678L, 2910023L, 2934981L,
                10724729528L, 669781989470L);
        String result = formatter.formatMarketIndex(List.of(b));
        assertThat(result).contains("29,234.56");
        assertThat(result).contains("29,349.81");
    }

    @Test
    void formatMarketIndex_singleBar_volumeAndTurnoverRendered() {
        TaiexDailyBar b = bar("2026-03-03", 2923456L, 2945678L, 2910023L, 2934981L,
                10724729528L, 669781989470L);
        String result = formatter.formatMarketIndex(List.of(b));
        assertThat(result).contains("10,724,729,528");
        assertThat(result).contains("669,781,989,470");
    }

    @Test
    void formatMarketIndex_nullVolumeAndTurnover_renderedAsDash() {
        TaiexDailyBar b = bar("2026-03-03", 2923456L, 2945678L, 2910023L, 2934981L, null, null);
        String result = formatter.formatMarketIndex(List.of(b));
        assertThat(result).contains("-");
    }

    @Test
    void formatMarketIndex_multipleBars_allDatesPresent() {
        List<TaiexDailyBar> bars = List.of(
                bar("2026-03-03", 2923456L, 2945678L, 2910023L, 2934981L, 1000L, 2000L),
                bar("2026-03-04", 2934981L, 2951234L, 2920100L, 2948822L, 1100L, 2100L)
        );
        String result = formatter.formatMarketIndex(bars);
        assertThat(result).contains("2026-03-03");
        assertThat(result).contains("2026-03-04");
    }

    @Test
    void formatMarketIndex_containsExpectedHeaders() {
        TaiexDailyBar b = bar("2026-03-03", 2923456L, 2945678L, 2910023L, 2934981L, 1000L, 2000L);
        String result = formatter.formatMarketIndex(List.of(b));
        assertThat(result).contains("Date");
        assertThat(result).contains("Open");
        assertThat(result).contains("High");
        assertThat(result).contains("Low");
        assertThat(result).contains("Close");
        assertThat(result).contains("Volume");
        assertThat(result).contains("Turnover");
    }
}
