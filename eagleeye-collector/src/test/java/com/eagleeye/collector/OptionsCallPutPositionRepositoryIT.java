package com.eagleeye.collector;

import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:callput_repo_it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eagleeye.collector.enabled=false"
})
@Transactional
class OptionsCallPutPositionRepositoryIT {

    @Autowired
    private OptionsCallPutPositionRepository repo;

    private static final LocalDate DATE = LocalDate.of(2026, 5, 28);

    @Test
    @DisplayName("persists inherited columns plus right_type")
    void save_persistsFullRow() {
        OptionsCallPutPosition saved =
                new OptionsCallPutPosition(DATE, "TXO", TraderType.FINI, RightType.CALL);
        saved.setOiShortValue(1_512_653L);
        saved.setOiNetValue(569_039L);
        repo.saveAndFlush(saved);

        OptionsCallPutPosition found = repo
                .findByTradeDateAndContractAndTraderTypeAndRightType(
                        DATE, "TXO", TraderType.FINI, RightType.CALL)
                .orElseThrow();

        assertThat(found.getRightType()).isEqualTo(RightType.CALL);
        assertThat(found.getOiShortValue()).isEqualTo(1_512_653L);
        assertThat(found.getOiNetValue()).isEqualTo(569_039L);
    }

    @Test
    @DisplayName("CALL and PUT for same trader coexist (right_type in unique key)")
    void save_callAndPut_coexist() {
        repo.saveAndFlush(new OptionsCallPutPosition(DATE, "TXO", TraderType.FINI, RightType.CALL));
        repo.saveAndFlush(new OptionsCallPutPosition(DATE, "TXO", TraderType.FINI, RightType.PUT));

        assertThat(repo.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("rejects duplicate (trade_date, contract, trader_type, right_type)")
    void save_duplicateKey_throws() {
        repo.saveAndFlush(new OptionsCallPutPosition(DATE, "TXO", TraderType.DEALER, RightType.CALL));

        assertThatThrownBy(() -> repo
                .saveAndFlush(new OptionsCallPutPosition(DATE, "TXO", TraderType.DEALER, RightType.CALL)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("dashboard finder returns matching right ordered by date")
    void finder_returnsOrderedSeries() {
        repo.saveAndFlush(new OptionsCallPutPosition(DATE, "TXO", TraderType.FINI, RightType.CALL));
        repo.saveAndFlush(new OptionsCallPutPosition(DATE.plusDays(1), "TXO", TraderType.FINI, RightType.CALL));
        repo.saveAndFlush(new OptionsCallPutPosition(DATE, "TXO", TraderType.FINI, RightType.PUT));

        List<OptionsCallPutPosition> calls = repo
                .findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
                        "TXO", TraderType.FINI, RightType.CALL, DATE, DATE.plusDays(5));

        assertThat(calls).hasSize(2);
        assertThat(calls).extracting(OptionsCallPutPosition::getTradeDate)
                .containsExactly(DATE, DATE.plusDays(1));
    }
}
