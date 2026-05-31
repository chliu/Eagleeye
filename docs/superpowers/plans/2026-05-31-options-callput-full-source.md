# Store complete callsAndPuts source — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the full 12-column TAIFEX callsAndPuts breakdown per `(trade_date, contract, trader, right)` in a dedicated table, and migrate the dashboard to read call/put net value from it.

**Architecture:** New `OptionsCallPutPosition` entity extends the shared `AbstractMarketPosition` (12 volume/value columns) and adds a `right_type` discriminator. The parser keeps all 12 columns instead of only the last; the collection service upserts into the new table; the dashboard reads `oiNetValue` from the new repository. `OptionsPosition` reverts to a plain entity (its two scalar net-value columns are removed and left unused in the existing DB).

**Tech Stack:** Java 25, Spring Boot 4.x, Spring Data JPA, Hibernate (`ddl-auto: update`), JUnit 5, AssertJ, Mockito, jsoup, Maven multi-module.

---

## Preflight & conventions

- **Working tree state:** The tree currently contains uncommitted "call/put net value" work (flat `OptionsCallPutDto`, `RightType`, `OptionsPosition` with `oiNetValueCall`/`oiNetValuePut`, dashboard wired to those two columns). This plan **transforms** that work; the task commits will encompass these existing changes.
- **Test command pattern** (multi-module reactor, builds upstream `eagleeye-domain` via `-am`):
  ```bash
  mvn -q -pl <module> -am test -Dtest=<TestClass> -DfailIfNoTests=false
  ```
- **Full build:** `mvn -q clean test` from repo root.
- Column semantics (12 data columns, in order): `tradingLongVolume, tradingLongValue, tradingShortVolume, tradingShortValue, tradingNetVolume, tradingNetValue, oiLongVolume, oiLongValue, oiShortVolume, oiShortValue, oiNetVolume, oiNetValue`. `oiNetValue` = 未平倉餘額→買賣差額→契約金額 (headline metric).

### Task 0: Confirm green baseline

- [ ] **Step 1: Build the current tree**

Run: `mvn -q clean test`
Expected: BUILD SUCCESS. If it fails, stop and report — do not start the plan on a red tree.

---

## Task 1: New `OptionsCallPutPosition` entity + repository

**Files:**
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/OptionsCallPutPosition.java`
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/OptionsCallPutPositionRepository.java`
- Test (create): `eagleeye-collector/src/test/java/com/eagleeye/collector/OptionsCallPutPositionRepositoryIT.java`

- [ ] **Step 1: Create the entity**

Create `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/OptionsCallPutPosition.java`:

```java
package com.eagleeye.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * Full per-trader breakdown of the TAIFEX callsAndPuts report
 * (三大法人－區分各類選擇權買賣權契約金額), split by call/put.
 *
 * <p>Inherits the 12 trading/open-interest volume+value columns from
 * {@link AbstractMarketPosition}; {@code oiNetValue} here is
 * 未平倉餘額→買賣差額→契約金額, the headline metric.
 */
@Entity
@Table(
    name = "options_call_put_position",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_options_call_put_position",
        columnNames = {"trade_date", "contract", "trader_type", "right_type"}
    )
)
public class OptionsCallPutPosition extends AbstractMarketPosition {

    @Enumerated(EnumType.STRING)
    @Column(name = "right_type", nullable = false, length = 4)
    private RightType rightType;

    protected OptionsCallPutPosition() {}

    public OptionsCallPutPosition(LocalDate tradeDate, String contract,
                                  TraderType traderType, RightType rightType) {
        super(tradeDate, contract, traderType);
        this.rightType = rightType;
    }

    public RightType getRightType() { return rightType; }
    public void setRightType(RightType v) { this.rightType = v; }
}
```

- [ ] **Step 2: Create the repository**

Create `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/OptionsCallPutPositionRepository.java`:

```java
package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TraderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OptionsCallPutPositionRepository
        extends JpaRepository<OptionsCallPutPosition, Long> {

    Optional<OptionsCallPutPosition> findByTradeDateAndContractAndTraderTypeAndRightType(
            LocalDate tradeDate, String contract, TraderType traderType, RightType rightType);

    List<OptionsCallPutPosition>
    findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, TraderType traderType, RightType rightType,
            LocalDate from, LocalDate to);
}
```

- [ ] **Step 3: Write the failing repository IT**

Create `eagleeye-collector/src/test/java/com/eagleeye/collector/OptionsCallPutPositionRepositoryIT.java`:

```java
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
```

- [ ] **Step 4: Run the IT**

Run: `mvn -q -pl eagleeye-collector -am test -Dtest=OptionsCallPutPositionRepositoryIT -DfailIfNoTests=false`
Expected: PASS (all 4 tests). Hibernate `create-drop` builds the new table from the entity.

- [ ] **Step 5: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/entity/OptionsCallPutPosition.java \
        eagleeye-domain/src/main/java/com/eagleeye/domain/repository/OptionsCallPutPositionRepository.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/OptionsCallPutPositionRepositoryIT.java
git commit -m "feat(domain): add OptionsCallPutPosition entity + repository"
```

---

## Task 2: Full callsAndPuts pipeline — DTO, parser, collection service

**Files:**
- Modify: `eagleeye-domain/src/main/java/com/eagleeye/domain/dto/OptionsCallPutDto.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TaifexParser.java` (`parseCallPut`, ~lines 87-132)
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/CollectionService.java` (fields/constructor + `upsertOptionsCallPut`, ~lines 26-39, 137-147)
- Test (modify): `eagleeye-collector/src/test/java/com/eagleeye/collector/taifex/TaifexParserTest.java`
- Test (modify): `eagleeye-collector/src/test/java/com/eagleeye/collector/service/CollectionServiceTest.java`

- [ ] **Step 1: Rewrite the DTO as a `PositionDto` wrapper**

Replace the entire contents of `eagleeye-domain/src/main/java/com/eagleeye/domain/dto/OptionsCallPutDto.java`:

```java
package com.eagleeye.domain.dto;

import com.eagleeye.domain.entity.RightType;

/**
 * One callsAndPuts row: the full 12-column {@link PositionDto} plus its call/put marker.
 */
public record OptionsCallPutDto(PositionDto position, RightType rightType) {}
```

- [ ] **Step 2: Update the parser test to expect all 12 columns**

In `eagleeye-collector/src/test/java/com/eagleeye/collector/taifex/TaifexParserTest.java`:

Replace the test method `parseCallPut_extracts_call_and_put_net_value_per_trader` (lines 87-113) with:

```java
    @Test
    void parseCallPut_extracts_all_twelve_columns_per_trader_split_by_right() {
        // 12 data columns: trLongVol, trLongVal, trShortVol, trShortVal, trNetVol, trNetVal,
        //                  oiLongVol, oiLongVal, oiShortVol, oiShortVal, oiNetVol, oiNetVal
        String html = buildTable(
                callPutRow(true, "1", "TXO", "CALL", "Dealers",
                        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "-953601"),
                callPutTraderRow("Investment Trust",
                        "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "-82826"),
                callPutTraderRow("FINI",
                        "73728", "760472", "73387", "834188", "341", "-73717",
                        "10691", "1996813", "9137", "1512653", "1554", "569039"),
                callPutRow(false, null, null, "PUT", "Dealers",
                        "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "-57345"),
                callPutTraderRow("Investment Trust",
                        "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "-9643"),
                callPutTraderRow("FINI",
                        "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "101887")
        );

        List<OptionsCallPutDto> results = parser.parseCallPut(html, TEST_DATE);

        assertThat(results).hasSize(6);

        OptionsCallPutDto finiCall = findCallPut(results, TraderType.FINI, RightType.CALL);
        PositionDto p = finiCall.position();
        assertThat(p.contract()).isEqualTo("TXO");
        assertThat(p.tradingLongVolume()).isEqualTo(73728L);
        assertThat(p.oiLongValue()).isEqualTo(1996813L);
        assertThat(p.oiShortValue()).isEqualTo(1512653L);   // 賣方未平倉契約金額
        assertThat(p.oiNetValue()).isEqualTo(569039L);        // 買賣差額契約金額 — headline metric

        OptionsCallPutDto finiPut = findCallPut(results, TraderType.FINI, RightType.PUT);
        assertThat(finiPut.position().oiNetValue()).isEqualTo(101887L);

        OptionsCallPutDto dealerCall = findCallPut(results, TraderType.DEALER, RightType.CALL);
        assertThat(dealerCall.position().oiNetValue()).isEqualTo(-953601L);
    }
```

Replace the `findCallPut` helper (lines 327-332) with:

```java
    private OptionsCallPutDto findCallPut(List<OptionsCallPutDto> list, TraderType type, RightType right) {
        return list.stream()
                .filter(p -> p.position().traderType() == type && p.rightType() == right)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No call/put position for " + type + "/" + right));
    }
```

Replace the three HTML builders `callPutRow`, `callPutTraderRow`, `appendCallPutCells` (lines 303-325) with varargs versions that carry all 12 cells:

```java
    /**
     * Builds a call/put trader row carrying 12 data columns. The first row of a contract
     * adds SN + Contract (rowspan=6); a row that begins a CALL/PUT section adds the
     * right-type cell (rowspan=3).
     */
    private String callPutRow(boolean isFirstInContract, String sn, String contract,
                              String rightLabel, String trader, String... vals) {
        StringBuilder sb = new StringBuilder("<tr>");
        if (isFirstInContract) {
            sb.append("<td rowspan='6'>").append(sn).append("</td>");
            sb.append("<td rowspan='6'>").append(contract).append("</td>");
        }
        sb.append("<td rowspan='3'>").append(rightLabel).append("</td>");
        appendCallPutCells(sb, trader, vals);
        return sb.append("</tr>").toString();
    }

    private String callPutTraderRow(String trader, String... vals) {
        StringBuilder sb = new StringBuilder("<tr>");
        appendCallPutCells(sb, trader, vals);
        return sb.append("</tr>").toString();
    }

    private void appendCallPutCells(StringBuilder sb, String trader, String... vals) {
        sb.append("<td>").append(trader).append("</td>");
        for (String v : vals) sb.append("<td>").append(v).append("</td>");
    }
```

- [ ] **Step 3: Run the parser test to verify it fails**

Run: `mvn -q -pl eagleeye-collector -am test -Dtest=TaifexParserTest -DfailIfNoTests=false`
Expected: COMPILATION FAILURE or test FAIL — `OptionsCallPutDto` no longer has `.contract()`/`.oiNetValue()`/`.traderType()`; `parseCallPut` still emits the old flat DTO.

- [ ] **Step 4: Rewrite `parseCallPut` to keep all 12 columns**

In `eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TaifexParser.java`, replace the body of `parseCallPut` (lines 87-132). Keep the existing javadoc above it. New method:

```java
    public List<OptionsCallPutDto> parseCallPut(String html, LocalDate tradeDate) {
        Document doc = Jsoup.parse(html);
        List<OptionsCallPutDto> results = new ArrayList<>();

        Element table = findDataTable(doc);
        if (table == null) {
            log.warn("Could not find call/put data table in TAIFEX HTML for date {}", tradeDate);
            return results;
        }

        String currentContract = null;
        RightType currentRight = null;

        for (Element row : table.select("tr")) {
            String contract = findContractCodeRowspan(row, 6);
            if (contract != null) currentContract = contract;

            RightType right = findRightType(row);
            if (right != null) currentRight = right;

            String traderLabel = findTraderLabel(row.select("td"));
            if (traderLabel == null || currentContract == null || currentRight == null) continue;

            TraderType traderType;
            try {
                traderType = TraderType.fromLabel(traderLabel);
            } catch (IllegalArgumentException e) {
                log.debug("Unknown trader label '{}' for contract {}", traderLabel, currentContract);
                continue;
            }

            long[] all = extractNumbers(row.select("td"));
            if (all.length < 12) {
                log.warn("Expected ≥12 numeric cells, got {} for {}/{}/{} on {}",
                        all.length, currentContract, currentRight, traderType, tradeDate);
                continue;
            }

            // Take the last 12 — the Dealer row carries an extra leading SN number
            long[] n = Arrays.copyOfRange(all, all.length - 12, all.length);
            PositionDto position = new PositionDto(tradeDate, currentContract, traderType,
                    n[0],  n[1],   // trading long  vol, val
                    n[2],  n[3],   // trading short vol, val
                    n[4],  n[5],   // trading net   vol, val
                    n[6],  n[7],   // OI     long   vol, val
                    n[8],  n[9],   // OI     short  vol, val
                    n[10], n[11]); // OI     net    vol, val
            results.add(new OptionsCallPutDto(position, currentRight));
        }

        log.info("Parsed {} call/put positions for {}", results.size(), tradeDate);
        return results;
    }
```

(`PositionDto`, `Arrays`, `RightType`, `OptionsCallPutDto` are already imported in this file.)

- [ ] **Step 5: Run the parser test to verify it passes**

Run: `mvn -q -pl eagleeye-collector -am test -Dtest=TaifexParserTest -DfailIfNoTests=false`
Expected: PASS. (`CollectionService` may not compile yet — that's fixed next; if the reactor compiles test sources of the whole module it will fail to compile `CollectionServiceTest`. If so, proceed to Step 6 first, then run Steps 5+7 together. See note below.)

> **Note:** Because `TaifexParserTest` and `CollectionServiceTest` are in the same module, the module's test compilation is all-or-nothing. Do Steps 6 (service + its test) before re-running tests if compilation blocks. The logical TDD order is preserved; the run just batches.

- [ ] **Step 6: Update `CollectionService` to write the new entity**

In `eagleeye-collector/src/main/java/com/eagleeye/collector/service/CollectionService.java`:

Add imports (with the existing imports near the top):

```java
import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
```

Remove the now-unused import `import com.eagleeye.domain.entity.RightType;` (line 10).

Add a field and constructor parameter. Replace the field block + constructor (lines 26-39) with:

```java
    private final TaifexClient taifexClient;
    private final TaifexParser taifexParser;
    private final FuturesPositionRepository futuresRepo;
    private final OptionsPositionRepository optionsRepo;
    private final OptionsCallPutPositionRepository callPutRepo;

    public CollectionService(TaifexClient taifexClient,
                             TaifexParser taifexParser,
                             FuturesPositionRepository futuresRepo,
                             OptionsPositionRepository optionsRepo,
                             OptionsCallPutPositionRepository callPutRepo) {
        this.taifexClient = taifexClient;
        this.taifexParser = taifexParser;
        this.futuresRepo = futuresRepo;
        this.optionsRepo = optionsRepo;
        this.callPutRepo = callPutRepo;
    }
```

Replace `upsertOptionsCallPut` (lines 137-147) with:

```java
    private void upsertOptionsCallPut(OptionsCallPutDto dto, LocalDate date) {
        PositionDto p = dto.position();
        OptionsCallPutPosition pos = callPutRepo
                .findByTradeDateAndContractAndTraderTypeAndRightType(
                        date, p.contract(), p.traderType(), dto.rightType())
                .orElseGet(() -> new OptionsCallPutPosition(
                        date, p.contract(), p.traderType(), dto.rightType()));
        applyDto(pos, p);
        callPutRepo.save(pos);
    }
```

(`applyDto(AbstractMarketPosition, PositionDto)` already exists and accepts the new entity, which extends `AbstractMarketPosition`.)

- [ ] **Step 7: Update `CollectionServiceTest`**

In `eagleeye-collector/src/test/java/com/eagleeye/collector/service/CollectionServiceTest.java`:

Add the import:

```java
import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
```

Add a mock alongside the others (after line 35 `@Mock OptionsPositionRepository optionsRepo;`):

```java
    @Mock OptionsCallPutPositionRepository callPutRepo;
```

(`@InjectMocks CollectionService service` wires it by type automatically.)

Replace the `cpDto` helper (lines 240-242) with the wrapper form:

```java
    private OptionsCallPutDto cpDto(String contract, TraderType traderType, RightType rightType, long oiNetValue) {
        PositionDto p = new PositionDto(DATE, contract, traderType,
                0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, oiNetValue);
        return new OptionsCallPutDto(p, rightType);
    }
```

In `collectAll_collected_returnsFuturesAndOptionsCount` (parseCallPut returns 2 DTOs → upsert runs), add these stubs right after the `parseCallPut` stub (after line 79):

```java
        when(callPutRepo.findByTradeDateAndContractAndTraderTypeAndRightType(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(callPutRepo.save(any())).thenAnswer(i -> i.getArgument(0));
```

Replace the test `collectOptions_appliesCallPutNetValues_toOptionsRow` (lines 154-175) with one that verifies the full breakdown lands in the new table:

```java
    @Test
    void collectOptions_persistsFullCallPutBreakdown() {
        when(taifexClient.fetchOptionsHtml(DATE)).thenReturn("<html>data</html>");
        when(taifexParser.isNoDataPage("<html>data</html>")).thenReturn(false);
        when(taifexParser.parse("<html>data</html>", DATE)).thenReturn(List.of(dto("TXO", TraderType.FINI)));
        when(optionsRepo.findByTradeDateAndContractAndTraderType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(optionsRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        when(taifexClient.fetchOptionsCallPutHtml(DATE)).thenReturn("<html>callput</html>");
        when(taifexParser.parseCallPut("<html>callput</html>", DATE)).thenReturn(List.of(
                cpDto("TXO", TraderType.FINI, RightType.CALL, 569039L),
                cpDto("TXO", TraderType.FINI, RightType.PUT, 101887L)
        ));
        when(callPutRepo.findByTradeDateAndContractAndTraderTypeAndRightType(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(callPutRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.collectOptions(DATE);

        verify(callPutRepo).save(argThat(p ->
                "TXO".equals(p.getContract())
                && p.getRightType() == RightType.CALL
                && p.getOiNetValue() == 569039L));
        verify(callPutRepo).save(argThat(p ->
                p.getRightType() == RightType.PUT
                && p.getOiNetValue() == 101887L));
    }
```

- [ ] **Step 8: Run the collector module tests**

Run: `mvn -q -pl eagleeye-collector -am test -Dtest=TaifexParserTest,CollectionServiceTest,OptionsCallPutPositionRepositoryIT -DfailIfNoTests=false`
Expected: PASS for all three.

- [ ] **Step 9: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/dto/OptionsCallPutDto.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TaifexParser.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/service/CollectionService.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/taifex/TaifexParserTest.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/CollectionServiceTest.java
git commit -m "feat(collector): persist full callsAndPuts breakdown per trader/right"
```

---

## Task 3: Dashboard reads net value from the new repository

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java`
- Test (modify): `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java`

- [ ] **Step 1: Update the dashboard test**

In `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java`:

Add a mock after line 24 (`@Mock OptionsPositionRepository optionsRepo;`):

```java
    @Mock OptionsCallPutPositionRepository callPutRepo;
```

Update the constructor call in `setUp` (line 31) to:

```java
        service = new DashboardService(taiexRepo, flowRepo, futuresRepo, optionsRepo, callPutRepo, marginRepo);
```

Add a helper after the `options(...)` helper (after line 61):

```java
    OptionsCallPutPosition callPut(LocalDate date, RightType right, long oiNetValue) {
        OptionsCallPutPosition cp = new OptionsCallPutPosition(date, "TXO", TraderType.FINI, right);
        cp.setOiNetValue(oiNetValue);
        return cp;
    }
```

Replace `buildViewModel_computesOptionsCallPutNetValue` (lines 141-157) with:

```java
    @Test
    void buildViewModel_computesOptionsCallPutNetValue() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(callPutRepo.findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
                eq("TXO"), eq(TraderType.FINI), eq(RightType.CALL), any(), any()))
            .thenReturn(List.of(callPut(d, RightType.CALL, 569_039L)));
        when(callPutRepo.findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
                eq("TXO"), eq(TraderType.FINI), eq(RightType.PUT), any(), any()))
            .thenReturn(List.of(callPut(d, RightType.PUT, 101_887L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.optionsCallNetValue()).containsExactly(569_039L);
        assertThat(vm.optionsPutNetValue()).containsExactly(101_887L);
    }
```

(The test already uses wildcard imports `com.eagleeye.domain.entity.*` and `com.eagleeye.domain.repository.*`, so `OptionsCallPutPosition`, `RightType`, and `OptionsCallPutPositionRepository` resolve without new imports.)

- [ ] **Step 2: Run the dashboard test to verify it fails**

Run: `mvn -q -pl eagleeye-web -am test -Dtest=DashboardServiceTest -DfailIfNoTests=false`
Expected: COMPILATION FAILURE — `DashboardService` constructor still takes 5 args, no `callPutRepo` field/finder usage.

- [ ] **Step 3: Wire `DashboardService` to the new repository**

In `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java`:

Add imports (alongside existing entity/repository imports):

```java
import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
```

Add field + constructor param. Replace the field block + constructor (lines 31-47) with:

```java
    private final TaiexIndexRepository taiexRepo;
    private final InstitutionalFlowRepository flowRepo;
    private final FuturesPositionRepository futuresRepo;
    private final OptionsPositionRepository optionsRepo;
    private final OptionsCallPutPositionRepository callPutRepo;
    private final MarginTransactionRepository marginRepo;

    public DashboardService(TaiexIndexRepository taiexRepo,
                            InstitutionalFlowRepository flowRepo,
                            FuturesPositionRepository futuresRepo,
                            OptionsPositionRepository optionsRepo,
                            OptionsCallPutPositionRepository callPutRepo,
                            MarginTransactionRepository marginRepo) {
        this.taiexRepo = taiexRepo;
        this.flowRepo = flowRepo;
        this.futuresRepo = futuresRepo;
        this.optionsRepo = optionsRepo;
        this.callPutRepo = callPutRepo;
        this.marginRepo = marginRepo;
    }
```

After the `optionsList` query (line 57-58), add the two call/put series queries:

```java
        List<OptionsCallPutPosition> callList = callPutRepo
            .findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
                "TXO", TraderType.FINI, RightType.CALL, from, to);
        List<OptionsCallPutPosition> putList = callPutRepo
            .findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
                "TXO", TraderType.FINI, RightType.PUT, from, to);
```

After the `optMap` line (line 64), add:

```java
        Map<LocalDate, OptionsCallPutPosition> callMap = indexByDate(callList, OptionsCallPutPosition::getTradeDate);
        Map<LocalDate, OptionsCallPutPosition> putMap  = indexByDate(putList,  OptionsCallPutPosition::getTradeDate);
```

Inside the `for (LocalDate date : dates)` loop, after `OptionsPosition op = optMap.get(date);` (line 94), add:

```java
            OptionsCallPutPosition cp = callMap.get(date);
            OptionsCallPutPosition pp = putMap.get(date);
```

Replace the two net-value lines (lines 112-113):

```java
            optionsCallNetValue.add(op != null ? op.getOiNetValueCall() : null);
            optionsPutNetValue.add(op != null ? op.getOiNetValuePut()  : null);
```

with:

```java
            optionsCallNetValue.add(cp != null ? cp.getOiNetValue() : null);
            optionsPutNetValue.add(pp != null ? pp.getOiNetValue() : null);
```

(The `optMap`/`op` lookups remain — they still feed `optionsCallOI`/`optionsPutOI` from `oiLongVolume`/`oiShortVolume`.)

- [ ] **Step 4: Run the dashboard test to verify it passes**

Run: `mvn -q -pl eagleeye-web -am test -Dtest=DashboardServiceTest -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java \
        eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java
git commit -m "feat(web): read call/put net value from OptionsCallPutPosition"
```

---

## Task 4: Drop the now-unused columns from `OptionsPosition`

**Files:**
- Modify: `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/OptionsPosition.java`

- [ ] **Step 1: Revert `OptionsPosition` to a plain entity**

Replace the entire contents of `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/OptionsPosition.java`:

```java
package com.eagleeye.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

@Entity
@Table(
    name = "options_position",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_options_position",
        columnNames = {"trade_date", "contract", "trader_type"}
    )
)
public class OptionsPosition extends AbstractMarketPosition {

    protected OptionsPosition() {}

    public OptionsPosition(LocalDate tradeDate, String contract, TraderType traderType) {
        super(tradeDate, contract, traderType);
    }
}
```

> The physical columns `oi_net_value_call` / `oi_net_value_put` remain in the existing
> file/SQLite DBs (Hibernate `ddl-auto: update` never drops columns) and are intentionally
> left unused. No code references them after Tasks 2-3.

- [ ] **Step 2: Full build to confirm no dangling references**

Run: `mvn -q clean test`
Expected: BUILD SUCCESS across all modules. (A failure here naming `getOiNetValueCall`/`setOiNetValueCall` means a reference was missed in Task 2 or 3 — fix it before committing.)

- [ ] **Step 3: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/entity/OptionsPosition.java
git commit -m "refactor(domain): drop unused call/put net-value columns from OptionsPosition"
```

---

## Task 5: Backfill the new table (manual, optional)

No code. To repopulate history with the full 12-column data (the old two columns held only the net value), run the existing combined backfill against the collector app. Example:

```bash
mvn -q -pl eagleeye-collector -am spring-boot:run \
  -Dspring-boot.run.profiles=prod \
  -Dspring-boot.run.arguments="--combined.backfill.from=2026-01-01"
```

`CombinedBackfillRunner` → `collectionService.collectAll(day)` → `processOptionsCallPut` now writes `OptionsCallPutPosition` rows. Verify a sample afterwards:

```bash
sqlite3 ~/.eagleeye/data/eagleeye.db \
  "SELECT date(trade_date/1000+8*3600,'unixepoch') d, right_type, oi_short_value, oi_net_value
   FROM options_call_put_position
   WHERE contract='TXO' AND trader_type='FINI'
   ORDER BY trade_date DESC, right_type LIMIT 6;"
```

Expected (e.g. 2026-05-28 CALL): `oi_short_value=1512653`, `oi_net_value=569039`.

---

## Self-review notes

- **Spec coverage:** §1 table → Task 1; §1 OptionsPosition revert → Task 4; §2 DTO+parser → Task 2; §3 collection service → Task 2; §4 repository finders → Task 1; §5 dashboard (net value moves, OI stays on OptionsPosition) → Task 3; §6 backfill → Task 5; §7 tests → Tasks 1-3.
- **Type consistency:** finder names identical across entity/repo/service/dashboard/tests (`findByTradeDateAndContractAndTraderTypeAndRightType`, `findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc`). DTO accessor is `position()` + `rightType()` everywhere. Constructor arg order `(taiex, flow, futures, options, callPut, margin)` matches between `DashboardService` and its test.
- **No placeholders.**
