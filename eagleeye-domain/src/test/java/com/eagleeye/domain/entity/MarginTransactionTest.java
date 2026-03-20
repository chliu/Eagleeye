package com.eagleeye.domain.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class MarginTransactionTest {

    @Test
    void constructor_setsTradeDate() {
        LocalDate date = LocalDate.of(2026, 3, 18);
        MarginTransaction bar = new MarginTransaction(date);
        assertThat(bar.getTradeDate()).isEqualTo(date);
    }

    @Test
    void setters_storeAllFields() {
        MarginTransaction bar = new MarginTransaction(LocalDate.of(2026, 3, 18));
        bar.setMarginPurchase(526_296L);
        bar.setMarginSale(485_038L);
        bar.setMarginCashRedemption(6_678L);
        bar.setMarginPrevBalance(8_074_444L);
        bar.setMarginBalance(8_109_024L);
        bar.setShortCovering(31_407L);
        bar.setShortSale(23_277L);
        bar.setShortStockRedemption(1_999L);
        bar.setShortPrevBalance(215_077L);
        bar.setShortBalance(204_948L);

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
}
