package com.eagleeye.shell.formatter;

import com.eagleeye.domain.entity.InstitutionalFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionalFlowFormatterTest {

    private TableFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new TableFormatter();
    }

    private InstitutionalFlow flow(String date) {
        InstitutionalFlow f = new InstitutionalFlow(LocalDate.parse(date));
        f.setForeignBuy(100_000_000_000L);
        f.setForeignSell(80_000_000_000L);
        f.setForeignNet(20_000_000_000L);
        f.setInvestmentTrustBuy(5_000_000_000L);
        f.setInvestmentTrustSell(4_000_000_000L);
        f.setInvestmentTrustNet(1_000_000_000L);
        f.setDealerBuy(3_000_000_000L);
        f.setDealerSell(2_500_000_000L);
        f.setDealerNet(500_000_000L);
        return f;
    }

    @Test
    void formatInstitutionalFlow_emptyList_returnsNoData() {
        assertThat(formatter.formatInstitutionalFlow(List.of())).isEqualTo("No data found.");
    }

    @Test
    void formatInstitutionalFlow_containsExpectedHeaders() {
        String result = formatter.formatInstitutionalFlow(List.of(flow("2026-03-19")));
        assertThat(result).contains("Date");
        assertThat(result).contains("F-Buy");
        assertThat(result).contains("F-Sell");
        assertThat(result).contains("F-Net");
        assertThat(result).contains("IT-Buy");
        assertThat(result).contains("IT-Sell");
        assertThat(result).contains("IT-Net");
        assertThat(result).contains("D-Buy");
        assertThat(result).contains("D-Sell");
        assertThat(result).contains("D-Net");
    }

    @Test
    void formatInstitutionalFlow_singleFlow_containsDate() {
        assertThat(formatter.formatInstitutionalFlow(List.of(flow("2026-03-19"))))
                .contains("2026-03-19");
    }

    @Test
    void formatInstitutionalFlow_singleFlow_containsFormattedNumbers() {
        String result = formatter.formatInstitutionalFlow(List.of(flow("2026-03-19")));
        assertThat(result).contains("100,000,000,000");  // foreignBuy
        assertThat(result).contains("+20,000,000,000");  // foreignNet (positive → + prefix)
        assertThat(result).contains("500,000,000");      // dealerNet
    }

    @Test
    void formatInstitutionalFlow_nullFields_renderedAsDash() {
        InstitutionalFlow f = new InstitutionalFlow(LocalDate.parse("2026-03-19"));
        // leave all fields null
        String result = formatter.formatInstitutionalFlow(List.of(f));
        assertThat(result).contains("-");
    }

    @Test
    void formatInstitutionalFlow_negativeNet_noLeadingPlus() {
        InstitutionalFlow f = new InstitutionalFlow(LocalDate.parse("2026-03-19"));
        f.setForeignBuy(80_000_000_000L);
        f.setForeignSell(100_000_000_000L);
        f.setForeignNet(-20_000_000_000L);
        String result = formatter.formatInstitutionalFlow(List.of(f));
        assertThat(result).contains("-20,000,000,000");
        assertThat(result).doesNotContain("+-");
    }

    @Test
    void formatInstitutionalFlow_multipleFlows_allDatesPresent() {
        List<InstitutionalFlow> flows = List.of(flow("2026-03-18"), flow("2026-03-19"));
        String result = formatter.formatInstitutionalFlow(flows);
        assertThat(result).contains("2026-03-18");
        assertThat(result).contains("2026-03-19");
    }
}
