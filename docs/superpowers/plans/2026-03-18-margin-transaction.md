# Margin Transaction (融資融券) Collection Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Taiwan market-wide margin transaction daily data collection, storage, and shell display following the same pattern as MarketIndex/Futures/Options.

**Architecture:** New `MarginDailyBar` entity + repository in `eagleeye-domain`; new `fetchMarginJson` on `TwseClient`, `parseMargin` on `TwseParser`, `MarginCollectionResult`, `MarginTransactionService`, `MarginTransactionScheduler` in `eagleeye-collector`; update `CombinedBackfillRunner`; add `formatMarginTransaction` to `TableFormatter` and new `MarginTransactionCommands` in `eagleeye-shell`. TDD throughout.

**Tech Stack:** Spring Boot 4, Spring Data JPA, Spring Shell 4, Jackson 3 (`tools.jackson.databind`), JUnit 5, AssertJ, Mockito

---

## File Map

**New files:**
- `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/MarginDailyBar.java`
- `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/MarginDailyBarRepository.java`
- `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarginCollectionResult.java`
- `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarginTransactionService.java`
- `eagleeye-collector/src/main/java/com/eagleeye/collector/scheduler/MarginTransactionScheduler.java`
- `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarginTransactionCommands.java`
- Test counterparts for each of the above

**Modified files:**
- `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseClient.java` — add `fetchMarginJson(LocalDate)`
- `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseParser.java` — add `parseMargin(String, LocalDate)`
- `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CombinedBackfillRunner.java` — add `marginTransactionService` + call per weekday
- `eagleeye-collector/src/test/java/com/eagleeye/collector/runner/CombinedBackfillRunnerTest.java` — add mock + verify margin called per weekday
- `eagleeye-shell/src/main/java/com/eagleeye/shell/formatter/TableFormatter.java` — add `formatMarginTransaction`

---

## Chunk 1: Domain layer

### Task 1: `MarginDailyBar` entity

**Files:**
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/MarginDailyBar.java`

- [ ] **Step 1: Write the failing test**

Create `eagleeye-domain/src/test/java/com/eagleeye/domain/entity/MarginDailyBarTest.java`:

```java
package com.eagleeye.domain.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class MarginDailyBarTest {

    @Test
    void constructor_setsTradeDate() {
        LocalDate date = LocalDate.of(2026, 3, 18);
        MarginDailyBar bar = new MarginDailyBar(date);
        assertThat(bar.getTradeDate()).isEqualTo(date);
    }

    @Test
    void setters_storeAllFields() {
        MarginDailyBar bar = new MarginDailyBar(LocalDate.of(2026, 3, 18));
        bar.setMarginPurchase(526296L);
        bar.setMarginSale(485038L);
        bar.setMarginCashRedemption(6678L);
        bar.setMarginPrevBalance(8074444L);
        bar.setMarginBalance(8109024L);
        bar.setShortCovering(31407L);
        bar.setShortSale(23277L);
        bar.setShortStockRedemption(1999L);
        bar.setShortPrevBalance(215077L);
        bar.setShortBalance(204948L);

        assertThat(bar.getMarginPurchase()).isEqualTo(526296L);
        assertThat(bar.getMarginSale()).isEqualTo(485038L);
        assertThat(bar.getMarginCashRedemption()).isEqualTo(6678L);
        assertThat(bar.getMarginPrevBalance()).isEqualTo(8074444L);
        assertThat(bar.getMarginBalance()).isEqualTo(8109024L);
        assertThat(bar.getShortCovering()).isEqualTo(31407L);
        assertThat(bar.getShortSale()).isEqualTo(23277L);
        assertThat(bar.getShortStockRedemption()).isEqualTo(1999L);
        assertThat(bar.getShortPrevBalance()).isEqualTo(215077L);
        assertThat(bar.getShortBalance()).isEqualTo(204948L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -pl eagleeye-domain -Dtest=MarginDailyBarTest -q 2>&1 | tail -10
```

Expected: FAIL — `MarginDailyBar` does not exist.

- [ ] **Step 3: Implement `MarginDailyBar`**

Create `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/MarginDailyBar.java`:

```java
package com.eagleeye.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "margin_daily_bar",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_margin_daily_bar_trade_date",
        columnNames = {"trade_date"}
    )
)
public class MarginDailyBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    // Margin (融資) — trading units (lots/張)
    @Column(name = "margin_purchase")      private Long marginPurchase;
    @Column(name = "margin_sale")          private Long marginSale;
    @Column(name = "margin_cash_redemption") private Long marginCashRedemption;
    @Column(name = "margin_prev_balance")  private Long marginPrevBalance;
    @Column(name = "margin_balance")       private Long marginBalance;

    // Short (融券) — trading units (lots/張)
    @Column(name = "short_covering")           private Long shortCovering;
    @Column(name = "short_sale")               private Long shortSale;
    @Column(name = "short_stock_redemption")   private Long shortStockRedemption;
    @Column(name = "short_prev_balance")       private Long shortPrevBalance;
    @Column(name = "short_balance")            private Long shortBalance;

    protected MarginDailyBar() {}

    public MarginDailyBar(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Long getId()                   { return id; }
    public LocalDate getTradeDate()       { return tradeDate; }
    public Long getMarginPurchase()       { return marginPurchase; }
    public Long getMarginSale()           { return marginSale; }
    public Long getMarginCashRedemption() { return marginCashRedemption; }
    public Long getMarginPrevBalance()    { return marginPrevBalance; }
    public Long getMarginBalance()        { return marginBalance; }
    public Long getShortCovering()        { return shortCovering; }
    public Long getShortSale()            { return shortSale; }
    public Long getShortStockRedemption() { return shortStockRedemption; }
    public Long getShortPrevBalance()     { return shortPrevBalance; }
    public Long getShortBalance()         { return shortBalance; }

    public void setMarginPurchase(Long v)       { this.marginPurchase = v; }
    public void setMarginSale(Long v)           { this.marginSale = v; }
    public void setMarginCashRedemption(Long v) { this.marginCashRedemption = v; }
    public void setMarginPrevBalance(Long v)    { this.marginPrevBalance = v; }
    public void setMarginBalance(Long v)        { this.marginBalance = v; }
    public void setShortCovering(Long v)        { this.shortCovering = v; }
    public void setShortSale(Long v)            { this.shortSale = v; }
    public void setShortStockRedemption(Long v) { this.shortStockRedemption = v; }
    public void setShortPrevBalance(Long v)     { this.shortPrevBalance = v; }
    public void setShortBalance(Long v)         { this.shortBalance = v; }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -pl eagleeye-domain -Dtest=MarginDailyBarTest -q 2>&1 | tail -5
```

Expected: `Tests run: 2, Failures: 0`

### Task 2: `MarginDailyBarRepository`

**Files:**
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/MarginDailyBarRepository.java`

- [ ] **Step 5: Implement the repository interface**

```java
package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.MarginDailyBar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarginDailyBarRepository extends JpaRepository<MarginDailyBar, Long> {

    Optional<MarginDailyBar> findByTradeDate(LocalDate tradeDate);

    List<MarginDailyBar> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
}
```

- [ ] **Step 6: Commit domain layer**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/entity/MarginDailyBar.java \
        eagleeye-domain/src/main/java/com/eagleeye/domain/repository/MarginDailyBarRepository.java \
        eagleeye-domain/src/test/java/com/eagleeye/domain/entity/MarginDailyBarTest.java
git commit -m "feat(domain): add MarginDailyBar entity and repository"
```

---

## Chunk 2: Parser and HTTP client

### Task 3: `TwseParser.parseMargin`

**Files:**
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseParser.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/twse/TwseParserTest.java`

- [ ] **Step 7: Add failing tests to `TwseParserTest`**

Add these tests to the existing `TwseParserTest` class (do not replace existing tests):

```java
// ── parseMargin ─────────────────────────────────────────────────────────────

private static final String MARGIN_JSON = """
        {
          "stat": "OK",
          "data": [
            ["Margin Purchase (Trading unit)", "526,296", "485,038", "6,678", "8,074,444", "8,109,024"],
            ["Short Sale (Trading unit)", "31,407", "23,277", "1,999", "215,077", "204,948"]
          ]
        }
        """;

@Test
void parseMargin_validJson_returnsBarWithAllFields() {
    LocalDate date = LocalDate.of(2026, 3, 18);
    MarginDailyBar bar = parser.parseMargin(MARGIN_JSON, date);

    assertThat(bar).isNotNull();
    assertThat(bar.getTradeDate()).isEqualTo(date);
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

@Test
void parseMargin_statNotOk_returnsNull() {
    String json = """{"stat":"NO DATA","data":[]}""";
    assertThat(parser.parseMargin(json, LocalDate.of(2026, 3, 18))).isNull();
}

@Test
void parseMargin_missingRows_returnsNull() {
    String json = """{"stat":"OK","data":[["only one row","1","2","3","4","5"]]}""";
    assertThat(parser.parseMargin(json, LocalDate.of(2026, 3, 18))).isNull();
}

@Test
void parseMargin_invalidJson_returnsNull() {
    assertThat(parser.parseMargin("not-json", LocalDate.of(2026, 3, 18))).isNull();
}
```

Also add this import to `TwseParserTest`:
```java
import com.eagleeye.domain.entity.MarginDailyBar;
```

- [ ] **Step 8: Run tests to verify they fail**

```bash
mvn test -pl eagleeye-collector -Dtest=TwseParserTest -q 2>&1 | tail -10
```

Expected: FAIL — `parseMargin` method does not exist.

- [ ] **Step 9: Implement `parseMargin` in `TwseParser`**

Add to `TwseParser.java` — add import at top:
```java
import com.eagleeye.domain.entity.MarginDailyBar;
```

Add the method (after `parseVolumeByDate`):

```java
/**
 * Parses MI_MARGN market-wide margin transaction JSON for a single date.
 *
 * Row 0 = Margin Purchase (融資): cols [1-5] → marginPurchase, marginSale,
 *   marginCashRedemption, marginPrevBalance, marginBalance
 * Row 1 = Short Sale (融券): cols [1-5] → shortCovering, shortSale,
 *   shortStockRedemption, shortPrevBalance, shortBalance
 *
 * All values are plain trading units (lots/張) — no fixed-point conversion.
 */
public MarginDailyBar parseMargin(String json, LocalDate date) {
    JsonNode root;
    try {
        root = objectMapper.readTree(json);
    } catch (Exception e) {
        log.warn("Failed to parse margin JSON: {}", truncate(json));
        return null;
    }

    if (!"OK".equals(root.path("stat").asText(""))) {
        log.info("Margin stat='{}' — no data for {}", root.path("stat").asText(""), date);
        return null;
    }

    JsonNode data = root.path("data");
    if (!data.isArray() || data.size() < 2) {
        log.info("Margin data missing or incomplete for {}", date);
        return null;
    }

    try {
        JsonNode marginRow = data.get(0);
        JsonNode shortRow  = data.get(1);

        MarginDailyBar bar = new MarginDailyBar(date);
        bar.setMarginPurchase(toLong(marginRow.get(1).asText()));
        bar.setMarginSale(toLong(marginRow.get(2).asText()));
        bar.setMarginCashRedemption(toLong(marginRow.get(3).asText()));
        bar.setMarginPrevBalance(toLong(marginRow.get(4).asText()));
        bar.setMarginBalance(toLong(marginRow.get(5).asText()));
        bar.setShortCovering(toLong(shortRow.get(1).asText()));
        bar.setShortSale(toLong(shortRow.get(2).asText()));
        bar.setShortStockRedemption(toLong(shortRow.get(3).asText()));
        bar.setShortPrevBalance(toLong(shortRow.get(4).asText()));
        bar.setShortBalance(toLong(shortRow.get(5).asText()));
        return bar;
    } catch (Exception e) {
        log.warn("Failed to parse margin data rows for {}: {}", date, e.getMessage());
        return null;
    }
}
```

- [ ] **Step 10: Run tests to verify they pass**

```bash
mvn test -pl eagleeye-collector -Dtest=TwseParserTest -q 2>&1 | tail -5
```

Expected: all tests pass.

### Task 4: `TwseClient.fetchMarginJson`

**Files:**
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseClient.java`

> `TwseClient` is a thin HTTP wrapper with no unit tests (consistent with project convention). Just implement and verify compilation.

- [ ] **Step 11: Add `fetchMarginJson` to `TwseClient`**

Add constant and method. First add the import at the top of `TwseClient.java`:
```java
import java.time.LocalDate;
```

Add to the constants block (after `MARKET_STATS_PATH`):
```java
private static final String MARGIN_PATH = "/rwd/en/marginTrading/MI_MARGN";
```

Add the method (after `fetchMarketStatsJson`):
```java
public String fetchMarginJson(LocalDate date) {
    String queryDate = date.format(DATE_FORMAT);
    return restClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path(MARGIN_PATH)
                    .queryParam("date", queryDate)
                    .queryParam("selectType", "MS")
                    .queryParam("response", "json")
                    .build())
            .retrieve()
            .body(String.class);
}
```

- [ ] **Step 12: Compile to verify**

```bash
mvn compile -pl eagleeye-collector -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 13: Commit parser + client**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseParser.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseClient.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/twse/TwseParserTest.java
git commit -m "feat(collector): add margin transaction parsing and fetch to TwseParser/TwseClient"
```

---

## Chunk 3: Service and scheduler

### Task 5: `MarginCollectionResult`

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarginCollectionResult.java`

- [ ] **Step 14: Implement `MarginCollectionResult`**

```java
package com.eagleeye.collector.service;

import java.time.LocalDate;

/**
 * Result of a margin transaction daily collection operation.
 * Uses LocalDate (not YearMonth) — MI_MARGN API is per-day.
 */
public record MarginCollectionResult(
        LocalDate tradeDate,
        Status status,
        String errorMessage
) {
    public enum Status { COLLECTED, NO_DATA, ERROR }

    public static MarginCollectionResult collected(LocalDate date) {
        return new MarginCollectionResult(date, Status.COLLECTED, null);
    }

    public static MarginCollectionResult noData(LocalDate date) {
        return new MarginCollectionResult(date, Status.NO_DATA, null);
    }

    public static MarginCollectionResult error(LocalDate date, String message) {
        return new MarginCollectionResult(date, Status.ERROR, message);
    }
}
```

### Task 6: `MarginTransactionService`

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarginTransactionService.java`
- Create: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarginTransactionServiceTest.java`

- [ ] **Step 15: Write the failing tests**

Create `eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarginTransactionServiceTest.java`:

```java
package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
import com.eagleeye.domain.entity.MarginDailyBar;
import com.eagleeye.domain.repository.MarginDailyBarRepository;
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
class MarginTransactionServiceTest {

    @Mock private TwseClient twseClient;
    @Mock private TwseParser twseParser;
    @Mock private MarginDailyBarRepository repository;

    private MarginTransactionService service;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 18);
    private static final String MARGIN_JSON = """{"stat":"OK","data":[["row0","1","2","3","4","5"],["row1","6","7","8","9","10"]]}""";

    @BeforeEach
    void setUp() {
        service = new MarginTransactionService(twseClient, twseParser, repository);
    }

    @Test
    void collectDate_success_savesAndReturnsCollected() {
        MarginDailyBar bar = new MarginDailyBar(DATE);
        when(twseClient.fetchMarginJson(DATE)).thenReturn(MARGIN_JSON);
        when(twseParser.parseMargin(MARGIN_JSON, DATE)).thenReturn(bar);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.empty());

        MarginCollectionResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(MarginCollectionResult.Status.COLLECTED);
        assertThat(result.tradeDate()).isEqualTo(DATE);
        verify(repository).save(any(MarginDailyBar.class));
    }

    @Test
    void collectDate_noData_returnsNoData() {
        when(twseClient.fetchMarginJson(DATE)).thenReturn("""{"stat":"NO DATA"}""");
        when(twseParser.parseMargin(any(), eq(DATE))).thenReturn(null);

        MarginCollectionResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(MarginCollectionResult.Status.NO_DATA);
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_clientThrows_returnsError() {
        when(twseClient.fetchMarginJson(DATE)).thenThrow(new RuntimeException("timeout"));

        MarginCollectionResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(MarginCollectionResult.Status.ERROR);
        assertThat(result.errorMessage()).contains("timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_existingBar_upserts() {
        MarginDailyBar existing = new MarginDailyBar(DATE);
        MarginDailyBar parsed = new MarginDailyBar(DATE);
        parsed.setMarginBalance(8_109_024L);
        parsed.setShortBalance(204_948L);

        when(twseClient.fetchMarginJson(DATE)).thenReturn(MARGIN_JSON);
        when(twseParser.parseMargin(MARGIN_JSON, DATE)).thenReturn(parsed);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.of(existing));

        service.collectDate(DATE);

        ArgumentCaptor<MarginDailyBar> captor = ArgumentCaptor.forClass(MarginDailyBar.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMarginBalance()).isEqualTo(8_109_024L);
        assertThat(captor.getValue().getShortBalance()).isEqualTo(204_948L);
    }
}
```

- [ ] **Step 16: Run tests to verify they fail**

```bash
mvn test -pl eagleeye-collector -am -Dtest=MarginTransactionServiceTest -q 2>&1 | tail -10
```

Expected: FAIL — `MarginTransactionService` does not exist.

- [ ] **Step 17: Implement `MarginTransactionService`**

Create `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarginTransactionService.java`:

```java
package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
import com.eagleeye.domain.entity.MarginDailyBar;
import com.eagleeye.domain.repository.MarginDailyBarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class MarginTransactionService {

    private static final Logger log = LoggerFactory.getLogger(MarginTransactionService.class);

    private final TwseClient twseClient;
    private final TwseParser twseParser;
    private final MarginDailyBarRepository repository;

    public MarginTransactionService(TwseClient twseClient,
                                    TwseParser twseParser,
                                    MarginDailyBarRepository repository) {
        this.twseClient = twseClient;
        this.twseParser = twseParser;
        this.repository = repository;
    }

    @Transactional
    public MarginCollectionResult collectDate(LocalDate date) {
        try {
            String json   = twseClient.fetchMarginJson(date);
            MarginDailyBar parsed = twseParser.parseMargin(json, date);
            if (parsed == null) {
                log.info("No margin data for {}", date);
                return MarginCollectionResult.noData(date);
            }
            upsert(parsed);
            log.info("Collected margin data for {}", date);
            return MarginCollectionResult.collected(date);
        } catch (Exception e) {
            log.error("Margin collection failed for {}: {}", date, e.getMessage(), e);
            return MarginCollectionResult.error(date, e.getMessage());
        }
    }

    private void upsert(MarginDailyBar parsed) {
        MarginDailyBar bar = repository
                .findByTradeDate(parsed.getTradeDate())
                .orElseGet(() -> new MarginDailyBar(parsed.getTradeDate()));

        bar.setMarginPurchase(parsed.getMarginPurchase());
        bar.setMarginSale(parsed.getMarginSale());
        bar.setMarginCashRedemption(parsed.getMarginCashRedemption());
        bar.setMarginPrevBalance(parsed.getMarginPrevBalance());
        bar.setMarginBalance(parsed.getMarginBalance());
        bar.setShortCovering(parsed.getShortCovering());
        bar.setShortSale(parsed.getShortSale());
        bar.setShortStockRedemption(parsed.getShortStockRedemption());
        bar.setShortPrevBalance(parsed.getShortPrevBalance());
        bar.setShortBalance(parsed.getShortBalance());

        repository.save(bar);
    }
}
```

- [ ] **Step 18: Run tests to verify they pass**

```bash
mvn test -pl eagleeye-collector -am -Dtest=MarginTransactionServiceTest -q 2>&1 | tail -5
```

Expected: `Tests run: 4, Failures: 0`

### Task 7: `MarginTransactionScheduler`

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/scheduler/MarginTransactionScheduler.java`

> Scheduler is a thin `@Scheduled` wrapper — no unit test (consistent with `CollectionScheduler` and `MarketIndexScheduler`).

- [ ] **Step 19: Implement `MarginTransactionScheduler`**

```java
package com.eagleeye.collector.scheduler;

import com.eagleeye.collector.service.MarginCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Triggers daily margin transaction collection at 15:30 Taipei time.
 *
 * TWSE publishes margin transaction data after the 13:30 market close.
 * 15:30 provides a safe 2-hour buffer.
 *
 * Does NOT modify CollectionScheduler or MarketIndexScheduler — independent bean.
 */
@Component
public class MarginTransactionScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarginTransactionScheduler.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final MarginTransactionService marginTransactionService;

    public MarginTransactionScheduler(MarginTransactionService marginTransactionService) {
        this.marginTransactionService = marginTransactionService;
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Taipei")
    public void collectMargin() {
        LocalDate today = LocalDate.now(TAIPEI);
        log.info("=== Margin transaction daily collection triggered for {} ===", today);
        try {
            MarginCollectionResult result = marginTransactionService.collectDate(today);
            log.info("=== Margin collection completed: {} for {} ===", result.status(), today);
        } catch (Exception e) {
            log.error("Margin daily collection failed for {}: {}", today, e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 20: Commit service layer**

```bash
git add \
  eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarginCollectionResult.java \
  eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarginTransactionService.java \
  eagleeye-collector/src/main/java/com/eagleeye/collector/scheduler/MarginTransactionScheduler.java \
  eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarginTransactionServiceTest.java
git commit -m "feat(collector): add MarginTransactionService and daily scheduler"
```

---

## Chunk 4: CombinedBackfillRunner update

### Task 8: Add margin to `CombinedBackfillRunner`

**Files:**
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CombinedBackfillRunner.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/runner/CombinedBackfillRunnerTest.java`

- [ ] **Step 21: Write failing test — verify margin called per weekday**

Add to `CombinedBackfillRunnerTest` (do not replace existing tests):

```java
@Mock private MarginTransactionService marginTransactionService;
```

Update `setUp()` to use the new 5-arg constructor (to be implemented):
```java
@BeforeEach
void setUp() {
    runner = new CombinedBackfillRunner(marketIndexService, collectionService, null, marginTransactionService, 0);
}
```

Add a `stubMargin()` helper and two new tests:
```java
@Test
void executeBackfill_collectsMarginForEachWeekday() throws Exception {
    // 2026-03-03 (Tue) to 2026-03-06 (Fri) = 4 weekdays
    stubMarketIndex(YearMonth.of(2026, 3));
    stubTaifex();
    stubMargin();

    runner.executeBackfill(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 6));

    verify(marginTransactionService, times(4)).collectDate(any(LocalDate.class));
    verify(marginTransactionService).collectDate(LocalDate.of(2026, 3, 3));
    verify(marginTransactionService).collectDate(LocalDate.of(2026, 3, 4));
    verify(marginTransactionService).collectDate(LocalDate.of(2026, 3, 5));
    verify(marginTransactionService).collectDate(LocalDate.of(2026, 3, 6));
}

@Test
void executeBackfill_skipsMarginOnWeekends() throws Exception {
    stubMarketIndex(YearMonth.of(2026, 3));
    stubTaifex();
    stubMargin();

    runner.executeBackfill(LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 9));

    verify(marginTransactionService, times(2)).collectDate(any(LocalDate.class));
    verify(marginTransactionService, never()).collectDate(LocalDate.of(2026, 3, 7)); // Sat
    verify(marginTransactionService, never()).collectDate(LocalDate.of(2026, 3, 8)); // Sun
}

private void stubMargin() {
    when(marginTransactionService.collectDate(any(LocalDate.class)))
            .thenReturn(MarginCollectionResult.collected(LocalDate.now()));
}
```

Also add import:
```java
import com.eagleeye.collector.service.MarginCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
```

- [ ] **Step 22: Run tests to verify they fail**

```bash
mvn test -pl eagleeye-collector -am -Dtest=CombinedBackfillRunnerTest -q 2>&1 | tail -10
```

Expected: FAIL — constructor mismatch and `MarginTransactionService` not injected.

- [ ] **Step 23: Update `CombinedBackfillRunner`**

Add field and update constructors. In `CombinedBackfillRunner.java`:

Add import:
```java
import com.eagleeye.collector.service.MarginCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
```

Add field (after `collectionService`):
```java
private final MarginTransactionService marginTransactionService;
```

Replace the two constructors:
```java
@Autowired
public CombinedBackfillRunner(MarketIndexService marketIndexService,
                              CollectionService collectionService,
                              ApplicationContext applicationContext,
                              MarginTransactionService marginTransactionService) {
    this(marketIndexService, collectionService, applicationContext, marginTransactionService, 500);
}

CombinedBackfillRunner(MarketIndexService marketIndexService,
                       CollectionService collectionService,
                       ApplicationContext applicationContext,
                       MarginTransactionService marginTransactionService,
                       long requestDelayMs) {
    this.marketIndexService = marketIndexService;
    this.collectionService = collectionService;
    this.applicationContext = applicationContext;
    this.marginTransactionService = marginTransactionService;
    this.requestDelayMs = requestDelayMs;
}
```

In `executeBackfill`, add margin call alongside TAIFEX (inside the weekday block):
```java
if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
    CollectionResult result = collectionService.collectAll(day);
    printTaifex(day, result);
    Thread.sleep(requestDelayMs);

    MarginCollectionResult marginResult = marginTransactionService.collectDate(day);
    printMargin(day, marginResult);
    Thread.sleep(requestDelayMs);
}
```

Add print helper after `printTaifex`:
```java
private void printMargin(LocalDate date, MarginCollectionResult r) {
    String status = switch (r.status()) {
        case COLLECTED -> "collected";
        case NO_DATA   -> "no data";
        case ERROR     -> "ERROR: " + r.errorMessage();
    };
    System.out.printf("  [MARGIN]  %-12s  %s%n", date, status);
}
```

- [ ] **Step 24: Run all collector tests to verify they pass**

```bash
mvn test -pl eagleeye-collector -am -q 2>&1 | tail -10
```

Expected: all tests pass, 0 failures.

- [ ] **Step 25: Commit backfill runner update**

```bash
git add \
  eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CombinedBackfillRunner.java \
  eagleeye-collector/src/test/java/com/eagleeye/collector/runner/CombinedBackfillRunnerTest.java
git commit -m "feat(collector): add margin transaction to CombinedBackfillRunner"
```

---

## Chunk 5: Shell layer

### Task 9: `TableFormatter.formatMarginTransaction`

**Files:**
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/formatter/TableFormatter.java`
- Create: `eagleeye-shell/src/test/java/com/eagleeye/shell/formatter/MarginTransactionFormatterTest.java`

- [ ] **Step 26: Write failing formatter tests**

Create `eagleeye-shell/src/test/java/com/eagleeye/shell/formatter/MarginTransactionFormatterTest.java`:

```java
package com.eagleeye.shell.formatter;

import com.eagleeye.domain.entity.MarginDailyBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarginTransactionFormatterTest {

    private TableFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new TableFormatter();
    }

    private MarginDailyBar bar(String date) {
        MarginDailyBar b = new MarginDailyBar(LocalDate.parse(date));
        b.setMarginPurchase(526_296L);
        b.setMarginSale(485_038L);
        b.setMarginCashRedemption(6_678L);
        b.setMarginPrevBalance(8_074_444L);
        b.setMarginBalance(8_109_024L);
        b.setShortCovering(31_407L);
        b.setShortSale(23_277L);
        b.setShortStockRedemption(1_999L);
        b.setShortPrevBalance(215_077L);
        b.setShortBalance(204_948L);
        return b;
    }

    @Test
    void formatMarginTransaction_emptyList_returnsNoData() {
        assertThat(formatter.formatMarginTransaction(List.of())).isEqualTo("No data found.");
    }

    @Test
    void formatMarginTransaction_containsExpectedHeaders() {
        String result = formatter.formatMarginTransaction(List.of(bar("2026-03-18")));
        assertThat(result).contains("Date");
        assertThat(result).contains("M-Buy");
        assertThat(result).contains("M-Sell");
        assertThat(result).contains("M-Bal");
        assertThat(result).contains("S-Cover");
        assertThat(result).contains("S-Sell");
        assertThat(result).contains("S-Bal");
    }

    @Test
    void formatMarginTransaction_singleBar_containsDate() {
        assertThat(formatter.formatMarginTransaction(List.of(bar("2026-03-18"))))
                .contains("2026-03-18");
    }

    @Test
    void formatMarginTransaction_singleBar_containsFormattedNumbers() {
        String result = formatter.formatMarginTransaction(List.of(bar("2026-03-18")));
        assertThat(result).contains("526,296");   // marginPurchase
        assertThat(result).contains("8,109,024"); // marginBalance
        assertThat(result).contains("204,948");   // shortBalance
    }

    @Test
    void formatMarginTransaction_nullFields_renderedAsDash() {
        MarginDailyBar b = new MarginDailyBar(LocalDate.parse("2026-03-18"));
        // leave all fields null
        String result = formatter.formatMarginTransaction(List.of(b));
        assertThat(result).contains("-");
    }

    @Test
    void formatMarginTransaction_multipleBars_allDatesPresent() {
        List<MarginDailyBar> bars = List.of(bar("2026-03-17"), bar("2026-03-18"));
        String result = formatter.formatMarginTransaction(bars);
        assertThat(result).contains("2026-03-17");
        assertThat(result).contains("2026-03-18");
    }
}
```

- [ ] **Step 27: Run tests to verify they fail**

```bash
mvn test -pl eagleeye-shell -am -Dtest=MarginTransactionFormatterTest -q 2>&1 | tail -10
```

Expected: FAIL — `formatMarginTransaction` does not exist.

- [ ] **Step 28: Implement `formatMarginTransaction` in `TableFormatter`**

Add import at top of `TableFormatter.java`:
```java
import com.eagleeye.domain.entity.MarginDailyBar;
```

Add constant after `W_TURNOVER`:
```java
private static final int W_MARGIN    = 11;  // e.g., "8,109,024" (9 chars)
```

Add method after `formatMarketIndex`:
```java
/** Taiwan market-wide margin transaction daily summary. Columns: Date + 8 numeric. */
public String formatMarginTransaction(List<MarginDailyBar> bars) {
    if (bars.isEmpty()) return "No data found.";

    int[] widths  = {W_DATE, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN, W_MARGIN};
    String[] headers = {"Date", "M-Buy", "M-Sell", "M-Redeem", "M-Bal", "S-Cover", "S-Sell", "S-Redeem", "S-Bal"};

    List<Row> rows = new ArrayList<>();
    for (MarginDailyBar b : bars) {
        rows.add(Row.data(
                b.getTradeDate().toString(),
                fmtVol(b.getMarginPurchase()),
                fmtVol(b.getMarginSale()),
                fmtVol(b.getMarginCashRedemption()),
                fmtVol(b.getMarginBalance()),
                fmtVol(b.getShortCovering()),
                fmtVol(b.getShortSale()),
                fmtVol(b.getShortStockRedemption()),
                fmtVol(b.getShortBalance())
        ));
    }
    return renderTable(headers, widths, rows);
}
```

> Note: `marginPrevBalance` and `shortPrevBalance` are stored but not displayed (derivable from adjacent rows). Display focuses on the 7 most useful fields.

- [ ] **Step 29: Run tests to verify they pass**

```bash
mvn test -pl eagleeye-shell -am -Dtest=MarginTransactionFormatterTest -q 2>&1 | tail -5
```

Expected: `Tests run: 6, Failures: 0`

### Task 10: `MarginTransactionCommands`

**Files:**
- Create: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarginTransactionCommands.java`
- Create: `eagleeye-shell/src/test/java/com/eagleeye/shell/commands/MarginTransactionCommandsTest.java`

- [ ] **Step 30: Write failing command tests**

Create `eagleeye-shell/src/test/java/com/eagleeye/shell/commands/MarginTransactionCommandsTest.java`:

```java
package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.MarginCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.domain.entity.MarginDailyBar;
import com.eagleeye.domain.repository.MarginDailyBarRepository;
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
class MarginTransactionCommandsTest {

    @Mock private MarginTransactionService marginTransactionService;
    @Mock private MarginDailyBarRepository repository;
    @Mock private TableFormatter formatter;

    private MarginTransactionCommands commands;

    @BeforeEach
    void setUp() {
        commands = new MarginTransactionCommands(marginTransactionService, repository, formatter);
    }

    // ── margin list ──────────────────────────────────────────────────────────

    @Test
    void list_defaultsToToday() {
        when(repository.findByTradeDate(LocalDate.now())).thenReturn(Optional.empty());
        commands.list("");
        verify(repository).findByTradeDate(LocalDate.now());
    }

    @Test
    void list_parsesExplicitDate() {
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 18))).thenReturn(Optional.empty());
        commands.list("2026-03-18");
        verify(repository).findByTradeDate(LocalDate.of(2026, 3, 18));
    }

    @Test
    void list_barFound_returnsFormattedTable() {
        MarginDailyBar bar = new MarginDailyBar(LocalDate.of(2026, 3, 18));
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 18))).thenReturn(Optional.of(bar));
        when(formatter.formatMarginTransaction(List.of(bar))).thenReturn("rendered");

        String result = commands.list("2026-03-18");
        assertThat(result).contains("rendered");
    }

    @Test
    void list_noBarFound_returnsNoData() {
        when(repository.findByTradeDate(any())).thenReturn(Optional.empty());
        String result = commands.list("2026-03-18");
        assertThat(result).containsIgnoringCase("no data");
    }

    // ── margin show ──────────────────────────────────────────────────────────

    @Test
    void show_defaultRange_queriesLast30Days() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of());
        when(formatter.formatMarginTransaction(any())).thenReturn("table");

        commands.show("", "");

        LocalDate today = LocalDate.now();
        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(today.minusDays(30), today);
    }

    @Test
    void show_explicitRange_queriesGivenRange() {
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of());
        when(formatter.formatMarginTransaction(any())).thenReturn("table");

        commands.show("2026-01-01", "2026-03-18");

        verify(repository).findByTradeDateBetweenOrderByTradeDateAsc(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 18));
    }

    @Test
    void show_returnsFormattedTableWithRowCount() {
        MarginDailyBar bar = new MarginDailyBar(LocalDate.of(2026, 3, 18));
        when(repository.findByTradeDateBetweenOrderByTradeDateAsc(any(), any())).thenReturn(List.of(bar));
        when(formatter.formatMarginTransaction(List.of(bar))).thenReturn("rendered-table");

        String result = commands.show("2026-03-01", "2026-03-18");
        assertThat(result).contains("rendered-table");
        assertThat(result).contains("1"); // row count
    }

    // ── margin collect ───────────────────────────────────────────────────────

    @Test
    void collect_defaultsToToday() {
        when(marginTransactionService.collectDate(any()))
                .thenReturn(MarginCollectionResult.collected(LocalDate.now()));

        commands.collect("");

        verify(marginTransactionService).collectDate(LocalDate.now());
    }
}
```

- [ ] **Step 31: Run tests to verify they fail**

```bash
mvn test -pl eagleeye-shell -am -Dtest=MarginTransactionCommandsTest -q 2>&1 | tail -10
```

Expected: FAIL — `MarginTransactionCommands` does not exist.

- [ ] **Step 32: Implement `MarginTransactionCommands`**

Create `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarginTransactionCommands.java`:

```java
package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.MarginCollectionResult;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.domain.entity.MarginDailyBar;
import com.eagleeye.domain.repository.MarginDailyBarRepository;
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
public class MarginTransactionCommands {

    private final MarginTransactionService marginTransactionService;
    private final MarginDailyBarRepository repository;
    private final TableFormatter formatter;

    @Autowired
    public MarginTransactionCommands(MarginTransactionService marginTransactionService,
                                     MarginDailyBarRepository repository,
                                     TableFormatter formatter) {
        this.marginTransactionService = marginTransactionService;
        this.repository = repository;
        this.formatter = formatter;
    }

    @Command(name = "margin list", description = "Show margin transaction data for a single date")
    public String list(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        Optional<MarginDailyBar> bar = repository.findByTradeDate(d);
        if (bar.isEmpty()) return "No data for " + d;
        return formatter.formatMarginTransaction(List.of(bar.get()));
    }

    @Command(name = "margin show", description = "Show margin transaction data over a date range")
    public String show(
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 30 days ago)", defaultValue = "") String from,
            @Option(longName = "to",   description = "End date YYYY-MM-DD (default: today)",         defaultValue = "") String to) {
        LocalDate toDate   = (to   == null || to.isEmpty())   ? LocalDate.now()        : LocalDate.parse(to);
        LocalDate fromDate = (from == null || from.isEmpty()) ? toDate.minusDays(30)   : LocalDate.parse(from);
        List<MarginDailyBar> bars = repository.findByTradeDateBetweenOrderByTradeDateAsc(fromDate, toDate);
        return "Margin \u2014 " + fromDate + " \u2192 " + toDate + " (" + bars.size() + " bars)\n"
                + formatter.formatMarginTransaction(bars);
    }

    @Command(name = "margin collect", description = "Collect margin transaction data for a date")
    public String collect(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        return formatResult(marginTransactionService.collectDate(d));
    }

    @Command(name = "margin backfill", description = "Backfill margin transaction data for a date range")
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
                sb.append(formatResult(marginTransactionService.collectDate(current))).append("\n");
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            current = current.plusDays(1);
        }
        return sb.toString().stripTrailing();
    }

    private String formatResult(MarginCollectionResult r) {
        return switch (r.status()) {
            case COLLECTED -> r.tradeDate() + " \u2014 collected";
            case NO_DATA   -> r.tradeDate() + " \u2014 no data";
            case ERROR     -> r.tradeDate() + " \u2014 ERROR: " + r.errorMessage();
        };
    }
}
```

- [ ] **Step 33: Run all shell tests to verify they pass**

```bash
mvn test -pl eagleeye-shell -am -q 2>&1 | tail -10
```

Expected: all tests pass, 0 failures.

- [ ] **Step 34: Run full build to verify everything compiles and passes**

```bash
mvn test -pl eagleeye-domain,eagleeye-collector,eagleeye-shell -am -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 0 failures.

- [ ] **Step 35: Commit shell layer**

```bash
git add \
  eagleeye-shell/src/main/java/com/eagleeye/shell/formatter/TableFormatter.java \
  eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarginTransactionCommands.java \
  eagleeye-shell/src/test/java/com/eagleeye/shell/formatter/MarginTransactionFormatterTest.java \
  eagleeye-shell/src/test/java/com/eagleeye/shell/commands/MarginTransactionCommandsTest.java
git commit -m "feat(shell): add margin transaction commands and formatter"
```
