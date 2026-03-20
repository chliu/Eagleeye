# Institutional Flow Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collect daily institutional investor trading values (Foreign Investors, Investment Trust, Dealers) from TWSE BFI82U and expose them via shell commands.

**Architecture:** New `InstitutionalFlow` entity in `eagleeye-domain`; `InstitutionalFlowService` in `eagleeye-collector` fetches and persists per-day data; standalone `InstitutionalFlowScheduler` triggers daily at 15:30 Taipei; `CombinedBackfillRunner` extended with a 5th service; `InstitutionalFlowCommands` in `eagleeye-shell` with `TableFormatter` engine fix for single-date-column tables.

**Tech Stack:** Spring Boot 4, Java 25, JPA/H2, JUnit 5, AssertJ, Mockito, Spring Shell

---

## Chunk 1: Domain Layer

**Files:**
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/InstitutionalFlow.java`
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/InstitutionalFlowRepository.java`
- Create: `eagleeye-domain/src/test/java/com/eagleeye/domain/entity/InstitutionalFlowTest.java`

### Task 1: InstitutionalFlow entity unit test (Red)

- [ ] **Step 1: Write the failing test**

Create `eagleeye-domain/src/test/java/com/eagleeye/domain/entity/InstitutionalFlowTest.java`:

```java
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
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd eagleeye-domain && mvn test -pl . -Dtest=InstitutionalFlowTest -q 2>&1 | tail -5
```
Expected: compilation error — `InstitutionalFlow` does not exist.

### Task 2: InstitutionalFlow entity (Green)

- [ ] **Step 3: Create `InstitutionalFlow.java`**

```java
package com.eagleeye.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "institutional_flow",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_institutional_flow_trade_date",
        columnNames = {"trade_date"}
    )
)
public class InstitutionalFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    // Foreign Investors — NTD
    @Column(name = "foreign_buy")  private Long foreignBuy;
    @Column(name = "foreign_sell") private Long foreignSell;
    @Column(name = "foreign_net")  private Long foreignNet;

    // Investment Trust — NTD
    @Column(name = "investment_trust_buy")  private Long investmentTrustBuy;
    @Column(name = "investment_trust_sell") private Long investmentTrustSell;
    @Column(name = "investment_trust_net")  private Long investmentTrustNet;

    // Dealers — NTD
    @Column(name = "dealer_buy")  private Long dealerBuy;
    @Column(name = "dealer_sell") private Long dealerSell;
    @Column(name = "dealer_net")  private Long dealerNet;

    protected InstitutionalFlow() {}

    public InstitutionalFlow(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Long getId()                   { return id; }
    public LocalDate getTradeDate()       { return tradeDate; }
    public Long getForeignBuy()           { return foreignBuy; }
    public Long getForeignSell()          { return foreignSell; }
    public Long getForeignNet()           { return foreignNet; }
    public Long getInvestmentTrustBuy()   { return investmentTrustBuy; }
    public Long getInvestmentTrustSell()  { return investmentTrustSell; }
    public Long getInvestmentTrustNet()   { return investmentTrustNet; }
    public Long getDealerBuy()            { return dealerBuy; }
    public Long getDealerSell()           { return dealerSell; }
    public Long getDealerNet()            { return dealerNet; }

    public void setForeignBuy(Long v)          { this.foreignBuy = v; }
    public void setForeignSell(Long v)         { this.foreignSell = v; }
    public void setForeignNet(Long v)          { this.foreignNet = v; }
    public void setInvestmentTrustBuy(Long v)  { this.investmentTrustBuy = v; }
    public void setInvestmentTrustSell(Long v) { this.investmentTrustSell = v; }
    public void setInvestmentTrustNet(Long v)  { this.investmentTrustNet = v; }
    public void setDealerBuy(Long v)           { this.dealerBuy = v; }
    public void setDealerSell(Long v)          { this.dealerSell = v; }
    public void setDealerNet(Long v)           { this.dealerNet = v; }
}
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
cd eagleeye-domain && mvn test -pl . -Dtest=InstitutionalFlowTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

### Task 3: InstitutionalFlowRepository

- [ ] **Step 5: Create `InstitutionalFlowRepository.java`**

```java
package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.InstitutionalFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstitutionalFlowRepository extends JpaRepository<InstitutionalFlow, Long> {
    Optional<InstitutionalFlow> findByTradeDate(LocalDate tradeDate);
    List<InstitutionalFlow> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
}
```

- [ ] **Step 6: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/entity/InstitutionalFlow.java \
        eagleeye-domain/src/main/java/com/eagleeye/domain/repository/InstitutionalFlowRepository.java \
        eagleeye-domain/src/test/java/com/eagleeye/domain/entity/InstitutionalFlowTest.java
git commit -m "feat(domain): add InstitutionalFlow entity and repository"
```

---

## Chunk 2: Collector — Client, Parser, Result, Service, Unit Tests

**Files:**
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseClient.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseParser.java`
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/InstitutionalFlowResult.java`
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/InstitutionalFlowService.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/twse/TwseParserTest.java`
- Create: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/InstitutionalFlowServiceTest.java`

### Task 4: TwseParser — parseInstitutionalFlow (TDD)

- [ ] **Step 1: Add parser tests (Red)**

Add to `eagleeye-collector/src/test/java/com/eagleeye/collector/twse/TwseParserTest.java` — append these test methods inside the existing class:

```java
    // ── parseInstitutionalFlow ────────────────────────────────────────────────

    private static final String FLOW_JSON = """
            {
              "stat": "OK",
              "tables": [
                {
                  "data": [
                    ["Foreign Investors", "100,000,000,000", "80,000,000,000", "20,000,000,000"],
                    ["Investment Trust",   "5,000,000,000",  "4,000,000,000",  "1,000,000,000"],
                    ["Dealers",            "3,000,000,000",  "2,500,000,000",    "500,000,000"]
                  ]
                }
              ]
            }
            """;

    private static final LocalDate FLOW_DATE = LocalDate.of(2026, 3, 19);

    @Test
    void parseInstitutionalFlow_success_returnsAllNineFields() {
        InstitutionalFlow flow = parser.parseInstitutionalFlow(FLOW_JSON, FLOW_DATE);

        assertThat(flow).isNotNull();
        assertThat(flow.getTradeDate()).isEqualTo(FLOW_DATE);
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

    @Test
    void parseInstitutionalFlow_statNotOk_returnsNull() {
        String json = """
                {"stat": "NO DATA", "tables": [{"data": []}]}
                """;
        assertThat(parser.parseInstitutionalFlow(json, FLOW_DATE)).isNull();
    }

    @Test
    void parseInstitutionalFlow_emptyData_returnsNull() {
        String json = """
                {"stat": "OK", "tables": [{"data": []}]}
                """;
        assertThat(parser.parseInstitutionalFlow(json, FLOW_DATE)).isNull();
    }

    @Test
    void parseInstitutionalFlow_invalidJson_returnsNull() {
        assertThat(parser.parseInstitutionalFlow("not-json", FLOW_DATE)).isNull();
    }
```

Note: `TwseParserTest` already has `parser` field and `@BeforeEach` setup — append only the new fields and test methods.

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd eagleeye-collector && mvn test -Dtest=TwseParserTest -q 2>&1 | tail -10
```
Expected: compilation error — `parseInstitutionalFlow` does not exist.

- [ ] **Step 3: Add `fetchInstitutionalFlowJson` to TwseClient**

In `TwseClient.java`, add after the `MARGIN_PATH` constant and add the method:

```java
    private static final String INSTITUTIONAL_FLOW_PATH = "/rwd/en/fund/BFI82U";
```

```java
    public String fetchInstitutionalFlowJson(LocalDate date) {
        String queryDate = date.format(DATE_FORMAT);
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(INSTITUTIONAL_FLOW_PATH)
                        .queryParam("type", "day")
                        .queryParam("dayDate", queryDate)
                        .queryParam("response", "json")
                        .build())
                .retrieve()
                .body(String.class);
    }
```

- [ ] **Step 4: Add `parseInstitutionalFlow` to TwseParser**

In `TwseParser.java`, add the import and method. Add import at top:
```java
import com.eagleeye.domain.entity.InstitutionalFlow;
```

Add method after `parseMargin`:

```java
    /**
     * Parses BFI82U institutional investor daily trading value JSON for a single date.
     *
     * Response: tables[0].data — rows identified by label in column [0]:
     *   "Foreign Investors" → foreignBuy/Sell/Net
     *   "Investment Trust"  → investmentTrustBuy/Sell/Net
     *   "Dealers"           → dealerBuy/Sell/Net
     * Columns: [1] = buy, [2] = sell, [3] = net — raw NTD integers (comma-formatted).
     */
    public InstitutionalFlow parseInstitutionalFlow(String json, LocalDate date) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse institutional flow JSON: {}", truncate(json));
            return null;
        }

        String stat = root.path("stat").asText("");
        if (!"OK".equals(stat)) {
            log.info("Institutional flow stat='{}' — no data for {}", stat, date);
            return null;
        }

        JsonNode tables = root.path("tables");
        JsonNode data = (tables.isArray() && !tables.isEmpty())
                ? tables.get(0).path("data")
                : root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            log.info("Institutional flow data missing for {} (raw: {})", date, truncate(json));
            return null;
        }

        try {
            InstitutionalFlow flow = new InstitutionalFlow(date);
            for (JsonNode row : data) {
                String label = row.get(0).asText();
                long buy  = toLong(row.get(1).asText());
                long sell = toLong(row.get(2).asText());
                long net  = toLong(row.get(3).asText());
                if (label.contains("Foreign Investors")) {
                    flow.setForeignBuy(buy);
                    flow.setForeignSell(sell);
                    flow.setForeignNet(net);
                } else if (label.contains("Investment Trust")) {
                    flow.setInvestmentTrustBuy(buy);
                    flow.setInvestmentTrustSell(sell);
                    flow.setInvestmentTrustNet(net);
                } else if (label.contains("Dealers")) {
                    flow.setDealerBuy(buy);
                    flow.setDealerSell(sell);
                    flow.setDealerNet(net);
                }
            }
            return flow;
        } catch (Exception e) {
            log.warn("Failed to parse institutional flow rows for {}: {}", date, e.getMessage());
            log.debug("Institutional flow raw data: {}", data);
            return null;
        }
    }
```

- [ ] **Step 5: Run parser tests to confirm they pass**

```bash
cd eagleeye-collector && mvn test -Dtest=TwseParserTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseClient.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseParser.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/twse/TwseParserTest.java
git commit -m "feat(collector): add fetchInstitutionalFlowJson and parseInstitutionalFlow"
```

### Task 5: InstitutionalFlowResult record

- [ ] **Step 7: Create `InstitutionalFlowResult.java`**

```java
package com.eagleeye.collector.service;

import java.time.LocalDate;

/**
 * Result of an institutional flow daily collection operation.
 */
public record InstitutionalFlowResult(
        LocalDate tradeDate,
        Status status,
        String errorMessage
) {
    public enum Status { COLLECTED, NO_DATA, ERROR }

    public static InstitutionalFlowResult collected(LocalDate date) {
        return new InstitutionalFlowResult(date, Status.COLLECTED, null);
    }

    public static InstitutionalFlowResult noData(LocalDate date) {
        return new InstitutionalFlowResult(date, Status.NO_DATA, null);
    }

    public static InstitutionalFlowResult error(LocalDate date, String message) {
        return new InstitutionalFlowResult(date, Status.ERROR, message);
    }
}
```

### Task 6: InstitutionalFlowService (TDD)

- [ ] **Step 8: Write service unit tests (Red)**

Create `eagleeye-collector/src/test/java/com/eagleeye/collector/service/InstitutionalFlowServiceTest.java`:

```java
package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitutionalFlowServiceTest {

    @Mock private TwseClient twseClient;
    @Mock private TwseParser twseParser;
    @Mock private InstitutionalFlowRepository repository;

    private InstitutionalFlowService service;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 19);
    private static final String FLOW_JSON = "{\"stat\":\"OK\",\"tables\":[{\"data\":[]}]}";

    @BeforeEach
    void setUp() {
        service = new InstitutionalFlowService(twseClient, twseParser, repository);
    }

    @Test
    void collectDate_success_savesAndReturnsCollected() {
        InstitutionalFlow flow = new InstitutionalFlow(DATE);
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(FLOW_JSON);
        when(twseParser.parseInstitutionalFlow(FLOW_JSON, DATE)).thenReturn(flow);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.empty());

        InstitutionalFlowResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(InstitutionalFlowResult.Status.COLLECTED);
        assertThat(result.tradeDate()).isEqualTo(DATE);
        verify(repository).save(any(InstitutionalFlow.class));
    }

    @Test
    void collectDate_noData_returnsNoData() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn("{\"stat\":\"NO DATA\"}");
        when(twseParser.parseInstitutionalFlow(any(), eq(DATE))).thenReturn(null);

        InstitutionalFlowResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(InstitutionalFlowResult.Status.NO_DATA);
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_clientThrows_returnsError() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenThrow(new RuntimeException("timeout"));

        InstitutionalFlowResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(InstitutionalFlowResult.Status.ERROR);
        assertThat(result.errorMessage()).contains("timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_existingRecord_upserts() {
        InstitutionalFlow existing = new InstitutionalFlow(DATE);
        InstitutionalFlow parsed   = new InstitutionalFlow(DATE);
        parsed.setForeignBuy(100_000_000_000L);
        parsed.setForeignNet(-5_000_000_000L);

        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(FLOW_JSON);
        when(twseParser.parseInstitutionalFlow(FLOW_JSON, DATE)).thenReturn(parsed);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.of(existing));

        service.collectDate(DATE);

        ArgumentCaptor<InstitutionalFlow> captor = ArgumentCaptor.forClass(InstitutionalFlow.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getForeignBuy()).isEqualTo(100_000_000_000L);
        assertThat(captor.getValue().getForeignNet()).isEqualTo(-5_000_000_000L);
    }
}
```

- [ ] **Step 9: Run tests to confirm they fail**

```bash
cd eagleeye-collector && mvn test -Dtest=InstitutionalFlowServiceTest -q 2>&1 | tail -10
```
Expected: compilation error — `InstitutionalFlowService` does not exist.

- [ ] **Step 10: Create `InstitutionalFlowService.java`**

```java
package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class InstitutionalFlowService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalFlowService.class);

    private final TwseClient twseClient;
    private final TwseParser twseParser;
    private final InstitutionalFlowRepository repository;

    public InstitutionalFlowService(TwseClient twseClient,
                                    TwseParser twseParser,
                                    InstitutionalFlowRepository repository) {
        this.twseClient = twseClient;
        this.twseParser = twseParser;
        this.repository = repository;
    }

    @Transactional
    public InstitutionalFlowResult collectDate(LocalDate date) {
        try {
            String json = twseClient.fetchInstitutionalFlowJson(date);
            InstitutionalFlow parsed = twseParser.parseInstitutionalFlow(json, date);
            if (parsed == null) {
                log.info("No institutional flow data for {}", date);
                return InstitutionalFlowResult.noData(date);
            }
            upsert(parsed);
            log.info("Collected institutional flow data for {}", date);
            return InstitutionalFlowResult.collected(date);
        } catch (Exception e) {
            log.error("Institutional flow collection failed for {}: {}", date, e.getMessage(), e);
            return InstitutionalFlowResult.error(date, e.getMessage());
        }
    }

    private void upsert(InstitutionalFlow parsed) {
        InstitutionalFlow flow = repository
                .findByTradeDate(parsed.getTradeDate())
                .orElseGet(() -> new InstitutionalFlow(parsed.getTradeDate()));

        flow.setForeignBuy(parsed.getForeignBuy());
        flow.setForeignSell(parsed.getForeignSell());
        flow.setForeignNet(parsed.getForeignNet());
        flow.setInvestmentTrustBuy(parsed.getInvestmentTrustBuy());
        flow.setInvestmentTrustSell(parsed.getInvestmentTrustSell());
        flow.setInvestmentTrustNet(parsed.getInvestmentTrustNet());
        flow.setDealerBuy(parsed.getDealerBuy());
        flow.setDealerSell(parsed.getDealerSell());
        flow.setDealerNet(parsed.getDealerNet());

        repository.save(flow);
    }
}
```

- [ ] **Step 11: Run service unit tests to confirm they pass**

```bash
cd eagleeye-collector && mvn test -Dtest=InstitutionalFlowServiceTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 12: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/service/InstitutionalFlowResult.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/service/InstitutionalFlowService.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/InstitutionalFlowServiceTest.java
git commit -m "feat(collector): add InstitutionalFlowResult and InstitutionalFlowService"
```

---

## Chunk 3: Scheduler and CombinedBackfillRunner

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/scheduler/InstitutionalFlowScheduler.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CombinedBackfillRunner.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/runner/CombinedBackfillRunnerTest.java`

### Task 7: InstitutionalFlowScheduler

- [ ] **Step 1: Create `InstitutionalFlowScheduler.java`**

Mirror `MarginTransactionScheduler` exactly:

```java
package com.eagleeye.collector.scheduler;

import com.eagleeye.collector.service.InstitutionalFlowResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Triggers daily institutional flow collection at 15:30 Taipei time.
 *
 * TWSE publishes BFI82U data after the 13:30 market close.
 * 15:30 provides a safe 2-hour buffer.
 *
 * Does NOT modify CollectionScheduler or MarginTransactionScheduler — independent bean.
 */
@Component
public class InstitutionalFlowScheduler {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalFlowScheduler.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final InstitutionalFlowService institutionalFlowService;

    public InstitutionalFlowScheduler(InstitutionalFlowService institutionalFlowService) {
        this.institutionalFlowService = institutionalFlowService;
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Taipei")
    public void collect() {
        LocalDate today = LocalDate.now(TAIPEI);
        log.info("=== Institutional flow daily collection triggered for {} ===", today);
        try {
            InstitutionalFlowResult result = institutionalFlowService.collectDate(today);
            log.info("=== Institutional flow collection completed: {} for {} ===", result.status(), today);
        } catch (Exception e) {
            log.error("Institutional flow daily collection failed for {}: {}", today, e.getMessage(), e);
        }
    }
}
```

### Task 8: CombinedBackfillRunner — add InstitutionalFlowService (TDD)

- [ ] **Step 2: Add tests to CombinedBackfillRunnerTest (Red)**

In `CombinedBackfillRunnerTest.java`, make these changes:

1. Add mock field after the `marginTransactionService` mock:
```java
    @Mock private InstitutionalFlowService institutionalFlowService;
```

2. Update `setUp()` to pass the new mock (6-arg test constructor):
```java
    runner = new CombinedBackfillRunner(marketIndexService, collectionService, null,
            marginTransactionService, institutionalFlowService, 0);
```

3. Add `stubFlow()` helper alongside `stubMargin()`:
```java
    private void stubFlow() {
        when(institutionalFlowService.collectDate(any(LocalDate.class)))
                .thenReturn(InstitutionalFlowResult.collected(LocalDate.now()));
    }
```

4. Add `stubFlow()` call to every existing test method (alongside `stubMargin()`).

5. Add two new test methods:
```java
    // ── Institutional flow: every weekday in range ───────────────────────────

    @Test
    void executeBackfill_collectsFlowForEachWeekday() throws Exception {
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 6));

        verify(institutionalFlowService, times(4)).collectDate(any(LocalDate.class));
        verify(institutionalFlowService).collectDate(LocalDate.of(2026, 3, 3));
        verify(institutionalFlowService).collectDate(LocalDate.of(2026, 3, 6));
    }

    @Test
    void executeBackfill_skipsFlowOnWeekends() throws Exception {
        stubMarketIndex(YearMonth.of(2026, 3));
        stubTaifex();
        stubMargin();
        stubFlow();

        runner.executeBackfill(LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 9));

        verify(institutionalFlowService, times(2)).collectDate(any(LocalDate.class));
        verify(institutionalFlowService, never()).collectDate(LocalDate.of(2026, 3, 7));
        verify(institutionalFlowService, never()).collectDate(LocalDate.of(2026, 3, 8));
    }
```

Add import at top of test file:
```java
import com.eagleeye.collector.service.InstitutionalFlowResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
```

- [ ] **Step 3: Run tests to confirm they fail**

```bash
cd eagleeye-collector && mvn test -Dtest=CombinedBackfillRunnerTest -q 2>&1 | tail -10
```
Expected: compilation error — 6-arg constructor does not exist.

- [ ] **Step 4: Update CombinedBackfillRunner**

Make these changes to `CombinedBackfillRunner.java`:

1. Add import:
```java
import com.eagleeye.collector.service.InstitutionalFlowResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
```

2. Add field after `marginTransactionService`:
```java
    private final InstitutionalFlowService institutionalFlowService;
```

3. Replace the 4-arg `@Autowired` constructor with a 5-arg one, and the 5-arg test constructor with a 6-arg one:

```java
    @Autowired
    public CombinedBackfillRunner(MarketIndexService marketIndexService,
                                  CollectionService collectionService,
                                  ApplicationContext applicationContext,
                                  MarginTransactionService marginTransactionService,
                                  InstitutionalFlowService institutionalFlowService) {
        this(marketIndexService, collectionService, applicationContext,
                marginTransactionService, institutionalFlowService, 500);
    }

    CombinedBackfillRunner(MarketIndexService marketIndexService,
                           CollectionService collectionService,
                           ApplicationContext applicationContext,
                           MarginTransactionService marginTransactionService,
                           InstitutionalFlowService institutionalFlowService,
                           long requestDelayMs) {
        this.marketIndexService = marketIndexService;
        this.collectionService = collectionService;
        this.marginTransactionService = marginTransactionService;
        this.institutionalFlowService = institutionalFlowService;
        this.applicationContext = applicationContext;
        this.requestDelayMs = requestDelayMs;
    }
```

4. In `executeBackfill`, after the `printMargin` call and sleep, add:
```java
                    InstitutionalFlowResult flowResult = institutionalFlowService.collectDate(day);
                    printInstitutionalFlow(day, flowResult);
                    Thread.sleep(requestDelayMs);
```

5. Add `printInstitutionalFlow` helper method:
```java
    private void printInstitutionalFlow(LocalDate date, InstitutionalFlowResult r) {
        String status = switch (r.status()) {
            case COLLECTED -> "collected";
            case NO_DATA   -> "no data";
            case ERROR     -> "ERROR: " + r.errorMessage();
        };
        System.out.printf("  [IFLOW]   %-12s  %s%n", date, status);
    }
```

- [ ] **Step 5: Run all CombinedBackfillRunner tests**

```bash
cd eagleeye-collector && mvn test -Dtest=CombinedBackfillRunnerTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/scheduler/InstitutionalFlowScheduler.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CombinedBackfillRunner.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/runner/CombinedBackfillRunnerTest.java
git commit -m "feat(collector): add InstitutionalFlowScheduler and extend CombinedBackfillRunner"
```

---

## Chunk 4: Integration Tests

**Files:**
- Create: `eagleeye-collector/src/test/java/com/eagleeye/collector/InstitutionalFlowRepositoryIT.java`
- Create: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/InstitutionalFlowServiceIT.java`

### Task 9: InstitutionalFlowRepositoryIT

- [ ] **Step 1: Create `InstitutionalFlowRepositoryIT.java`**

```java
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
```

- [ ] **Step 2: Run repository IT**

```bash
cd eagleeye-collector && mvn test -Dtest=InstitutionalFlowRepositoryIT -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

### Task 10: InstitutionalFlowServiceIT

- [ ] **Step 3: Create `InstitutionalFlowServiceIT.java`**

```java
package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iflow_it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class InstitutionalFlowServiceIT {

    @Autowired
    private InstitutionalFlowService service;

    @Autowired
    private InstitutionalFlowRepository repository;

    @MockitoBean
    private TwseClient twseClient;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 19);

    private static final String FLOW_JSON = """
            {
              "stat": "OK",
              "tables": [
                {
                  "data": [
                    ["Foreign Investors", "100,000,000,000", "80,000,000,000", "20,000,000,000"],
                    ["Investment Trust",   "5,000,000,000",  "4,000,000,000",  "1,000,000,000"],
                    ["Dealers",            "3,000,000,000",  "2,500,000,000",    "500,000,000"]
                  ]
                }
              ]
            }
            """;

    private static final String NO_DATA_JSON = """
            {"stat": "NO DATA", "tables": [{"data": []}]}
            """;

    @Test
    void collectDate_returnsCollected_andPersistsAllNineFields() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(FLOW_JSON);

        InstitutionalFlowResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(InstitutionalFlowResult.Status.COLLECTED);
        assertThat(result.tradeDate()).isEqualTo(DATE);

        InstitutionalFlow saved = repository.findByTradeDate(DATE).orElseThrow();
        assertThat(saved.getForeignBuy()).isEqualTo(100_000_000_000L);
        assertThat(saved.getForeignSell()).isEqualTo(80_000_000_000L);
        assertThat(saved.getForeignNet()).isEqualTo(20_000_000_000L);
        assertThat(saved.getInvestmentTrustBuy()).isEqualTo(5_000_000_000L);
        assertThat(saved.getInvestmentTrustSell()).isEqualTo(4_000_000_000L);
        assertThat(saved.getInvestmentTrustNet()).isEqualTo(1_000_000_000L);
        assertThat(saved.getDealerBuy()).isEqualTo(3_000_000_000L);
        assertThat(saved.getDealerSell()).isEqualTo(2_500_000_000L);
        assertThat(saved.getDealerNet()).isEqualTo(500_000_000L);
    }

    @Test
    void collectDate_upserts_updatesExistingRowWithoutCreatingDuplicate() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(FLOW_JSON);
        service.collectDate(DATE);
        assertThat(repository.count()).isEqualTo(1);

        String updatedJson = """
                {
                  "stat": "OK",
                  "tables": [
                    {
                      "data": [
                        ["Foreign Investors", "200,000,000,000", "150,000,000,000", "50,000,000,000"],
                        ["Investment Trust",   "6,000,000,000",   "5,000,000,000",  "1,000,000,000"],
                        ["Dealers",            "4,000,000,000",   "3,000,000,000",  "1,000,000,000"]
                      ]
                    }
                  ]
                }
                """;
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(updatedJson);
        service.collectDate(DATE);

        assertThat(repository.count()).isEqualTo(1);
        InstitutionalFlow updated = repository.findByTradeDate(DATE).orElseThrow();
        assertThat(updated.getForeignBuy()).isEqualTo(200_000_000_000L);
        assertThat(updated.getDealerNet()).isEqualTo(1_000_000_000L);
    }

    @Test
    void collectDate_returnsNoData_andPersistsNothing_whenApiReturnsNoStat() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(NO_DATA_JSON);

        InstitutionalFlowResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(InstitutionalFlowResult.Status.NO_DATA);
        assertThat(repository.findByTradeDate(DATE)).isEmpty();
    }

    @Test
    void collectDate_returnsError_andPersistsNothing_whenClientThrows() {
        when(twseClient.fetchInstitutionalFlowJson(DATE))
                .thenThrow(new RuntimeException("connection timeout"));

        InstitutionalFlowResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(InstitutionalFlowResult.Status.ERROR);
        assertThat(result.errorMessage()).contains("connection timeout");
        assertThat(repository.findByTradeDate(DATE)).isEmpty();
    }
}
```

- [ ] **Step 4: Run service IT**

```bash
cd eagleeye-collector && mvn test -Dtest=InstitutionalFlowServiceIT -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Run full collector test suite**

```bash
cd eagleeye-collector && mvn test -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add eagleeye-collector/src/test/java/com/eagleeye/collector/InstitutionalFlowRepositoryIT.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/InstitutionalFlowServiceIT.java
git commit -m "test(collector): add InstitutionalFlow repository and service integration tests"
```

---

## Chunk 5: Shell Layer

**Files:**
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/formatter/TableFormatter.java`
- Create: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/InstitutionalFlowCommands.java`
- Create: `eagleeye-shell/src/test/java/com/eagleeye/shell/formatter/InstitutionalFlowFormatterTest.java`
- Create: `eagleeye-shell/src/test/java/com/eagleeye/shell/commands/InstitutionalFlowCommandsTest.java`

### Task 11: TableFormatter — engine fix + formatInstitutionalFlow (TDD)

- [ ] **Step 1: Write formatter tests (Red)**

Create `eagleeye-shell/src/test/java/com/eagleeye/shell/formatter/InstitutionalFlowFormatterTest.java`:

```java
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
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd eagleeye-shell && mvn test -Dtest=InstitutionalFlowFormatterTest -q 2>&1 | tail -10
```
Expected: compilation error — `formatInstitutionalFlow` does not exist.

- [ ] **Step 3: Apply TableFormatter engine fix and add formatInstitutionalFlow**

Make the following changes to `TableFormatter.java`:

**3a. Add constant** after `W_MARGIN`:
```java
    private static final int W_FLOW     = 17;  // e.g. "+123,456,789,012" (15 chars + sign + space)
```

**3b. Change `renderTable` signature** to accept `numTextCols`:
```java
    private String renderTable(String[] headers, int[] widths, List<Row> rows, int numTextCols) {
```

Update the body to pass `numTextCols` to `dataLine`:
```java
        sb.append(dataLine(headers, widths, numTextCols)).append('\n');
        // ...
                sb.append(dataLine(row.cells, widths, numTextCols)).append('\n');
```

**3c. Change `dataLine` signature** and replace `i < 2`:
```java
    private String dataLine(String[] cells, int[] widths, int numTextCols) {
        // ...
            String fmt = i < numTextCols ? " %-" + widths[i] + "s " : " %" + widths[i] + "s ";
```

**3d. Update all existing `renderTable` call sites** to pass `numTextCols`.
There are exactly 4 call sites in `TableFormatter.java`:
- Line 88 in `formatPositions`: `return renderTable(headers, widths, rows);` → `return renderTable(headers, widths, rows, 2);`
- Line 110 in `formatMarketIndex`: `return renderTable(headers, widths, rows);` → `return renderTable(headers, widths, rows, 1);`
- Line 134 in `formatMarginTransaction`: `return renderTable(headers, widths, rows);` → `return renderTable(headers, widths, rows, 1);`
- Line 168 in `formatTrend`: `return renderTable(headers, widths, rows);` → `return renderTable(headers, widths, rows, 2);`

**3e. Add `formatInstitutionalFlow` method** after `formatMarginTransaction`:

```java
    /** Institutional investor daily trading values. Columns: Date + 9 numeric. */
    public String formatInstitutionalFlow(List<InstitutionalFlow> flows) {
        if (flows.isEmpty()) return "No data found.";

        int[] widths = {W_DATE, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW, W_FLOW};
        String[] headers = {"Date", "F-Buy", "F-Sell", "F-Net", "IT-Buy", "IT-Sell", "IT-Net", "D-Buy", "D-Sell", "D-Net"};

        List<Row> rows = new ArrayList<>();
        for (InstitutionalFlow f : flows) {
            rows.add(Row.data(
                    f.getTradeDate().toString(),
                    fmtVol(f.getForeignBuy()),
                    fmtVol(f.getForeignSell()),
                    fmtNet(f.getForeignNet()),
                    fmtVol(f.getInvestmentTrustBuy()),
                    fmtVol(f.getInvestmentTrustSell()),
                    fmtNet(f.getInvestmentTrustNet()),
                    fmtVol(f.getDealerBuy()),
                    fmtVol(f.getDealerSell()),
                    fmtNet(f.getDealerNet())
            ));
        }
        return renderTable(headers, widths, rows, 1);
    }
```

**3f. Add import** at top of `TableFormatter.java`:
```java
import com.eagleeye.domain.entity.InstitutionalFlow;
```

- [ ] **Step 4: Run formatter tests to confirm they pass**

```bash
cd eagleeye-shell && mvn test -Dtest=InstitutionalFlowFormatterTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Run existing shell tests to confirm nothing regressed**

```bash
cd eagleeye-shell && mvn test -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add eagleeye-shell/src/main/java/com/eagleeye/shell/formatter/TableFormatter.java \
        eagleeye-shell/src/test/java/com/eagleeye/shell/formatter/InstitutionalFlowFormatterTest.java
git commit -m "feat(shell): add formatInstitutionalFlow and fix TableFormatter numTextCols"
```

### Task 12: InstitutionalFlowCommands (TDD)

- [ ] **Step 7: Write commands tests (Red)**

Create `eagleeye-shell/src/test/java/com/eagleeye/shell/commands/InstitutionalFlowCommandsTest.java`:

```java
package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.InstitutionalFlowResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import com.eagleeye.shell.formatter.TableFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitutionalFlowCommandsTest {

    @Mock private InstitutionalFlowService service;
    @Mock private InstitutionalFlowRepository repository;
    @Mock private TableFormatter formatter;

    private InstitutionalFlowCommands commands;

    @BeforeEach
    void setUp() {
        commands = new InstitutionalFlowCommands(service, repository, formatter);
    }

    // ── institutional-flow list ───────────────────────────────────────────────

    @Test
    void list_defaultsToToday() {
        when(repository.findByTradeDate(LocalDate.now())).thenReturn(Optional.empty());
        commands.list("");
        verify(repository).findByTradeDate(LocalDate.now());
    }

    @Test
    void list_parsesExplicitDate() {
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 19))).thenReturn(Optional.empty());
        commands.list("2026-03-19");
        verify(repository).findByTradeDate(LocalDate.of(2026, 3, 19));
    }

    @Test
    void list_flowFound_returnsFormattedTable() {
        InstitutionalFlow flow = new InstitutionalFlow(LocalDate.of(2026, 3, 19));
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 19))).thenReturn(Optional.of(flow));
        when(formatter.formatInstitutionalFlow(List.of(flow))).thenReturn("rendered");

        String result = commands.list("2026-03-19");
        assertThat(result).contains("rendered");
    }

    @Test
    void list_noFlowFound_returnsNoData() {
        when(repository.findByTradeDate(any())).thenReturn(Optional.empty());
        String result = commands.list("2026-03-19");
        assertThat(result).containsIgnoringCase("no data");
    }

    // ── institutional-flow show ───────────────────────────────────────────────

    @Test
    void show_defaultRange_queriesLast30Days() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of());
        when(formatter.formatInstitutionalFlow(any())).thenReturn("table");

        commands.show("", "");

        LocalDate today = LocalDate.now();
        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(today.minusDays(30), today);
    }

    @Test
    void show_explicitRange_queriesGivenRange() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of());
        when(formatter.formatInstitutionalFlow(any())).thenReturn("table");

        commands.show("2026-01-01", "2026-03-19");

        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 19));
    }

    @Test
    void show_returnsFormattedTableWithRowCount() {
        InstitutionalFlow flow = new InstitutionalFlow(LocalDate.of(2026, 3, 19));
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of(flow));
        when(formatter.formatInstitutionalFlow(List.of(flow))).thenReturn("rendered-table");

        String result = commands.show("2026-03-01", "2026-03-19");
        assertThat(result).contains("rendered-table");
        assertThat(result).contains("1");
    }

    // ── institutional-flow collect ────────────────────────────────────────────

    @Test
    void collect_defaultsToToday() {
        when(service.collectDate(any()))
                .thenReturn(InstitutionalFlowResult.collected(LocalDate.now()));

        commands.collect("");

        verify(service).collectDate(LocalDate.now());
    }

    @Test
    void collect_collected_containsCollected() {
        when(service.collectDate(any()))
                .thenReturn(InstitutionalFlowResult.collected(LocalDate.of(2026, 3, 19)));

        String result = commands.collect("2026-03-19");
        assertThat(result).containsIgnoringCase("collected");
    }

    @Test
    void collect_noData_containsNoData() {
        when(service.collectDate(any()))
                .thenReturn(InstitutionalFlowResult.noData(LocalDate.of(2026, 3, 19)));

        String result = commands.collect("2026-03-19");
        assertThat(result).containsIgnoringCase("no data");
    }

    // ── institutional-flow backfill ───────────────────────────────────────────

    @Test
    void backfill_skipsWeekends_onlyCallsServiceOnWeekdays() {
        // 2026-03-06 (Fri) to 2026-03-09 (Mon) = 2 weekdays
        when(service.collectDate(any()))
                .thenReturn(InstitutionalFlowResult.collected(LocalDate.now()));

        commands.backfill("2026-03-06", "2026-03-09");

        verify(service, times(2)).collectDate(any());
        verify(service).collectDate(LocalDate.of(2026, 3, 6)); // Fri
        verify(service).collectDate(LocalDate.of(2026, 3, 9)); // Mon
        verify(service, never()).collectDate(LocalDate.of(2026, 3, 7)); // Sat
        verify(service, never()).collectDate(LocalDate.of(2026, 3, 8)); // Sun
    }

    @Test
    void backfill_singleDay_returnsResult() {
        when(service.collectDate(LocalDate.of(2026, 3, 19)))
                .thenReturn(InstitutionalFlowResult.collected(LocalDate.of(2026, 3, 19)));

        String result = commands.backfill("2026-03-19", "2026-03-19");
        assertThat(result).containsIgnoringCase("collected");
    }
}
```

- [ ] **Step 8: Run tests to confirm they fail**

```bash
cd eagleeye-shell && mvn test -Dtest=InstitutionalFlowCommandsTest -q 2>&1 | tail -10
```
Expected: compilation error — `InstitutionalFlowCommands` does not exist.

- [ ] **Step 9: Create `InstitutionalFlowCommands.java`**

```java
package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.InstitutionalFlowResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import com.eagleeye.shell.formatter.TableFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class InstitutionalFlowCommands {

    private final InstitutionalFlowService service;
    private final InstitutionalFlowRepository repository;
    private final TableFormatter formatter;

    @Autowired
    public InstitutionalFlowCommands(InstitutionalFlowService service,
                                     InstitutionalFlowRepository repository,
                                     TableFormatter formatter) {
        this.service = service;
        this.repository = repository;
        this.formatter = formatter;
    }

    @Command(name = "institutional-flow list", description = "Show institutional flow data for a single date")
    public String list(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        Optional<InstitutionalFlow> flow = repository.findByTradeDate(d);
        if (flow.isEmpty()) return "No data for " + d;
        return formatter.formatInstitutionalFlow(List.of(flow.get()));
    }

    @Command(name = "institutional-flow show", description = "Show institutional flow data over a date range")
    public String show(
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 30 days ago)", defaultValue = "") String from,
            @Option(longName = "to",   description = "End date YYYY-MM-DD (default: today)",         defaultValue = "") String to) {
        LocalDate toDate   = (to   == null || to.isEmpty())   ? LocalDate.now()      : LocalDate.parse(to);
        LocalDate fromDate = (from == null || from.isEmpty()) ? toDate.minusDays(30) : LocalDate.parse(from);
        List<InstitutionalFlow> flows =
                repository.findByTradeDateBetweenOrderByTradeDateAsc(fromDate, toDate);
        return "Institutional Flow \u2014 " + fromDate + " \u2192 " + toDate
                + " (" + flows.size() + " records)\n"
                + formatter.formatInstitutionalFlow(flows);
    }

    @Command(name = "institutional-flow collect", description = "Collect institutional flow data for a date")
    public String collect(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        return formatResult(service.collectDate(d));
    }

    @Command(name = "institutional-flow backfill", description = "Backfill institutional flow data for a date range")
    public String backfill(
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 12 months ago)", defaultValue = "") String from,
            @Option(longName = "to",   description = "End date YYYY-MM-DD (default: today)",           defaultValue = "") String to) {
        LocalDate fromDate = (from == null || from.isEmpty()) ? LocalDate.now().minusMonths(12) : LocalDate.parse(from);
        LocalDate toDate   = (to   == null || to.isEmpty())   ? LocalDate.now()                 : LocalDate.parse(to);

        StringBuilder sb = new StringBuilder();
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY
                    && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                sb.append(formatResult(service.collectDate(current))).append("\n");
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            current = current.plusDays(1);
        }
        return sb.toString().stripTrailing();
    }

    private String formatResult(InstitutionalFlowResult r) {
        return switch (r.status()) {
            case COLLECTED -> r.tradeDate() + " \u2014 collected";
            case NO_DATA   -> r.tradeDate() + " \u2014 no data";
            case ERROR     -> r.tradeDate() + " \u2014 ERROR: " + r.errorMessage();
        };
    }
}
```

- [ ] **Step 10: Run commands tests to confirm they pass**

```bash
cd eagleeye-shell && mvn test -Dtest=InstitutionalFlowCommandsTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 11: Run full shell test suite**

```bash
cd eagleeye-shell && mvn test -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Step 12: Run full project build**

```bash
mvn clean test -q 2>&1 | tail -15
```
Expected: `BUILD SUCCESS` — all modules green.

- [ ] **Step 13: Commit**

```bash
git add eagleeye-shell/src/main/java/com/eagleeye/shell/commands/InstitutionalFlowCommands.java \
        eagleeye-shell/src/test/java/com/eagleeye/shell/commands/InstitutionalFlowCommandsTest.java
git commit -m "feat(shell): add InstitutionalFlowCommands"
```
