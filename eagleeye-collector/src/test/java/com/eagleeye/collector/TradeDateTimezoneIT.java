package com.eagleeye.collector;

import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eagleeye.collector.enabled=false"
})
@Transactional
class TradeDateTimezoneIT {

    @Autowired private OptionsCallPutPositionRepository repo;
    @Autowired private EntityManager em;

    private static final LocalDate DATE = LocalDate.of(2026, 5, 28);

    @Test
    @DisplayName("SQLite stores trade_date as ISO text with no timezone offset")
    void tradeDate_storedAsIsoText_noOffset() {
        repo.saveAndFlush(new OptionsCallPutPosition(DATE, "TXO", TraderType.FINI, RightType.CALL));
        em.clear();

        // Before the fix SQLite stores trade_date as INTEGER epoch-millis (which reads one
        // day early under a UTC interpretation); the converter makes it ISO text.
        Object storageType = em.createNativeQuery(
                "SELECT typeof(trade_date) FROM options_call_put_position WHERE contract = 'TXO'")
                .getSingleResult();
        assertThat(storageType).isEqualTo("text");

        // A plain string-equality query (no timezone arithmetic) finds the row.
        Object isoValue = em.createNativeQuery(
                "SELECT trade_date FROM options_call_put_position WHERE trade_date = '2026-05-28'")
                .getSingleResult();
        assertThat(isoValue).hasToString("2026-05-28");

        OptionsCallPutPosition found = repo
                .findByTradeDateAndContractAndTraderTypeAndRightType(
                        DATE, "TXO", TraderType.FINI, RightType.CALL)
                .orElseThrow();
        assertThat(found.getTradeDate()).isEqualTo(DATE);
    }
}
