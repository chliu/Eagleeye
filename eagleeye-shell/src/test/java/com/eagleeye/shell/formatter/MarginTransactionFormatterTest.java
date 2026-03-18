package com.eagleeye.shell.formatter;

import com.eagleeye.domain.entity.MarginDailyBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarginTransactionFormatterTest {

    private TableFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new TableFormatter();
    }

    private MarginDailyBar bar(String date) {
        MarginDailyBar b = new MarginDailyBar(LocalDate.parse(date));
        b.setMarginPurchase(526_296L);
        b.setMarginSale(485_038L);
        b.setMarginCashRedemption(6_678L);
        b.setMarginPrevBalance(8_074_444L);
        b.setMarginBalance(8_109_024L);
        b.setShortCovering(31_407L);
        b.setShortSale(23_277L);
        b.setShortStockRedemption(1_999L);
        b.setShortPrevBalance(215_077L);
        b.setShortBalance(204_948L);
        return b;
    }

    @Test
    void formatMarginTransaction_emptyList_returnsNoData() {
        assertThat(formatter.formatMarginTransaction(List.of())).isEqualTo("No data found.");
    }

    @Test
    void formatMarginTransaction_containsExpectedHeaders() {
        String result = formatter.formatMarginTransaction(List.of(bar("2026-03-18")));
        assertThat(result).contains("Date");
        assertThat(result).contains("M-Buy");
        assertThat(result).contains("M-Sell");
        assertThat(result).contains("M-Bal");
        assertThat(result).contains("S-Cover");
        assertThat(result).contains("S-Sell");
        assertThat(result).contains("S-Bal");
    }

    @Test
    void formatMarginTransaction_singleBar_containsDate() {
        assertThat(formatter.formatMarginTransaction(List.of(bar("2026-03-18"))))
                .contains("2026-03-18");
    }

    @Test
    void formatMarginTransaction_singleBar_containsFormattedNumbers() {
        String result = formatter.formatMarginTransaction(List.of(bar("2026-03-18")));
        assertThat(result).contains("526,296");   // marginPurchase
        assertThat(result).contains("8,109,024"); // marginBalance
        assertThat(result).contains("204,948");   // shortBalance
    }

    @Test
    void formatMarginTransaction_nullFields_renderedAsDash() {
        MarginDailyBar b = new MarginDailyBar(LocalDate.parse("2026-03-18"));
        // leave all fields null
        String result = formatter.formatMarginTransaction(List.of(b));
        assertThat(result).contains("-");
    }

    @Test
    void formatMarginTransaction_multipleBars_allDatesPresent() {
        List<MarginDailyBar> bars = List.of(bar("2026-03-17"), bar("2026-03-18"));
        String result = formatter.formatMarginTransaction(bars);
        assertThat(result).contains("2026-03-17");
        assertThat(result).contains("2026-03-18");
    }
}
