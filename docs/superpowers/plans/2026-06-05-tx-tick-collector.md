# TX Tick Collector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Download TAIFEX daily TX tick data, apply 8 cleaning rules, and store in SQLite `tx_tick` table; runs automatically at 14:05 on weekdays.

**Architecture:** New `TxTickDownloader` fetches and unzips the TAIFEX daily CSV (Big5), `TxTickParser` applies cleaning rules and returns `List<TxTick>`, `TxTickService` does delete+saveAll in one transaction, `TxTickCollector` wires into the existing `ScheduledCollector` dispatch system.

**Tech Stack:** Java 25, Spring Boot 4, Spring Data JPA, SQLite, JUnit 5 + AssertJ

---

## File Map

| Action | File |
|--------|------|
| Create | `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/TxTick.java` |
| Create | `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/TxTickRepository.java` |
| Create | `eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TxTickDownloader.java` |
| Create | `eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TxTickParser.java` |
| Create | `eagleeye-collector/src/test/java/com/eagleeye/collector/taifex/TxTickParserTest.java` |
| Create | `eagleeye-collector/src/main/java/com/eagleeye/collector/service/TxTickService.java` |
| Create | `eagleeye-collector/src/main/java/com/eagleeye/collector/collector/TxTickCollector.java` |
| Create | `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/TxTickBackfillRunner.java` |
| Create | `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/TxTickCommands.java` |
| Create | `deploy/com.eagleeye.collector.txtick.plist` |
| Modify | `eagleeye-collector/src/main/resources/application.yml` — add Hibernate batch_size |
| Modify | `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CollectorDispatchRunner.java` — add txtick.backfill.from to ConditionalOnExpression |
| Modify | `deploy/eagleeye-backfill.sh` — add `--tx-tick` flag |
| Modify | `deploy/install.sh` — install txtick plist |
| Modify | `deploy/uninstall.sh` — remove txtick plist |

---

## Task 1: TxTick entity + TxTickRepository

**Files:**
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/TxTick.java`
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/TxTickRepository.java`

- [ ] **Step 1: Create TxTick entity**

```java
// eagleeye-domain/src/main/java/com/eagleeye/domain/entity/TxTick.java
package com.eagleeye.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "tx_tick",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tx_tick",
        columnNames = {"trade_date", "time", "price", "volume"}
    )
)
public class TxTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "time", nullable = false, length = 6)
    private String time;

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "volume", nullable = false)
    private int volume;

    @Column(name = "contract_month", nullable = false)
    private String contractMonth;

    @Column(name = "is_auction", nullable = false)
    private boolean auction;

    protected TxTick() {}

    public TxTick(LocalDate tradeDate, String time, int price, int volume,
                  String contractMonth, boolean auction) {
        this.tradeDate = tradeDate;
        this.time = time;
        this.price = price;
        this.volume = volume;
        this.contractMonth = contractMonth;
        this.auction = auction;
    }

    public Long getId()                { return id; }
    public LocalDate getTradeDate()    { return tradeDate; }
    public String getTime()            { return time; }
    public int getPrice()              { return price; }
    public int getVolume()             { return volume; }
    public String getContractMonth()   { return contractMonth; }
    public boolean isAuction()         { return auction; }
}
```

- [ ] **Step 2: Create TxTickRepository**

```java
// eagleeye-domain/src/main/java/com/eagleeye/domain/repository/TxTickRepository.java
package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.TxTick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface TxTickRepository extends JpaRepository<TxTick, Long> {

    long countByTradeDate(LocalDate tradeDate);

    List<TxTick> findTop5ByTradeDateOrderByTimeAsc(LocalDate tradeDate);

    @Modifying
    @Query("DELETE FROM TxTick t WHERE t.tradeDate = :tradeDate")
    void deleteByTradeDate(LocalDate tradeDate);
}
```

- [ ] **Step 3: Verify compile**

```bash
cd /Users/chrisliu/projects/chris/Eagleeye
mvn compile -pl eagleeye-domain -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/entity/TxTick.java \
        eagleeye-domain/src/main/java/com/eagleeye/domain/repository/TxTickRepository.java
git commit -m "feat(domain): add TxTick entity and repository"
```

---

## Task 2: TxTickParser with unit tests

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TxTickParser.java`
- Create: `eagleeye-collector/src/test/java/com/eagleeye/collector/taifex/TxTickParserTest.java`

- [ ] **Step 1: Write failing tests**

```java
// eagleeye-collector/src/test/java/com/eagleeye/collector/taifex/TxTickParserTest.java
package com.eagleeye.collector.taifex;

import com.eagleeye.domain.entity.TxTick;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TxTickParserTest {

    private final TxTickParser parser = new TxTickParser();
    private final LocalDate DATE = LocalDate.of(2026, 6, 5);

    // CSV columns: date,product,contract_month,time,price,volume,near_price,far_price,auction_flag
    private String row(String product, String contractMonth, String time,
                       String price, String volume, String auctionFlag) {
        return "20260605," + product + "," + contractMonth + "," +
               time + "," + price + "," + volume + ",0,0," + auctionFlag;
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertThat(parser.parse(List.of(), DATE)).isEmpty();
    }

    @Test
    void nonTxProduct_filtered() {
        assertThat(parser.parse(List.of(row("MXF", "202606", "090000", "21000", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void contractMonthWithSlash_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606/202609", "090000", "21500", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void priceBelowThreshold_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606", "090000", "29999", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void priceAtExactThreshold_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606", "090000", "30000", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void beforeMarketOpen_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606", "084459", "21500", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void afterMarketClose_filtered() {
        assertThat(parser.parse(List.of(row("TX", "202606", "134501", "21500", "5", "")), DATE))
            .isEmpty();
    }

    @Test
    void marketBoundaries_inclusive() {
        List<String> lines = List.of(
            row("TX", "202606", "084500", "21500", "3", ""),
            row("TX", "202606", "134500", "21600", "2", "")
        );
        assertThat(parser.parse(lines, DATE)).hasSize(2);
    }

    @Test
    void validRow_parsedCorrectly() {
        List<TxTick> ticks = parser.parse(List.of(row("TX", "202606", "090000", "21500", "5", "")), DATE);
        assertThat(ticks).hasSize(1);
        TxTick t = ticks.get(0);
        assertThat(t.getTradeDate()).isEqualTo(DATE);
        assertThat(t.getTime()).isEqualTo("090000");
        assertThat(t.getPrice()).isEqualTo(21500);
        assertThat(t.getVolume()).isEqualTo(5);
        assertThat(t.getContractMonth()).isEqualTo("202606");
        assertThat(t.isAuction()).isFalse();
    }

    @Test
    void auctionFlag_setsIsAuctionTrue() {
        List<TxTick> ticks = parser.parse(List.of(row("TX", "202606", "084500", "21500", "5", "*")), DATE);
        assertThat(ticks).hasSize(1);
        assertThat(ticks.get(0).isAuction()).isTrue();
    }

    @Test
    void timePadding_shortTime_paddedToSixDigits() {
        List<TxTick> ticks = parser.parse(List.of(row("TX", "202606", "84500", "21500", "5", "")), DATE);
        assertThat(ticks).hasSize(1);
        assertThat(ticks.get(0).getTime()).isEqualTo("084500");
    }

    @Test
    void dominantContract_onlyHighestVolumeKept() {
        // 202606: total volume 10; 202609: total volume 5 → keep 202606 only
        List<String> lines = List.of(
            row("TX", "202606", "090000", "21500", "10", ""),
            row("TX", "202609", "090100", "21500", "5", "")
        );
        List<TxTick> ticks = parser.parse(lines, DATE);
        assertThat(ticks).hasSize(1);
        assertThat(ticks.get(0).getContractMonth()).isEqualTo("202606");
    }

    @Test
    void malformedPrice_rowDropped() {
        List<String> lines = List.of(row("TX", "202606", "090000", "N/A", "5", ""));
        assertThat(parser.parse(lines, DATE)).isEmpty();
    }

    @Test
    void tooFewColumns_rowDropped() {
        assertThat(parser.parse(List.of("TX,202606,090000"), DATE)).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests — verify they FAIL (class not found)**

```bash
cd /Users/chrisliu/projects/chris/Eagleeye
mvn test -pl eagleeye-collector -Dtest=TxTickParserTest -q 2>&1 | tail -5
```

Expected: compilation error — `TxTickParser` does not exist yet.

- [ ] **Step 3: Implement TxTickParser**

```java
// eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TxTickParser.java
package com.eagleeye.collector.taifex;

import com.eagleeye.domain.entity.TxTick;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TxTickParser {

    public List<TxTick> parse(List<String> lines, LocalDate tradeDate) {
        // Steps 1–4: filter and validate each row
        record Row(String[] cols, String contractMonth, int price, int volume) {}
        List<Row> candidates = new ArrayList<>();

        for (String line : lines) {
            String[] cols = line.split(",", -1);
            if (cols.length < 9) continue;

            if (!"TX".equals(cols[1].trim())) continue;                     // rule 1
            String contract = cols[2].trim();
            if (contract.contains("/")) continue;                            // rule 2

            int price, volume;
            try {
                price  = Integer.parseInt(cols[4].trim().replace(",", "")); // rule 3
                volume = Integer.parseInt(cols[5].trim().replace(",", "")); // rule 3
            } catch (NumberFormatException e) {
                continue;
            }
            if (price <= 30000) continue;                                    // rule 4

            candidates.add(new Row(cols, contract, price, volume));
        }

        if (candidates.isEmpty()) return List.of();

        // Step 5: keep only the contract_month with the highest total volume
        String dominant = candidates.stream()
            .collect(Collectors.groupingBy(Row::contractMonth,
                     Collectors.summingInt(Row::volume)))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");

        // Steps 6–8: pad time, filter time range, mark auction
        List<TxTick> ticks = new ArrayList<>();
        for (Row r : candidates) {
            if (!r.contractMonth().equals(dominant)) continue;               // step 5

            String time = padTime(r.cols()[3].trim());                       // step 6
            if (time.compareTo("084500") < 0 || time.compareTo("134500") > 0) continue; // step 7

            boolean auction = r.cols()[8].trim().contains("*");              // step 8
            ticks.add(new TxTick(tradeDate, time, r.price(), r.volume(), dominant, auction));
        }

        return ticks;
    }

    private String padTime(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        while (digits.length() < 6) digits = "0" + digits;
        return digits.substring(0, 6);
    }
}
```

- [ ] **Step 4: Run tests — verify they PASS**

```bash
mvn test -pl eagleeye-collector -Dtest=TxTickParserTest
```

Expected:
```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TxTickParser.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/taifex/TxTickParserTest.java
git commit -m "feat(collector): add TxTickParser with 8 cleaning rules"
```

---

## Task 3: TxTickDownloader

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TxTickDownloader.java`

- [ ] **Step 1: Create TxTickDownloader**

```java
// eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TxTickDownloader.java
package com.eagleeye.collector.taifex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

@Component
public class TxTickDownloader {

    private static final Logger log = LoggerFactory.getLogger(TxTickDownloader.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy_MM_dd");
    private static final int MAX_RETRIES = 3;

    private final RestClient restClient;

    public TxTickDownloader(RestClient.Builder builder) {
        this.restClient = builder
            .baseUrl("https://www.taifex.com.tw")
            .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; EagleEye/1.0)")
            .build();
    }

    /**
     * Downloads and unzips the TAIFEX daily CSV for the given date.
     * Returns null when the file is not available (holiday / weekend).
     * Retries up to 3 times on transient errors (backoff: 1s, 2s, 4s).
     */
    public List<String> downloadLines(LocalDate date) throws IOException, InterruptedException {
        String path = "/file/taifex/Dailydownload/DailydownloadCSV/Daily_" + date.format(FMT) + ".zip";

        byte[] zipBytes = null;
        long delayMs = 1_000;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                zipBytes = restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(byte[].class);
                break;
            } catch (HttpClientErrorException.NotFound e) {
                log.info("No tick data for {} (404)", date);
                return null;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                log.warn("Download attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, date, e.getMessage());
                Thread.sleep(delayMs);
                delayMs *= 2;
            }
        }

        if (zipBytes == null) return null;
        return unzipToLines(zipBytes);
    }

    private List<String> unzipToLines(byte[] zipBytes) throws IOException {
        List<String> lines = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            if (zis.getNextEntry() == null) return lines;
            String content = new String(zis.readAllBytes(), Charset.forName("Big5"));
            for (String line : content.split("\r?\n")) {
                if (!line.isBlank()) lines.add(line);
            }
        }
        return lines;
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -pl eagleeye-collector -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TxTickDownloader.java
git commit -m "feat(collector): add TxTickDownloader with retry and Big5 unzip"
```

---

## Task 4: TxTickService + Hibernate batch config

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/TxTickService.java`
- Modify: `eagleeye-collector/src/main/resources/application.yml`

- [ ] **Step 1: Create TxTickService**

```java
// eagleeye-collector/src/main/java/com/eagleeye/collector/service/TxTickService.java
package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TxTickDownloader;
import com.eagleeye.collector.taifex.TxTickParser;
import com.eagleeye.domain.entity.TxTick;
import com.eagleeye.domain.repository.TxTickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TxTickService {

    private static final Logger log = LoggerFactory.getLogger(TxTickService.class);

    private final TxTickDownloader downloader;
    private final TxTickParser parser;
    private final TxTickRepository repository;

    public TxTickService(TxTickDownloader downloader, TxTickParser parser, TxTickRepository repository) {
        this.downloader = downloader;
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public DateCollectionResult collectDate(LocalDate date) {
        try {
            List<String> lines = downloader.downloadLines(date);
            if (lines == null) {
                log.info("No tick data for {}", date);
                return new DateCollectionResult.NoData(date);
            }
            List<TxTick> ticks = parser.parse(lines, date);
            if (ticks.isEmpty()) {
                log.info("No TX ticks after cleaning for {}", date);
                return new DateCollectionResult.NoData(date);
            }
            repository.deleteByTradeDate(date);
            repository.saveAll(ticks);
            log.info("Collected {} TX ticks for {}", ticks.size(), date);
            return new DateCollectionResult.Collected(date);
        } catch (Exception e) {
            log.error("TX tick collection failed for {}: {}", date, e.getMessage(), e);
            return new DateCollectionResult.Error(date, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Add Hibernate batch_size to application.yml**

In `eagleeye-collector/src/main/resources/application.yml`, add under the top-level `spring:` section (before the `---` profile separator), inside the existing `jpa:` block:

```yaml
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          batch_size: 500
        order_inserts: true
```

- [ ] **Step 3: Verify compile**

```bash
mvn compile -pl eagleeye-collector -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/service/TxTickService.java \
        eagleeye-collector/src/main/resources/application.yml
git commit -m "feat(collector): add TxTickService with delete+saveAll batch insert"
```

---

## Task 5: TxTickCollector

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/collector/TxTickCollector.java`

- [ ] **Step 1: Create TxTickCollector**

```java
// eagleeye-collector/src/main/java/com/eagleeye/collector/collector/TxTickCollector.java
package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.TxTickService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TxTickCollector implements ScheduledCollector {

    private final TxTickService service;

    public TxTickCollector(TxTickService service) {
        this.service = service;
    }

    @Override public String name() { return "TXTICK"; }

    @Override
    public CollectorOutcome collect(LocalDate date) {
        return switch (service.collectDate(date)) {
            case DateCollectionResult.Collected c -> CollectorOutcome.collected("ok");
            case DateCollectionResult.NoData n    -> CollectorOutcome.noData();
            case DateCollectionResult.Error e     -> CollectorOutcome.error(e.message());
        };
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -pl eagleeye-collector -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/collector/TxTickCollector.java
git commit -m "feat(collector): add TxTickCollector (TXTICK)"
```

---

## Task 6: TxTickBackfillRunner + CollectorDispatchRunner update

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/TxTickBackfillRunner.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CollectorDispatchRunner.java`

- [ ] **Step 1: Create TxTickBackfillRunner**

```java
// eagleeye-collector/src/main/java/com/eagleeye/collector/runner/TxTickBackfillRunner.java
package com.eagleeye.collector.runner;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.TxTickService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "txtick.backfill.from")
public class TxTickBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TxTickBackfillRunner.class);
    private static final long REQUEST_DELAY_MS = 1_000;

    @Value("${txtick.backfill.from}")
    private String fromStr;

    @Value("${txtick.backfill.to:#{null}}")
    private String toStr;

    private final TxTickService service;
    private final ApplicationContext applicationContext;

    public TxTickBackfillRunner(TxTickService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to   = toStr != null ? LocalDate.parse(toStr) : LocalDate.now();

        System.out.printf("TX Tick backfill: %s → %s%n%n", from, to);
        int ok = 0, holidays = 0, errors = 0;

        LocalDate current = from;
        while (!current.isAfter(to)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                current = current.plusDays(1);
                continue;
            }
            DateCollectionResult result = service.collectDate(current);
            switch (result) {
                case DateCollectionResult.Collected c -> { ok++;       System.out.printf("  %-12s  OK%n", current); }
                case DateCollectionResult.NoData n    -> { holidays++; System.out.printf("  %-12s  HOLIDAY%n", current); }
                case DateCollectionResult.Error e     -> { errors++;   System.out.printf("  %-12s  ERROR: %s%n", current, e.message()); }
            }
            Thread.sleep(REQUEST_DELAY_MS);
            current = current.plusDays(1);
        }

        System.out.printf("%n=== TX Tick backfill complete: %d collected, %d holidays, %d errors ===%n",
            ok, holidays, errors);
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
```

- [ ] **Step 2: Update CollectorDispatchRunner `@ConditionalOnExpression`**

In `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CollectorDispatchRunner.java`, change the `@ConditionalOnExpression` annotation from:

```java
@ConditionalOnExpression(
    "!environment.containsProperty('backfill.from') && " +
    "!environment.containsProperty('combined.backfill.from') && " +
    "!environment.containsProperty('market-index.backfill.from') && " +
    "!environment.containsProperty('futures-ah.backfill.from')"
)
```

to:

```java
@ConditionalOnExpression(
    "!environment.containsProperty('backfill.from') && " +
    "!environment.containsProperty('combined.backfill.from') && " +
    "!environment.containsProperty('market-index.backfill.from') && " +
    "!environment.containsProperty('futures-ah.backfill.from') && " +
    "!environment.containsProperty('txtick.backfill.from')"
)
```

- [ ] **Step 3: Verify compile**

```bash
mvn compile -pl eagleeye-collector -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/runner/TxTickBackfillRunner.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CollectorDispatchRunner.java
git commit -m "feat(collector): add TxTickBackfillRunner, exclude from dispatch"
```

---

## Task 7: Shell commands

**Files:**
- Create: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/TxTickCommands.java`

- [ ] **Step 1: Create TxTickCommands**

```java
// eagleeye-shell/src/main/java/com/eagleeye/shell/commands/TxTickCommands.java
package com.eagleeye.shell.commands;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.TxTickService;
import com.eagleeye.domain.entity.TxTick;
import com.eagleeye.domain.repository.TxTickRepository;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Component
public class TxTickCommands {

    private final TxTickService service;
    private final TxTickRepository repository;

    public TxTickCommands(TxTickService service, TxTickRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @Command(name = "tx-tick collect", description = "Collect TX tick data for a date")
    public String collect(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        return switch (service.collectDate(d)) {
            case DateCollectionResult.Collected c -> d + " — TX ticks collected";
            case DateCollectionResult.NoData n    -> d + " — no data";
            case DateCollectionResult.Error e     -> d + " — ERROR: " + e.message();
        };
    }

    @Command(name = "tx-tick list", description = "Show TX tick count and first 5 rows for a date")
    public String list(
            @Option(longName = "date", description = "Trade date YYYY-MM-DD (default: today)", defaultValue = "") String date) {
        LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
        long count = repository.countByTradeDate(d);
        List<TxTick> sample = repository.findTop5ByTradeDateOrderByTimeAsc(d);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("TX Ticks — %s (%d rows)%n", d, count));
        if (!sample.isEmpty()) {
            sb.append(String.format("  %-6s  %-7s  %-4s  %-12s  %s%n", "TIME", "PRICE", "VOL", "CONTRACT", "AUC"));
            for (TxTick t : sample) {
                sb.append(String.format("  %-6s  %-7d  %-4d  %-12s  %s%n",
                    t.getTime(), t.getPrice(), t.getVolume(), t.getContractMonth(),
                    t.isAuction() ? "Y" : ""));
            }
            if (count > 5) sb.append(String.format("  ... (%d more)%n", count - 5));
        }
        return sb.toString();
    }

    @Command(name = "tx-tick backfill", description = "Backfill TX tick data for a date range")
    public String backfill(
            @Option(longName = "from", required = true) String from,
            @Option(longName = "to", defaultValue = "") String to) throws InterruptedException {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate   = (to == null || to.isEmpty()) ? LocalDate.now() : LocalDate.parse(to);
        int ok = 0, holidays = 0, errors = 0;
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            if (current.getDayOfWeek() == DayOfWeek.SATURDAY ||
                current.getDayOfWeek() == DayOfWeek.SUNDAY) {
                current = current.plusDays(1);
                continue;
            }
            switch (service.collectDate(current)) {
                case DateCollectionResult.Collected c -> ok++;
                case DateCollectionResult.NoData n    -> holidays++;
                case DateCollectionResult.Error e     -> errors++;
            }
            Thread.sleep(1_000);
            current = current.plusDays(1);
        }
        return String.format("TX Tick backfill %s → %s: %d collected, %d holidays, %d errors",
            fromDate, toDate, ok, holidays, errors);
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -pl eagleeye-shell -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add eagleeye-shell/src/main/java/com/eagleeye/shell/commands/TxTickCommands.java
git commit -m "feat(shell): add tx-tick collect/list/backfill commands"
```

---

## Task 8: launchd plist + deploy scripts

**Files:**
- Create: `deploy/com.eagleeye.collector.txtick.plist`
- Modify: `deploy/eagleeye-backfill.sh`
- Modify: `deploy/install.sh`
- Modify: `deploy/uninstall.sh`

- [ ] **Step 1: Create plist**

```xml
<!-- deploy/com.eagleeye.collector.txtick.plist -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<!--
    TXTICK collector — daily TX tick data, TAIFEX file available ~13:50
    launchd StartCalendarInterval uses local time (Asia/Taipei), NOT UTC.
-->
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.eagleeye.collector.txtick</string>

    <key>ProgramArguments</key>
    <array>
        <string>/usr/local/opt/openjdk@25/bin/java</string>
        <string>--enable-native-access=ALL-UNNAMED</string>
        <string>-XX:+AutoCreateSharedArchive</string>
        <string>-XX:SharedArchiveFile=/opt/eagleeye/collector/collector.jsa</string>
        <string>-Xlog:cds=off</string>
        <string>-jar</string>
        <string>/opt/eagleeye/collector/eagleeye-collector.jar</string>
        <string>--spring.profiles.active=prod</string>
        <string>--logging.level.root=WARN</string>
        <string>--logging.level.com.eagleeye=INFO</string>
        <string>--collector=TXTICK</string>
    </array>

    <key>StartCalendarInterval</key>
    <array>
        <dict><key>Weekday</key><integer>1</integer><key>Hour</key><integer>14</integer><key>Minute</key><integer>5</integer></dict>
        <dict><key>Weekday</key><integer>2</integer><key>Hour</key><integer>14</integer><key>Minute</key><integer>5</integer></dict>
        <dict><key>Weekday</key><integer>3</integer><key>Hour</key><integer>14</integer><key>Minute</key><integer>5</integer></dict>
        <dict><key>Weekday</key><integer>4</integer><key>Hour</key><integer>14</integer><key>Minute</key><integer>5</integer></dict>
        <dict><key>Weekday</key><integer>5</integer><key>Hour</key><integer>14</integer><key>Minute</key><integer>5</integer></dict>
    </array>

    <key>StandardOutPath</key>
    <string>/opt/eagleeye/logs/collector-txtick.log</string>
    <key>StandardErrorPath</key>
    <string>/opt/eagleeye/logs/collector-txtick-error.log</string>

    <key>RunAtLoad</key>
    <false/>

    <key>EnvironmentVariables</key>
    <dict>
        <key>JAVA_HOME</key>
        <string>/usr/local/opt/openjdk@25</string>
    </dict>
</dict>
</plist>
```

- [ ] **Step 2: Update eagleeye-backfill.sh — add `--tx-tick` flag**

Replace the content of `deploy/eagleeye-backfill.sh` with:

```bash
#!/usr/bin/env bash
# One-shot backfill runner
# Usage:
#   eagleeye-backfill --from 2026-01-01 [--to 2026-05-28]              # combined (all regular collectors)
#   eagleeye-backfill --futures-ah --from 2026-01-01 [--to 2026-05-28] # after-hours futures only
#   eagleeye-backfill --tx-tick --from 2026-01-01 [--to 2026-05-28]    # TX tick data only

set -euo pipefail

FROM=""
TO=""
FUTURES_AH=false
TX_TICK=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --futures-ah) FUTURES_AH=true; shift ;;
        --tx-tick)    TX_TICK=true;    shift ;;
        --from) FROM="$2"; shift 2 ;;
        --to)   TO="$2";   shift 2 ;;
        *) echo "Unknown argument: $1"; exit 1 ;;
    esac
done

if [[ -z "$FROM" ]]; then
    echo "Usage: eagleeye-backfill [--futures-ah|--tx-tick] --from YYYY-MM-DD [--to YYYY-MM-DD]"
    exit 1
fi

TO="${TO:-$(date +%Y-%m-%d)}"

JAVA="java --enable-native-access=ALL-UNNAMED"
JAR="/opt/eagleeye/collector/eagleeye-collector.jar"
COMMON_ARGS="--spring.profiles.active=prod --logging.level.root=WARN --logging.level.com.eagleeye=INFO"

if [[ "$FUTURES_AH" == true ]]; then
    echo "=== After-hours futures backfill: $FROM → $TO ==="
    $JAVA -jar "$JAR" $COMMON_ARGS \
        --futures-ah.backfill.from="$FROM" \
        --futures-ah.backfill.to="$TO"
elif [[ "$TX_TICK" == true ]]; then
    echo "=== TX Tick backfill: $FROM → $TO ==="
    $JAVA -jar "$JAR" $COMMON_ARGS \
        --txtick.backfill.from="$FROM" \
        --txtick.backfill.to="$TO"
else
    echo "=== Backfilling $FROM → $TO ==="
    $JAVA -jar "$JAR" $COMMON_ARGS \
        --combined.backfill.from="$FROM" \
        --combined.backfill.to="$TO"
fi
```

- [ ] **Step 3: Update install.sh — add TXTICK to COLLECTORS array**

In `deploy/install.sh`, change:

```bash
COLLECTORS=(futah taiex iflow taifex margin)
```

to:

```bash
COLLECTORS=(futah taiex iflow taifex margin txtick)
```

- [ ] **Step 4: Update uninstall.sh — add TXTICK**

In `deploy/uninstall.sh`, change:

```bash
for c in "${COLLECTORS[@]}"; do
```

by first updating:

```bash
COLLECTORS=(futah taiex iflow taifex margin)
```

to:

```bash
COLLECTORS=(futah taiex iflow taifex margin txtick)
```

- [ ] **Step 5: Commit**

```bash
git add deploy/com.eagleeye.collector.txtick.plist \
        deploy/eagleeye-backfill.sh \
        deploy/install.sh \
        deploy/uninstall.sh
git commit -m "feat(deploy): add TXTICK launchd agent and backfill support"
```

---

## Task 9: Build, test, verify

- [ ] **Step 1: Full build + all tests**

```bash
cd /Users/chrisliu/projects/chris/Eagleeye
mvn clean package -DskipTests -q
mvn test -pl eagleeye-collector
```

Expected:
```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [ ] **Step 2: Smoke-test TXTICK collector with prod JAR**

Run the collector for a recent trading day (e.g. 2026-06-04 — adjust to the most recent Wednesday/Thursday):

```bash
java --enable-native-access=ALL-UNNAMED \
  -jar eagleeye-collector/target/eagleeye-collector-*-exec.jar \
  --spring.profiles.active=prod \
  --logging.level.root=WARN \
  --logging.level.com.eagleeye=INFO \
  --collector=TXTICK
```

Expected output:
```
  [TXTICK]  2026-06-04  ok
```

Then verify the row count in SQLite:

```bash
sqlite3 ~/.eagleeye/data/eagleeye.db \
  "SELECT count(*) FROM tx_tick WHERE trade_date='2026-06-04';"
```

Expected: a number between 5000 and 35000 (typical TX daily tick count).

- [ ] **Step 3: Verify backfill script help**

```bash
/usr/local/bin/eagleeye-backfill --help 2>&1 || true
# Or run without arguments:
/usr/local/bin/eagleeye-backfill
```

Expected: usage message includes `--tx-tick`.

- [ ] **Step 4: Final commit (if any stragglers)**

```bash
git status
# If clean: done. If not, commit remaining changes.
```
