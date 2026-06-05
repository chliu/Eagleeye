package com.eagleeye.collector.taifex;

import com.eagleeye.domain.entity.TxTick;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TxTickParserTest {

    private final TxTickParser parser = new TxTickParser();
    private final LocalDate DATE = LocalDate.of(2026, 6, 5);

    // CSV columns (no header): date,product,contract_month,time,price,volume,near_price,far_price,auction_flag
    private String row(String product, String contractMonth, String time,
                       String price, String volume, String auctionFlag) {
        return "20260605," + product + "," + contractMonth + "," +
               time + "," + price + "," + volume + ",0,0," + auctionFlag;
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertThat(parser.parse(List.of(), DATE)).isEmpty();
    }

    @Test
    void nonTxProduct_filtered() {
        assertThat(parser.parse(List.of(row("MXF", "202606", "090000", "21000", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void contractMonthWithSlash_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606/202609", "090000", "21500", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void priceBelowThreshold_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606", "090000", "29999", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void priceAtExactThreshold_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606", "090000", "30000", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void beforeMarketOpen_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606", "084459", "31500", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void afterMarketClose_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606", "134501", "31500", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void marketBoundaries_inclusive() {
        List<String> lines = List.of(
            row("TX", "202606", "084500", "31500", "3", ""),
            row("TX", "202606", "134500", "31600", "2", "")
        );
        assertThat(parser.parse(lines, DATE)).hasSize(2);
    }

    @Test
    void validRow_parsedCorrectly() {
        List<TxTick> ticks = parser.parse(List.of(row("TX", "202606", "090000", "31500", "5", "")), DATE);
        assertThat(ticks).hasSize(1);
        TxTick t = ticks.get(0);
        assertThat(t.getTradeDate()).isEqualTo(DATE);
        assertThat(t.getTime()).isEqualTo("090000");
        assertThat(t.getPrice()).isEqualTo(31500);
        assertThat(t.getVolume()).isEqualTo(5);
        assertThat(t.getContractMonth()).isEqualTo("202606");
        assertThat(t.isAuction()).isFalse();
    }

    @Test
    void auctionFlag_setsIsAuctionTrue() {
        List<TxTick> ticks = parser.parse(List.of(row("TX", "202606", "084500", "31500", "5", "*")), DATE);
        assertThat(ticks).hasSize(1);
        assertThat(ticks.get(0).isAuction()).isTrue();
    }

    @Test
    void timePadding_shortTime_paddedToSixDigits() {
        List<TxTick> ticks = parser.parse(List.of(row("TX", "202606", "84500", "31500", "5", "")), DATE);
        assertThat(ticks).hasSize(1);
        assertThat(ticks.get(0).getTime()).isEqualTo("084500");
    }

    @Test
    void dominantContract_onlyHighestVolumeKept() {
        // 202606: total volume 10; 202609: total volume 5 → keep 202606 only
        List<String> lines = List.of(
            row("TX", "202606", "090000", "31500", "10", ""),
            row("TX", "202609", "090100", "31500", "5", "")
        );
        List<TxTick> ticks = parser.parse(lines, DATE);
        assertThat(ticks).hasSize(1);
        assertThat(ticks.get(0).getContractMonth()).isEqualTo("202606");
    }

    @Test
    void malformedPrice_rowDropped() {
        List<String> lines = List.of(row("TX", "202606", "090000", "N/A", "5", ""));
        assertThat(parser.parse(lines, DATE)).isEmpty();
    }

    @Test
    void tooFewColumns_rowDropped() {
        assertThat(parser.parse(List.of("TX,202606,090000"), DATE)).isEmpty();
    }
}
