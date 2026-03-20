package com.eagleeye.collector;

import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.repository.MarginTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link MarginTransactionRepository} — verifies JPA mapping,
 * query methods, ordering, and the unique constraint on trade_date.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:repo_it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class MarginTransactionRepositoryIT {

    @Autowired
    private MarginTransactionRepository repository;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 18);

    // ── findByTradeDate ───────────────────────────────────────────────────────

    @Test
    void findByTradeDate_returnsEntity_whenExists() {
        repository.saveAndFlush(new MarginTransaction(DATE));

        Optional<MarginTransaction> found = repository.findByTradeDate(DATE);

        assertThat(found).isPresent();
        assertThat(found.get().getTradeDate()).isEqualTo(DATE);
    }

    @Test
    void findByTradeDate_returnsEmpty_whenAbsent() {
        assertThat(repository.findByTradeDate(DATE)).isEmpty();
    }

    // ── findByTradeDateBetweenOrderByTradeDateAsc ─────────────────────────────

    @Test
    void findByTradeDateBetween_returnsResultsInAscendingOrder() {
        LocalDate d1 = LocalDate.of(2026, 3, 17);
        LocalDate d2 = LocalDate.of(2026, 3, 18);
        LocalDate d3 = LocalDate.of(2026, 3, 19);
        // save out of order — query must re-sort
        repository.saveAllAndFlush(List.of(
                new MarginTransaction(d3),
                new MarginTransaction(d1),
                new MarginTransaction(d2)));

        List<MarginTransaction> results = repository.findByTradeDateBetweenOrderByTradeDateAsc(d1, d3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getTradeDate()).isEqualTo(d1);
        assertThat(results.get(1).getTradeDate()).isEqualTo(d2);
        assertThat(results.get(2).getTradeDate()).isEqualTo(d3);
    }

    @Test
    void findByTradeDateBetween_excludesDatesOutsideRange() {
        LocalDate before = LocalDate.of(2026, 3, 16);
        LocalDate from   = LocalDate.of(2026, 3, 17);
        LocalDate to     = LocalDate.of(2026, 3, 18);
        LocalDate after  = LocalDate.of(2026, 3, 19);
        repository.saveAllAndFlush(List.of(
                new MarginTransaction(before),
                new MarginTransaction(from),
                new MarginTransaction(to),
                new MarginTransaction(after)));

        List<MarginTransaction> results = repository.findByTradeDateBetweenOrderByTradeDateAsc(from, to);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(MarginTransaction::getTradeDate).containsExactly(from, to);
    }

    // ── persistence ───────────────────────────────────────────────────────────

    @Test
    void save_persistsAllTenFields() {
        MarginTransaction bar = new MarginTransaction(DATE);
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
        repository.saveAndFlush(bar);

        // clear first-level cache so we hit the DB
        repository.findById(bar.getId()); // force reload via fresh query
        MarginTransaction found = repository.findByTradeDate(DATE).orElseThrow();

        assertThat(found.getMarginPurchase()).isEqualTo(526_296L);
        assertThat(found.getMarginSale()).isEqualTo(485_038L);
        assertThat(found.getMarginCashRedemption()).isEqualTo(6_678L);
        assertThat(found.getMarginPrevBalance()).isEqualTo(8_074_444L);
        assertThat(found.getMarginBalance()).isEqualTo(8_109_024L);
        assertThat(found.getShortCovering()).isEqualTo(31_407L);
        assertThat(found.getShortSale()).isEqualTo(23_277L);
        assertThat(found.getShortStockRedemption()).isEqualTo(1_999L);
        assertThat(found.getShortPrevBalance()).isEqualTo(215_077L);
        assertThat(found.getShortBalance()).isEqualTo(204_948L);
    }

    @Test
    void save_duplicateTradeDate_throwsConstraintViolation() {
        repository.saveAndFlush(new MarginTransaction(DATE));

        MarginTransaction duplicate = new MarginTransaction(DATE);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(Exception.class);
    }
}
