# TAIEX Daily Bar Collector Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add daily TAIEX index OHLC/volume collection from TWSE to eagleeye, with automatic scheduling and Shell CLI commands.

**Architecture:** Mirror the existing TAIFEX collector pattern — new `TwseClient` fetches JSON from TWSE by month, `TwseParser` parses it into `TaiexDailyBar` entities (with fixed-point encoding for fractional index values), `MarketIndexService` upserts them. A standalone `MarketIndexScheduler` triggers collection at 18:00 Taipei time. Shell commands and a backfill runner provide manual control.

**Tech Stack:** Spring Boot, Jackson (via `spring-boot-starter-web`), JPA/Hibernate, Spring Shell 4.0.1, JUnit 5, AssertJ, Mockito.

---

## File Map

| Action | File |
|---|---|
| **Create** | `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/TaiexDailyBar.java` |
| **Create** | `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/TaiexDailyBarRepository.java` |
| **Create** | `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseParser.java` |
| **Create** | `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseClient.java` |
| **Create** | `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexCollectionResult.java` |
| **Create** | `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexService.java` |
| **Create** | `eagleeye-collector/src/main/java/com/eagleeye/collector/scheduler/MarketIndexScheduler.java` |
| **Create** | `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/MarketIndexBackfillRunner.java` |
| **Create** | `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarketIndexCommands.java` |
| **Create** | `eagleeye-collector/src/test/java/com/eagleeye/collector/twse/TwseParserTest.java` |
| **Create** | `eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarketIndexServiceTest.java` |

No existing files are modified. The `DomainConfiguration` automatically scans `com.eagleeye.domain.entity` and `com.eagleeye.domain.repository` — no config changes needed.

---

## TWSE API Reference

**Endpoint:** `GET https://www.twse.com.tw/rwd/en/index/TAIEX?date=YYYYMMDD&response=json`

**Sample response:**
```json
{
  "stat": "OK",
  "date": "20260301",
  "fields": ["Date", "Open", "High", "Low", "Close", "Volume", "Turnover"],
  "data": [
    ["115/03/03", "20,234.56", "20,456.78", "20,100.23", "20,300.45", "3,456,789", "123,456,789,012"],
    ["115/03/04", "20,300.45", "20,512.34", "20,201.11", "20,488.22", "3,567,890", "124,567,890,123"]
  ]
}
```

- `stat`: `"OK"` for success; any other value means no data
- `data`: array of arrays; empty array `[]` when no trading days in month
- **Date format:** Republic of China calendar — `"YYY/MM/DD"` where year = ROC year + 1911 (e.g. `"115/03/03"` → 2026-03-03)
- **OHLC format:** string with comma separators and 2 decimal places (e.g. `"20,234.56"`)
- **Volume/Turnover:** string integer with comma separators (e.g. `"3,456,789"`)
- **Column order (positional, by index):** `[0]` Date, `[1]` Open, `[2]` High, `[3]` Low, `[4]` Close, `[5]` Volume, `[6]` Turnover

> ⚠️ **Validate before coding:** Run `curl "https://www.twse.com.tw/rwd/en/index/TAIEX?date=20260301&response=json"` and confirm the `fields` order and `data` format match the fixture above. Adjust the parser if the actual response differs.

---

## Chunk 1: Domain + Parser

### Task 1: TaiexDailyBar Entity

**Files:**
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/TaiexDailyBar.java`

No test needed — JPA entity with no business logic. Pattern mirrors `FuturesPosition.java`.

- [ ] **Step 1: Create the entity**

```java
package com.eagleeye.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "taiex_daily_bar",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_taiex_daily_bar_trade_date",
        columnNames = {"trade_date"}
    )
)
public class TaiexDailyBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    // OHLC stored as fixed-point integer (index points × 100)
    // e.g. 20234.56 is stored as 2023456
    @Column(name = "open")
    private Long open;

    @Column(name = "high")
    private Long high;

    @Column(name = "low")
    private Long low;

    @Column(name = "close")
    private Long close;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "turnover")
    private Long turnover;

    protected TaiexDailyBar() {}

    public TaiexDailyBar(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Long getId()             { return id; }
    public LocalDate getTradeDate() { return tradeDate; }
    public Long getOpen()           { return open; }
    public Long getHigh()           { return high; }
    public Long getLow()            { return low; }
    public Long getClose()          { return close; }
    public Long getVolume()         { return volume; }
    public Long getTurnover()       { return turnover; }

    public void setOpen(Long v)     { this.open = v; }
    public void setHigh(Long v)     { this.high = v; }
    public void setLow(Long v)      { this.low = v; }
    public void setClose(Long v)    { this.close = v; }
    public void setVolume(Long v)   { this.volume = v; }
    public void setTurnover(Long v) { this.turnover = v; }
}
```

- [ ] **Step 2: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/entity/TaiexDailyBar.java
git commit -m "feat(domain): add TaiexDailyBar entity"
```

---

### Task 2: TaiexDailyBarRepository

**Files:**
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/TaiexDailyBarRepository.java`

- [ ] **Step 1: Create the repository**

```java
package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.TaiexDailyBar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface TaiexDailyBarRepository extends JpaRepository<TaiexDailyBar, Long> {

    Optional<TaiexDailyBar> findByTradeDate(LocalDate tradeDate);
}
```

- [ ] **Step 2: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/repository/TaiexDailyBarRepository.java
git commit -m "feat(domain): add TaiexDailyBarRepository"
```

---

### Task 3: TwseParser (TDD)

**Files:**
- Create: `eagleeye-collector/src/test/java/com/eagleeye/collector/twse/TwseParserTest.java`
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseParser.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.TaiexDailyBar;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwseParserTest {

    private TwseParser parser;

    // Realistic TWSE JSON: two trading days in March 2026
    // Date format: ROC year (2026-1911=115) / month / day
    // OHLC: fractional, comma-separated
    // Volume/Turnover: integer, comma-separated
    private static final String VALID_JSON = """
            {
              "stat": "OK",
              "date": "20260301",
              "fields": ["Date","Open","High","Low","Close","Volume","Turnover"],
              "data": [
                ["115/03/03", "20,234.56", "20,456.78", "20,100.23", "20,300.45", "3,456,789", "123,456,789,012"],
                ["115/03/04", "20,300.45", "20,512.34", "20,201.11", "20,488.22", "3,567,890", "124,567,890,123"]
              ]
            }
            """;

    private static final String EMPTY_DATA_JSON = """
            {
              "stat": "OK",
              "date": "20260201",
              "fields": ["Date","Open","High","Low","Close","Volume","Turnover"],
              "data": []
            }
            """;

    private static final String NO_OK_STAT_JSON = """
            {
              "stat": "NO DATA",
              "date": "20260101",
              "data": []
            }
            """;

    @BeforeEach
    void setUp() {
        parser = new TwseParser(new ObjectMapper());
    }

    @Test
    void parse_validJson_returnsTwoBars() {
        List<TaiexDailyBar> bars = parser.parse(VALID_JSON);
        assertThat(bars).hasSize(2);
    }

    @Test
    void parse_validJson_firstBarHasCorrectDate() {
        List<TaiexDailyBar> bars = parser.parse(VALID_JSON);
        assertThat(bars.get(0).getTradeDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 3));
    }

    @Test
    void parse_validJson_firstBarHasCorrectOhlcAsFixedPoint() {
        List<TaiexDailyBar> bars = parser.parse(VALID_JSON);
        TaiexDailyBar bar = bars.get(0);
        // "20,234.56" → 2023456
        assertThat(bar.getOpen()).isEqualTo(2023456L);
        // "20,456.78" → 2045678
        assertThat(bar.getHigh()).isEqualTo(2045678L);
        // "20,100.23" → 2010023
        assertThat(bar.getLow()).isEqualTo(2010023L);
        // "20,300.45" → 2030045
        assertThat(bar.getClose()).isEqualTo(2030045L);
    }

    @Test
    void parse_validJson_firstBarHasCorrectVolumeAndTurnover() {
        List<TaiexDailyBar> bars = parser.parse(VALID_JSON);
        TaiexDailyBar bar = bars.get(0);
        assertThat(bar.getVolume()).isEqualTo(3456789L);
        assertThat(bar.getTurnover()).isEqualTo(123456789012L);
    }

    @Test
    void parse_emptyDataArray_returnsEmptyList() {
        List<TaiexDailyBar> bars = parser.parse(EMPTY_DATA_JSON);
        assertThat(bars).isEmpty();
    }

    @Test
    void parse_nonOkStat_returnsEmptyList() {
        List<TaiexDailyBar> bars = parser.parse(NO_OK_STAT_JSON);
        assertThat(bars).isEmpty();
    }

    @Test
    void parse_malformedJson_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> parser.parse("not valid json {{{"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid json");
    }
}
```

- [ ] **Step 2: Run to confirm all tests fail**

```bash
cd /Users/chrisliu/projects/eagleeye
mvn test -pl eagleeye-collector -Dtest=TwseParserTest -q 2>&1 | tail -20
```
Expected: compilation failure (`TwseParser` not found).

- [ ] **Step 3: Create the minimal TwseParser implementation**

```java
package com.eagleeye.collector.twse;

import com.eagleeye.domain.entity.TaiexDailyBar;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses TWSE TAIEX monthly JSON into TaiexDailyBar entities.
 *
 * Column order (positional):
 *   [0] Date (ROC calendar "YYY/MM/DD")
 *   [1] Open, [2] High, [3] Low, [4] Close  (fractional, comma-separated)
 *   [5] Volume (integer, comma-separated)
 *   [6] Turnover (integer, comma-separated)
 *
 * OHLC values are stored as fixed-point integers (×100):
 *   "20,234.56" → strip commas → BigDecimal("20234.56") × 100 → 2023456L
 */
@Component
public class TwseParser {

    private static final Logger log = LoggerFactory.getLogger(TwseParser.class);
    private final ObjectMapper objectMapper;

    public TwseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TaiexDailyBar> parse(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse TWSE JSON: " + truncate(json), e);
        }

        String stat = root.path("stat").asText("");
        if (!"OK".equals(stat)) {
            log.info("TWSE stat='{}' — no data", stat);
            return List.of();
        }

        JsonNode dataNode = root.path("data");
        if (!dataNode.isArray() || dataNode.isEmpty()) {
            log.info("TWSE data array is empty");
            return List.of();
        }

        List<TaiexDailyBar> bars = new ArrayList<>();
        for (JsonNode row : dataNode) {
            TaiexDailyBar bar = parseRow(row);
            if (bar != null) bars.add(bar);
        }

        log.info("Parsed {} TAIEX bars", bars.size());
        return bars;
    }

    private TaiexDailyBar parseRow(JsonNode row) {
        try {
            LocalDate tradeDate = parseRocDate(row.get(0).asText());
            TaiexDailyBar bar = new TaiexDailyBar(tradeDate);
            bar.setOpen(toFixedPoint(row.get(1).asText()));
            bar.setHigh(toFixedPoint(row.get(2).asText()));
            bar.setLow(toFixedPoint(row.get(3).asText()));
            bar.setClose(toFixedPoint(row.get(4).asText()));
            bar.setVolume(toLong(row.get(5).asText()));
            bar.setTurnover(toLong(row.get(6).asText()));
            return bar;
        } catch (Exception e) {
            log.warn("Skipping unparseable row {}: {}", row, e.getMessage());
            return null;
        }
    }

    /**
     * Parses Republic of China calendar date string "YYY/MM/DD" to LocalDate.
     * ROC year 115 = 2026 (115 + 1911 = 2026).
     */
    private LocalDate parseRocDate(String rocDate) {
        String[] parts = rocDate.split("/");
        int year = Integer.parseInt(parts[0]) + 1911;
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);
        return LocalDate.of(year, month, day);
    }

    /**
     * Converts a comma-formatted fractional string to a fixed-point Long (×100).
     * "20,234.56" → 2023456L
     */
    private long toFixedPoint(String value) {
        String clean = value.replace(",", "");
        return new BigDecimal(clean).multiply(new BigDecimal("100")).longValue();
    }

    /**
     * Converts a comma-formatted integer string to Long.
     * "3,456,789" → 3456789L
     */
    private long toLong(String value) {
        return Long.parseLong(value.replace(",", ""));
    }

    private String truncate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
mvn test -pl eagleeye-collector -Dtest=TwseParserTest -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`, all 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add eagleeye-collector/src/test/java/com/eagleeye/collector/twse/TwseParserTest.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseParser.java
git commit -m "feat(collector): add TwseParser with TDD"
```

---

### Task 4: TwseClient

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseClient.java`

No unit test — mirrors `TaifexClient` exactly (HTTP client with no testable business logic).

- [ ] **Step 1: Create TwseClient**

```java
package com.eagleeye.collector.twse;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * HTTP client for the TWSE TAIEX monthly index data API.
 *
 * Endpoint: GET https://www.twse.com.tw/rwd/en/index/TAIEX?date=YYYYMMDD&response=json
 *
 * The date parameter selects the month — TWSE returns all trading days in that month.
 * We always use the first day of the month as the query date.
 */
@Component
public class TwseClient {

    private static final String BASE_URL = "https://www.twse.com.tw";
    private static final String TAIEX_PATH = "/rwd/en/index/TAIEX";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;

    public TwseClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; EagleEye/1.0)")
                .build();
    }

    /**
     * Fetches the TAIEX JSON for the given month.
     * Uses the first day of the month as the query date.
     */
    public String fetchMonthJson(YearMonth yearMonth) {
        String queryDate = yearMonth.atDay(1).format(DATE_FORMAT);
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(TAIEX_PATH)
                        .queryParam("date", queryDate)
                        .queryParam("response", "json")
                        .build())
                .retrieve()
                .body(String.class);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/twse/TwseClient.java
git commit -m "feat(collector): add TwseClient for TWSE TAIEX API"
```

---

## Chunk 2: Service + Infrastructure

### Task 5: MarketIndexCollectionResult

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexCollectionResult.java`

No test — simple data record, no logic.

- [ ] **Step 1: Create the result record**

```java
package com.eagleeye.collector.service;

import java.time.YearMonth;

/**
 * Result of a TAIEX monthly collection operation.
 * Separate from CollectionResult (which is TAIFEX-specific with futuresCount/optionsCount).
 */
public record MarketIndexCollectionResult(
        YearMonth yearMonth,
        int barsCount,
        Status status,
        String errorMessage
) {
    public enum Status { COLLECTED, NO_DATA, ERROR }

    public static MarketIndexCollectionResult collected(YearMonth yearMonth, int barsCount) {
        return new MarketIndexCollectionResult(yearMonth, barsCount, Status.COLLECTED, null);
    }

    public static MarketIndexCollectionResult noData(YearMonth yearMonth) {
        return new MarketIndexCollectionResult(yearMonth, 0, Status.NO_DATA, null);
    }

    public static MarketIndexCollectionResult error(YearMonth yearMonth, String message) {
        return new MarketIndexCollectionResult(yearMonth, 0, Status.ERROR, message);
    }

    public boolean isTradeMonth() { return status == Status.COLLECTED; }
}
```

- [ ] **Step 2: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexCollectionResult.java
git commit -m "feat(collector): add MarketIndexCollectionResult record"
```

---

### Task 6: MarketIndexService (TDD)

**Files:**
- Create: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarketIndexServiceTest.java`
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexService.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
import com.eagleeye.domain.entity.TaiexDailyBar;
import com.eagleeye.domain.repository.TaiexDailyBarRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketIndexServiceTest {

    @Mock
    private TwseClient twseClient;

    @Mock
    private TaiexDailyBarRepository repository;

    private TwseParser realParser;
    private MarketIndexService service;

    private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);

    // Minimal valid JSON: one bar for 2026-03-03
    private static final String ONE_BAR_JSON = """
            {
              "stat": "OK",
              "data": [
                ["115/03/03", "20,234.56", "20,456.78", "20,100.23", "20,300.45", "3,456,789", "123,456,789,012"]
              ]
            }
            """;

    private static final String EMPTY_JSON = """
            { "stat": "OK", "data": [] }
            """;

    @BeforeEach
    void setUp() {
        realParser = new TwseParser(new ObjectMapper());
        service = new MarketIndexService(twseClient, realParser, repository);
    }

    @Test
    void collectMonth_whenDataPresent_upsertsBarAndReturnsCollected() {
        when(twseClient.fetchMonthJson(MARCH_2026)).thenReturn(ONE_BAR_JSON);
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 3))).thenReturn(Optional.empty());

        MarketIndexCollectionResult result = service.collectMonth(MARCH_2026);

        assertThat(result.status()).isEqualTo(MarketIndexCollectionResult.Status.COLLECTED);
        assertThat(result.barsCount()).isEqualTo(1);
        assertThat(result.yearMonth()).isEqualTo(MARCH_2026);
        verify(repository, times(1)).save(any(TaiexDailyBar.class));
    }

    @Test
    void collectMonth_whenNoData_returnsNoData() {
        when(twseClient.fetchMonthJson(MARCH_2026)).thenReturn(EMPTY_JSON);

        MarketIndexCollectionResult result = service.collectMonth(MARCH_2026);

        assertThat(result.status()).isEqualTo(MarketIndexCollectionResult.Status.NO_DATA);
        assertThat(result.barsCount()).isEqualTo(0);
        verify(repository, never()).save(any());
    }

    @Test
    void collectMonth_whenClientThrows_returnsError() {
        when(twseClient.fetchMonthJson(MARCH_2026)).thenThrow(new RuntimeException("connection timeout"));

        MarketIndexCollectionResult result = service.collectMonth(MARCH_2026);

        assertThat(result.status()).isEqualTo(MarketIndexCollectionResult.Status.ERROR);
        assertThat(result.errorMessage()).contains("connection timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_delegatesToCollectMonth() {
        when(twseClient.fetchMonthJson(MARCH_2026)).thenReturn(ONE_BAR_JSON);
        when(repository.findByTradeDate(any())).thenReturn(Optional.empty());

        MarketIndexCollectionResult result = service.collectDate(LocalDate.of(2026, 3, 15));

        assertThat(result.yearMonth()).isEqualTo(MARCH_2026);
        assertThat(result.status()).isEqualTo(MarketIndexCollectionResult.Status.COLLECTED);
        verify(twseClient, times(1)).fetchMonthJson(MARCH_2026);
    }

    @Test
    void collectMonth_existingBar_isUpdatedNotDuplicated() {
        TaiexDailyBar existing = new TaiexDailyBar(LocalDate.of(2026, 3, 3));
        existing.setClose(1000000L);

        when(twseClient.fetchMonthJson(MARCH_2026)).thenReturn(ONE_BAR_JSON);
        when(repository.findByTradeDate(LocalDate.of(2026, 3, 3))).thenReturn(Optional.of(existing));

        service.collectMonth(MARCH_2026);

        // Verify exactly one save, and that the saved bar has the updated close value
        ArgumentCaptor<TaiexDailyBar> captor = ArgumentCaptor.forClass(TaiexDailyBar.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getClose()).isEqualTo(2030045L);
    }
}
```

- [ ] **Step 2: Run to confirm tests fail**

```bash
mvn test -pl eagleeye-collector -Dtest=MarketIndexServiceTest -q 2>&1 | tail -20
```
Expected: compilation failure (`MarketIndexService` not found).

- [ ] **Step 3: Create the MarketIndexService implementation**

```java
package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.collector.twse.TwseParser;
import com.eagleeye.domain.entity.TaiexDailyBar;
import com.eagleeye.domain.repository.TaiexDailyBarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class MarketIndexService {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexService.class);

    private final TwseClient twseClient;
    private final TwseParser twseParser;
    private final TaiexDailyBarRepository repository;

    public MarketIndexService(TwseClient twseClient,
                              TwseParser twseParser,
                              TaiexDailyBarRepository repository) {
        this.twseClient = twseClient;
        this.twseParser = twseParser;
        this.repository = repository;
    }

    /**
     * Fetches and upserts all TAIEX bars for the given month.
     * TWSE returns a full month per request — idempotent to re-run.
     */
    @Transactional
    public MarketIndexCollectionResult collectMonth(YearMonth yearMonth) {
        try {
            String json = twseClient.fetchMonthJson(yearMonth);
            List<TaiexDailyBar> bars = twseParser.parse(json);

            if (bars.isEmpty()) {
                log.info("No TAIEX data for {} — skipping", yearMonth);
                return MarketIndexCollectionResult.noData(yearMonth);
            }

            bars.forEach(this::upsert);
            log.info("Collected {} TAIEX bars for {}", bars.size(), yearMonth);
            return MarketIndexCollectionResult.collected(yearMonth, bars.size());

        } catch (Exception e) {
            log.error("TAIEX collection failed for {}: {}", yearMonth, e.getMessage(), e);
            return MarketIndexCollectionResult.error(yearMonth, e.getMessage());
        }
    }

    /**
     * Collects the month containing the given date.
     * Used by the shell command for single-date invocation.
     */
    public MarketIndexCollectionResult collectDate(LocalDate date) {
        return collectMonth(YearMonth.from(date));
    }

    private void upsert(TaiexDailyBar parsed) {
        TaiexDailyBar bar = repository
                .findByTradeDate(parsed.getTradeDate())
                .orElseGet(() -> new TaiexDailyBar(parsed.getTradeDate()));

        bar.setOpen(parsed.getOpen());
        bar.setHigh(parsed.getHigh());
        bar.setLow(parsed.getLow());
        bar.setClose(parsed.getClose());
        bar.setVolume(parsed.getVolume());
        bar.setTurnover(parsed.getTurnover());

        repository.save(bar);
    }
}
```

- [ ] **Step 4: Run all tests to confirm they pass**

```bash
mvn test -pl eagleeye-collector -Dtest=MarketIndexServiceTest -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`, all 5 tests pass.

- [ ] **Step 5: Run the full collector test suite to check for regressions**

```bash
mvn test -pl eagleeye-collector -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarketIndexServiceTest.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexService.java
git commit -m "feat(collector): add MarketIndexService with TDD"
```

---

### Task 7: MarketIndexScheduler

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/scheduler/MarketIndexScheduler.java`

Standalone `@Component` — does NOT touch `CollectionScheduler`. Pattern mirrors `CollectionScheduler`.

- [ ] **Step 1: Create the scheduler**

```java
package com.eagleeye.collector.scheduler;

import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Triggers TAIEX daily collection at 18:00 Taipei time (Asia/Taipei).
 *
 * TWSE publishes closing index data after the 13:30 market close.
 * 18:00 provides a safe buffer (vs TAIFEX which publishes at ~16:15).
 *
 * Does NOT modify CollectionScheduler — independent bean.
 */
@Component
public class MarketIndexScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexScheduler.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final MarketIndexService marketIndexService;

    public MarketIndexScheduler(MarketIndexService marketIndexService) {
        this.marketIndexService = marketIndexService;
    }

    /**
     * Runs Monday–Friday at 18:00 Taipei time.
     * Fetches the current month — idempotent if re-run.
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Taipei")
    public void collectTaiex() {
        YearMonth ym = YearMonth.now(TAIPEI);
        log.info("=== TAIEX daily collection triggered for {} ===", ym);
        try {
            MarketIndexCollectionResult result = marketIndexService.collectMonth(ym);
            log.info("=== TAIEX collection completed: {} bars for {} ===", result.barsCount(), ym);
        } catch (Exception e) {
            log.error("TAIEX daily collection failed for {}: {}", ym, e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/scheduler/MarketIndexScheduler.java
git commit -m "feat(collector): add MarketIndexScheduler (18:00 Taipei daily)"
```

---

### Task 8: MarketIndexBackfillRunner

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/MarketIndexBackfillRunner.java`

Standalone runner — does NOT touch `BackfillRunner`. Iterates month-by-month (not day-by-day like `BackfillRunner`).

- [ ] **Step 1: Create the backfill runner**

```java
package com.eagleeye.collector.runner;

import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot TAIEX backfill runner — activated by setting market-index.backfill.from property.
 *
 * Usage:
 *   java -jar eagleeye-collector-exec.jar \
 *        --market-index.backfill.from=2025-03-01 \
 *        --market-index.backfill.to=2026-03-18 \
 *        --spring.main.web-application-type=none
 *
 * Iterates month-by-month (TWSE returns full months, not individual days).
 * Does NOT activate BackfillRunner — uses a distinct property name.
 */
@Component
@ConditionalOnProperty(name = "market-index.backfill.from")
public class MarketIndexBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexBackfillRunner.class);
    private static final long REQUEST_DELAY_MS = 500;

    @Value("${market-index.backfill.from}")
    private String fromStr;

    @Value("${market-index.backfill.to:#{null}}")
    private String toStr;

    private final MarketIndexService marketIndexService;
    private final ApplicationContext applicationContext;

    public MarketIndexBackfillRunner(MarketIndexService marketIndexService,
                                     ApplicationContext applicationContext) {
        this.marketIndexService = marketIndexService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        YearMonth from = YearMonth.from(LocalDate.parse(fromStr));
        YearMonth to   = (toStr != null) ? YearMonth.from(LocalDate.parse(toStr)) : YearMonth.now();

        log.info("=== TAIEX backfill start: {} → {} ===", from, to);
        System.out.printf("TAIEX backfill: %s → %s%n%n", from, to);

        List<MarketIndexCollectionResult> results = new ArrayList<>();
        YearMonth current = from;

        while (!current.isAfter(to)) {
            MarketIndexCollectionResult result = marketIndexService.collectMonth(current);
            results.add(result);
            printRow(result);
            Thread.sleep(REQUEST_DELAY_MS);
            current = current.plusMonths(1);
        }

        printSummary(from, to, results);
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    private void printRow(MarketIndexCollectionResult result) {
        System.out.printf("  %-8s  %-8s  bars: %d%n",
                result.yearMonth(),
                result.status(),
                result.barsCount());
    }

    private void printSummary(YearMonth from, YearMonth to, List<MarketIndexCollectionResult> results) {
        long collected = results.stream().filter(MarketIndexCollectionResult::isTradeMonth).count();
        long noData    = results.stream().filter(r -> r.status() == MarketIndexCollectionResult.Status.NO_DATA).count();
        long errors    = results.stream().filter(r -> r.status() == MarketIndexCollectionResult.Status.ERROR).count();
        long totalBars = results.stream().mapToLong(MarketIndexCollectionResult::barsCount).sum();

        System.out.printf("""

                === TAIEX backfill complete: %s → %s ===
                  Months collected : %d
                  No-data months   : %d
                  Errors           : %d
                  Total bars       : %d
                %n""", from, to, collected, noData, errors, totalBars);
    }
}
```

- [ ] **Step 2: Run full build to confirm no regressions**

```bash
mvn test -pl eagleeye-collector -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/runner/MarketIndexBackfillRunner.java
git commit -m "feat(collector): add MarketIndexBackfillRunner for standalone TAIEX backfill"
```

---

### Task 9: MarketIndexCommands (Shell)

**Files:**
- Create: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarketIndexCommands.java`

Spring Shell 4.x rules (from migration notes):
- `@Component` on the class — do NOT add `@EnableCommand` to the app
- `@Command(name = "market-index collect")` on each method — multi-word names work
- `@Option(longName = "...")` on each parameter
- Shell commands call `MarketIndexService` directly in a loop — do NOT invoke `MarketIndexBackfillRunner` and do NOT call `System.exit()`

- [ ] **Step 1: Create MarketIndexCommands**

```java
package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class MarketIndexCommands {

    @Autowired
    MarketIndexService marketIndexService;

    @Command(name = "market-index collect", description = "Collect TAIEX data for the month containing the given date")
    public String collect(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        MarketIndexCollectionResult result = marketIndexService.collectDate(d);
        return formatResult(result);
    }

    @Command(name = "market-index backfill", description = "Backfill TAIEX data for a range of months")
    public String backfill(
            @Option(longName = "from", description = "Start date YYYY-MM-DD (default: 12 months ago)", defaultValue = "") String from,
            @Option(longName = "to",   description = "End date YYYY-MM-DD (default: today)",           defaultValue = "") String to) {

        LocalDate fromDate = (from == null || from.isEmpty()) ? LocalDate.now().minusMonths(12) : LocalDate.parse(from);
        LocalDate toDate   = (to   == null || to.isEmpty())   ? LocalDate.now()                 : LocalDate.parse(to);

        YearMonth fromYm = YearMonth.from(fromDate);
        YearMonth toYm   = YearMonth.from(toDate);

        StringBuilder sb = new StringBuilder();
        YearMonth current = fromYm;
        while (!current.isAfter(toYm)) {
            MarketIndexCollectionResult result = marketIndexService.collectMonth(current);
            sb.append(formatResult(result)).append("\n");
            current = current.plusMonths(1);
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return sb.toString().stripTrailing();
    }

    private String formatResult(MarketIndexCollectionResult r) {
        return switch (r.status()) {
            case COLLECTED -> r.yearMonth() + " \u2014 bars: " + r.barsCount();
            case NO_DATA   -> r.yearMonth() + " \u2014 no data";
            case ERROR     -> r.yearMonth() + " \u2014 ERROR: " + r.errorMessage();
        };
    }
}
```

- [ ] **Step 2: Build the full project to confirm everything compiles**

```bash
mvn install -q -DskipTests 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run all tests across all modules**

```bash
mvn test -q 2>&1 | tail -15
```
Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarketIndexCommands.java
git commit -m "feat(shell): add market-index collect and backfill commands"
```

---

## Verification

After all tasks complete, perform a final smoke test:

- [ ] **Build + test**

```bash
mvn clean install -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`.

- [ ] **Verify new DB table is created**

```bash
java -jar eagleeye-collector/target/eagleeye-collector-exec.jar \
     --spring.main.web-application-type=none \
     --spring.shell.interactive.enabled=false \
     --logging.level.org.hibernate=DEBUG 2>&1 | grep -i taiex_daily_bar | head -5
```
Expected: log line showing `create table taiex_daily_bar` (first run) or `alter table` / no DDL (subsequent runs).

- [ ] **Final commit tag**

```bash
git tag v0.2.0-taiex-collector
```
