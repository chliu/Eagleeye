package com.eagleeye.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionalFlowTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 19);

    @Test
    void constructor_setsTradeDate() {
        InstitutionalFlow flow = new InstitutionalFlow(DATE);
        assertThat(flow.getTradeDate()).isEqualTo(DATE);
    }

    @Test
    void setters_storeAllNineFields() {
        InstitutionalFlow flow = new InstitutionalFlow(DATE);
        flow.setForeignBuy(100_000_000_000L);
        flow.setForeignSell(80_000_000_000L);
        flow.setForeignNet(20_000_000_000L);
        flow.setInvestmentTrustBuy(5_000_000_000L);
        flow.setInvestmentTrustSell(4_000_000_000L);
        flow.setInvestmentTrustNet(1_000_000_000L);
        flow.setDealerBuy(3_000_000_000L);
        flow.setDealerSell(2_500_000_000L);
        flow.setDealerNet(500_000_000L);

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
}
