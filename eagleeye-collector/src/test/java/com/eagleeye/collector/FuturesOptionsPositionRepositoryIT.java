package com.eagleeye.collector;

import com.eagleeye.domain.entity.AbstractMarketPosition;
import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.FuturesPositionRepository;
import com.eagleeye.domain.repository.OptionsPositionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence checks for {@link FuturesPosition} and {@link OptionsPosition}, which share
 * their column mapping through {@link AbstractMarketPosition}. Verifies the inherited
 * {@code @MappedSuperclass} columns round-trip and the per-table unique constraint holds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:position_repo_it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Disable the launchd one-shot runner, which calls System.exit on context start.
        "eagleeye.collector.enabled=false"
})
@Transactional
class FuturesOptionsPositionRepositoryIT {

    @Autowired
    private FuturesPositionRepository futuresRepository;

    @Autowired
    private OptionsPositionRepository optionsRepository;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 19);
    private static final String CONTRACT = "TX";

    @Test
    @DisplayName("FuturesPosition persists all twelve inherited volume/value columns")
    void futures_save_persistsAllTwelveInheritedFields() {
        FuturesPosition saved = new FuturesPosition(DATE, CONTRACT, TraderType.FINI);
        applySampleValues(saved);
        futuresRepository.saveAndFlush(saved);

        FuturesPosition found = futuresRepository
                .findByTradeDateAndContractAndTraderType(DATE, CONTRACT, TraderType.FINI)
                .orElseThrow();

        assertInheritedColumns(found);
        assertThat(found.getTradeDate()).isEqualTo(DATE);
        assertThat(found.getContract()).isEqualTo(CONTRACT);
        assertThat(found.getTraderType()).isEqualTo(TraderType.FINI);
    }

    @Test
    @DisplayName("FuturesPosition rejects a duplicate (trade_date, contract, trader_type)")
    void futures_save_duplicateKey_throwsConstraintViolation() {
        futuresRepository.saveAndFlush(new FuturesPosition(DATE, CONTRACT, TraderType.DEALER));

        assertThatThrownBy(() -> futuresRepository
                .saveAndFlush(new FuturesPosition(DATE, CONTRACT, TraderType.DEALER)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("OptionsPosition persists all twelve inherited volume/value columns")
    void options_save_persistsAllTwelveInheritedFields() {
        OptionsPosition saved = new OptionsPosition(DATE, CONTRACT, TraderType.INVESTMENT_TRUST);
        applySampleValues(saved);
        optionsRepository.saveAndFlush(saved);

        OptionsPosition found = optionsRepository
                .findByTradeDateAndContractAndTraderType(DATE, CONTRACT, TraderType.INVESTMENT_TRUST)
                .orElseThrow();

        assertInheritedColumns(found);
        assertThat(found.getTradeDate()).isEqualTo(DATE);
        assertThat(found.getContract()).isEqualTo(CONTRACT);
        assertThat(found.getTraderType()).isEqualTo(TraderType.INVESTMENT_TRUST);
    }

    @Test
    @DisplayName("OptionsPosition rejects a duplicate (trade_date, contract, trader_type)")
    void options_save_duplicateKey_throwsConstraintViolation() {
        optionsRepository.saveAndFlush(new OptionsPosition(DATE, CONTRACT, TraderType.DEALER));

        assertThatThrownBy(() -> optionsRepository
                .saveAndFlush(new OptionsPosition(DATE, CONTRACT, TraderType.DEALER)))
                .isInstanceOf(Exception.class);
    }

    // Distinct value per column so a mis-wired getter/setter would surface.
    private static void applySampleValues(AbstractMarketPosition p) {
        p.setTradingLongVolume(1L);
        p.setTradingLongValue(2L);
        p.setTradingShortVolume(3L);
        p.setTradingShortValue(4L);
        p.setTradingNetVolume(5L);
        p.setTradingNetValue(6L);
        p.setOiLongVolume(7L);
        p.setOiLongValue(8L);
        p.setOiShortVolume(9L);
        p.setOiShortValue(10L);
        p.setOiNetVolume(11L);
        p.setOiNetValue(12L);
    }

    private static void assertInheritedColumns(AbstractMarketPosition p) {
        assertThat(p.getId()).isNotNull();
        assertThat(p.getTradingLongVolume()).isEqualTo(1L);
        assertThat(p.getTradingLongValue()).isEqualTo(2L);
        assertThat(p.getTradingShortVolume()).isEqualTo(3L);
        assertThat(p.getTradingShortValue()).isEqualTo(4L);
        assertThat(p.getTradingNetVolume()).isEqualTo(5L);
        assertThat(p.getTradingNetValue()).isEqualTo(6L);
        assertThat(p.getOiLongVolume()).isEqualTo(7L);
        assertThat(p.getOiLongValue()).isEqualTo(8L);
        assertThat(p.getOiShortVolume()).isEqualTo(9L);
        assertThat(p.getOiShortValue()).isEqualTo(10L);
        assertThat(p.getOiNetVolume()).isEqualTo(11L);
        assertThat(p.getOiNetValue()).isEqualTo(12L);
    }
}
