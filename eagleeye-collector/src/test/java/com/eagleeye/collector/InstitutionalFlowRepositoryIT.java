package com.eagleeye.collector;

import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iflow_repo_it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class InstitutionalFlowRepositoryIT {

    @Autowired
    private InstitutionalFlowRepository repository;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 19);

    // ── findByTradeDate ───────────────────────────────────────────────────────

    @Test
    void findByTradeDate_returnsEntity_whenExists() {
        repository.saveAndFlush(new InstitutionalFlow(DATE));

        Optional<InstitutionalFlow> found = repository.findByTradeDate(DATE);

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
        repository.saveAllAndFlush(List.of(
                new InstitutionalFlow(d3),
                new InstitutionalFlow(d1),
                new InstitutionalFlow(d2)));

        List<InstitutionalFlow> results =
                repository.findByTradeDateBetweenOrderByTradeDateAsc(d1, d3);

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
                new InstitutionalFlow(before),
                new InstitutionalFlow(from),
                new InstitutionalFlow(to),
                new InstitutionalFlow(after)));

        List<InstitutionalFlow> results =
                repository.findByTradeDateBetweenOrderByTradeDateAsc(from, to);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(InstitutionalFlow::getTradeDate).containsExactly(from, to);
    }

    // ── persistence ───────────────────────────────────────────────────────────

    @Test
    void save_persistsAllNineFields() {
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
        repository.saveAndFlush(flow);

        InstitutionalFlow found = repository.findByTradeDate(DATE).orElseThrow();

        assertThat(found.getForeignBuy()).isEqualTo(100_000_000_000L);
        assertThat(found.getForeignSell()).isEqualTo(80_000_000_000L);
        assertThat(found.getForeignNet()).isEqualTo(20_000_000_000L);
        assertThat(found.getInvestmentTrustBuy()).isEqualTo(5_000_000_000L);
        assertThat(found.getInvestmentTrustSell()).isEqualTo(4_000_000_000L);
        assertThat(found.getInvestmentTrustNet()).isEqualTo(1_000_000_000L);
        assertThat(found.getDealerBuy()).isEqualTo(3_000_000_000L);
        assertThat(found.getDealerSell()).isEqualTo(2_500_000_000L);
        assertThat(found.getDealerNet()).isEqualTo(500_000_000L);
    }

    @Test
    void save_duplicateTradeDate_throwsConstraintViolation() {
        repository.saveAndFlush(new InstitutionalFlow(DATE));

        assertThatThrownBy(() -> repository.saveAndFlush(new InstitutionalFlow(DATE)))
                .isInstanceOf(Exception.class);
    }
}
