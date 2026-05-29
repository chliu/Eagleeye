# Java 25 Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert three flat `*Result` records into sealed type hierarchies with variant records, and apply a handful of small Java 25 idiom upgrades (`Optional.map().orElse()`, `Math.clamp`, `String.formatted()`). All changes are behavior-preserving.

**Architecture:** Replace `(status: enum, payload-fields)` records with `sealed interface { Collected, NoData, Error }`. Call sites switch from `result.status()` enum patterns to type patterns. The `CollectionStatus` enum is retained because the unrelated `CollectResult` pipeline-status type still uses it.

**Tech Stack:** Java 25, Spring Boot 4.0.3, JPA, Spring Shell, JUnit 5 + AssertJ + Mockito, Maven multi-module.

**Reference spec:** `docs/superpowers/specs/2026-05-29-java25-modernization-design.md`

---

## Task 0: Baseline

**Files:** none

- [ ] **Step 1: Verify clean working tree**

Run: `git status`
Expected: clean (after the spec commit `015d3ca`).

- [ ] **Step 2: Run the full test suite as baseline**

Run: `mvn -q clean test`
Expected: BUILD SUCCESS. All tests pass. If anything is red, **stop** and report.

---

## Task 1: Sealed `CollectionResult`

**Goal:** Convert `CollectionResult` from a flat record + status enum into a sealed hierarchy. Update `CollectionService`, `BackfillRunner`, and `CollectionServiceTest`.

**Files:**
- Rewrite: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/CollectionResult.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/CollectionService.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/BackfillRunner.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/CollectionServiceTest.java`

- [ ] **Step 1: Rewrite `CollectionResult.java`**

Replace the entire file contents with:

```java
package com.eagleeye.collector.service;

import java.time.LocalDate;
import java.util.Objects;

public sealed interface CollectionResult {

    LocalDate date();

    record Collected(LocalDate date, int futuresCount, int optionsCount) implements CollectionResult {}

    record NoData(LocalDate date) implements CollectionResult {}

    record Error(LocalDate date, String message) implements CollectionResult {
        public Error {
            Objects.requireNonNull(message, "message");
        }
    }
}
```

Note: the old static factories (`collected`, `noData`, `error`) and `isTradeDay()` are gone. Call sites are updated next.

- [ ] **Step 2: Update `CollectionService.collectAll()` return statements**

In `CollectionService.java`, change three lines:

Line 48 — `return CollectionResult.noData(date);` becomes:
```java
return new CollectionResult.NoData(date);
```

Line 57 — `return CollectionResult.collected(date, futures, options);` becomes:
```java
return new CollectionResult.Collected(date, futures, options);
```

Line 61 — `return CollectionResult.error(date, e.getMessage());` becomes:
```java
return new CollectionResult.Error(date, e.getMessage());
```

- [ ] **Step 3: Update `BackfillRunner` switch in `run()`**

In `BackfillRunner.java`, replace the switch at lines 76–82 (currently `switch (result.status()) { ... }`) with:

```java
switch (result) {
    case CollectionResult.Collected c -> printRow(current, "OK",
            c.futuresCount() + " rows",
            c.optionsCount() + " rows");
    case CollectionResult.NoData n    -> printRow(current, "HOLIDAY", "-", "-");
    case CollectionResult.Error e     -> printRow(current, "ERROR", e.message(), "");
}
```

- [ ] **Step 4: Update `BackfillRunner.printSummary` aggregation**

Replace lines 98–102 with:

```java
long tradeDays = results.stream().filter(CollectionResult.Collected.class::isInstance).count();
long holidays  = results.stream().filter(CollectionResult.NoData.class::isInstance).count();
long errors    = results.stream().filter(CollectionResult.Error.class::isInstance).count();
long totalFut  = results.stream()
        .mapToLong(r -> r instanceof CollectionResult.Collected c ? c.futuresCount() : 0L)
        .sum();
long totalOpt  = results.stream()
        .mapToLong(r -> r instanceof CollectionResult.Collected c ? c.optionsCount() : 0L)
        .sum();
```

- [ ] **Step 5: Remove the now-unused `CollectionStatus` import from `BackfillRunner`**

In `BackfillRunner.java`, delete the line:
```java
import com.eagleeye.collector.service.CollectionStatus;
```
(`BackfillRunner` no longer references `CollectionStatus` directly.)

- [ ] **Step 6: Update `CollectionServiceTest` assertions**

In `CollectionServiceTest.java`:

Replace lines 46–48 (`collectAll_noData_whenFuturesPageHasNoData`):
```java
assertThat(result).isInstanceOf(CollectionResult.NoData.class);
assertThat(result.date()).isEqualTo(DATE);
```

Replace lines 75–77 (`collectAll_collected_returnsFuturesAndOptionsCount`):
```java
assertThat(result).isInstanceOf(CollectionResult.Collected.class);
CollectionResult.Collected collected = (CollectionResult.Collected) result;
assertThat(collected.futuresCount()).isEqualTo(2);
assertThat(collected.optionsCount()).isEqualTo(3);
```

Replace lines 86–87 (`collectAll_error_whenClientThrows`):
```java
assertThat(result).isInstanceOf(CollectionResult.Error.class);
CollectionResult.Error error = (CollectionResult.Error) result;
assertThat(error.message()).contains("connection refused");
```

Remove the now-unused import `import com.eagleeye.collector.service.CollectionStatus;` if Maven complains (the IDE will flag it; remove if present).

- [ ] **Step 7: Run the collector tests**

Run: `mvn -q -pl eagleeye-collector test`
Expected: BUILD SUCCESS.

If failures occur, read the error carefully — most likely a missed call site. Search:
```
git grep -n "CollectionResult\.\(collected\|noData\|error\|isTradeDay\)\|CollectionStatus\.\(COLLECTED\|NO_DATA\|ERROR\)" eagleeye-collector/src
```
Fix and re-run.

- [ ] **Step 8: Run the full suite to confirm no other modules broke**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/service/CollectionResult.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/service/CollectionService.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/runner/BackfillRunner.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/CollectionServiceTest.java
git commit -m "refactor(collector): seal CollectionResult into Collected/NoData/Error variants

Replaces flat record + CollectionStatus pattern with a sealed type
hierarchy. Compiler now enforces exhaustive switches on result type.
CollectionStatus enum retained for unrelated CollectResult."
```

---

## Task 2: Sealed `DateCollectionResult`

**Goal:** Same transform for `DateCollectionResult`, which is shared by three services and four call-site classes.

**Files:**
- Rewrite: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/DateCollectionResult.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/InstitutionalFlowService.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarginTransactionService.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/FuturesAhService.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/FuturesAhBackfillRunner.java`
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/InstitutionalFlowCommands.java`
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarginTransactionCommands.java`
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/FuturesAhCommands.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/InstitutionalFlowServiceTest.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/InstitutionalFlowServiceIT.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarginTransactionServiceTest.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarginTransactionServiceIT.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/FuturesAhServiceTest.java`

- [ ] **Step 1: Rewrite `DateCollectionResult.java`**

Replace the entire file contents with:

```java
package com.eagleeye.collector.service;

import java.time.LocalDate;
import java.util.Objects;

public sealed interface DateCollectionResult {

    LocalDate tradeDate();

    record Collected(LocalDate tradeDate) implements DateCollectionResult {}

    record NoData(LocalDate tradeDate) implements DateCollectionResult {}

    record Error(LocalDate tradeDate, String message) implements DateCollectionResult {
        public Error {
            Objects.requireNonNull(message, "message");
        }
    }
}
```

- [ ] **Step 2: Update `InstitutionalFlowService.collectDate()` returns**

In `InstitutionalFlowService.java`:

Line 40: `return DateCollectionResult.noData(date);` → `return new DateCollectionResult.NoData(date);`
Line 44: `return DateCollectionResult.collected(date);` → `return new DateCollectionResult.Collected(date);`
Line 47: `return DateCollectionResult.error(date, e.getMessage());` → `return new DateCollectionResult.Error(date, e.getMessage());`

- [ ] **Step 3: Update `MarginTransactionService.collectDate()` returns**

In `MarginTransactionService.java`:

Line 40: `return DateCollectionResult.noData(date);` → `return new DateCollectionResult.NoData(date);`
Line 44: `return DateCollectionResult.collected(date);` → `return new DateCollectionResult.Collected(date);`
Line 47: `return DateCollectionResult.error(date, e.getMessage());` → `return new DateCollectionResult.Error(date, e.getMessage());`

- [ ] **Step 4: Update `FuturesAhService.collectDate()` returns**

In `FuturesAhService.java`:

Line 39: `return DateCollectionResult.noData(date);` → `return new DateCollectionResult.NoData(date);`
Line 44: `return DateCollectionResult.collected(date);` → `return new DateCollectionResult.Collected(date);`
Line 47: `return DateCollectionResult.error(date, e.getMessage());` → `return new DateCollectionResult.Error(date, e.getMessage());`

- [ ] **Step 5: Update `FuturesAhBackfillRunner.printRow()` switch**

In `FuturesAhBackfillRunner.java`, replace the switch at lines 83–87 with:

```java
String status = switch (r) {
    case DateCollectionResult.Collected c -> "ok";
    case DateCollectionResult.NoData n    -> "no data";
    case DateCollectionResult.Error e     -> "ERROR: " + e.message();
};
```

- [ ] **Step 6: Update `FuturesAhBackfillRunner.printSummary()` aggregation**

Replace lines 92–94 with:

```java
long collected = results.stream().filter(DateCollectionResult.Collected.class::isInstance).count();
long noData    = results.stream().filter(DateCollectionResult.NoData.class::isInstance).count();
long errors    = results.stream().filter(DateCollectionResult.Error.class::isInstance).count();
```

- [ ] **Step 7: Remove `CollectionStatus` import from `FuturesAhBackfillRunner`**

Delete the line:
```java
import com.eagleeye.collector.service.CollectionStatus;
```

- [ ] **Step 8: Update `InstitutionalFlowCommands.formatResult()`**

In `InstitutionalFlowCommands.java`, replace the switch at lines 84–88 with:

```java
return switch (r) {
    case DateCollectionResult.Collected c -> c.tradeDate() + " — collected";
    case DateCollectionResult.NoData n    -> n.tradeDate() + " — no data";
    case DateCollectionResult.Error e     -> e.tradeDate() + " — ERROR: " + e.message();
};
```

- [ ] **Step 9: Update `MarginTransactionCommands.formatResult()`**

In `MarginTransactionCommands.java`, replace the switch at lines 82–86 with:

```java
return switch (r) {
    case DateCollectionResult.Collected c -> c.tradeDate() + " — collected";
    case DateCollectionResult.NoData n    -> n.tradeDate() + " — no data";
    case DateCollectionResult.Error e     -> e.tradeDate() + " — ERROR: " + e.message();
};
```

- [ ] **Step 10: Update `FuturesAhCommands.collectAh()` switch**

In `FuturesAhCommands.java`, replace the switch at lines 26–30 with:

```java
return switch (result) {
    case DateCollectionResult.Collected c -> c.tradeDate() + " — after-hours futures collected";
    case DateCollectionResult.NoData n    -> n.tradeDate() + " — no data";
    case DateCollectionResult.Error e     -> e.tradeDate() + " — ERROR: " + e.message();
};
```

Note: the variant's own `tradeDate()` equals the outer `d` (the service was called with `d`), so this is behavior-preserving. Using the pattern binding keeps the variable consistently used in every arm.

- [ ] **Step 11: Update `InstitutionalFlowServiceTest` assertions**

In `InstitutionalFlowServiceTest.java`:

Lines 48–49 (`collectDate_success_savesAndReturnsCollected`) become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
assertThat(result.tradeDate()).isEqualTo(DATE);
```

Line 60 (`collectDate_noData_returnsNoData`) becomes:
```java
assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
```

Lines 70–71 (`collectDate_clientThrows_returnsError`) become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
DateCollectionResult.Error error = (DateCollectionResult.Error) result;
assertThat(error.message()).contains("timeout");
```

Remove the import `com.eagleeye.collector.service.CollectionStatus;` if present.

- [ ] **Step 12: Update `MarginTransactionServiceTest` assertions**

In `MarginTransactionServiceTest.java`:

Lines 48–49 become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
assertThat(result.tradeDate()).isEqualTo(DATE);
```

Line 60 becomes:
```java
assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
```

Lines 70–71 become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
DateCollectionResult.Error error = (DateCollectionResult.Error) result;
assertThat(error.message()).contains("timeout");
```

Remove the `CollectionStatus` import if present.

- [ ] **Step 13: Update `FuturesAhServiceTest` assertions**

In `FuturesAhServiceTest.java`:

Lines 41–42 (`collectDate_noData_whenNoDataPage`) become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
assertThat(result.tradeDate()).isEqualTo(DATE);
```

Lines 61–62 (`collectDate_collected_whenDataFound`) become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
assertThat(result.tradeDate()).isEqualTo(DATE);
```

Lines 72–73 (`collectDate_error_whenClientThrows`) become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
DateCollectionResult.Error error = (DateCollectionResult.Error) result;
assertThat(error.message()).contains("timeout");
```

Remove the `CollectionStatus` import if present.

- [ ] **Step 14: Update `InstitutionalFlowServiceIT` assertions**

In `InstitutionalFlowServiceIT.java`:

Delete the import `import com.eagleeye.collector.service.CollectionStatus;` (line 4).

Lines 63–64 (`collectDate_returnsCollected_andPersistsAllNineFields`) become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
assertThat(result.tradeDate()).isEqualTo(DATE);
```

Line 113 (`collectDate_returnsNoData_andPersistsNothing_whenApiReturnsNoStat`) becomes:
```java
assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
```

Lines 124–125 (`collectDate_returnsError_andPersistsNothing_whenClientThrows`) become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
DateCollectionResult.Error error = (DateCollectionResult.Error) result;
assertThat(error.message()).contains("connection timeout");
```

- [ ] **Step 14b: Update `MarginTransactionServiceIT` assertions**

In `MarginTransactionServiceIT.java`:

Delete the import `import com.eagleeye.collector.service.CollectionStatus;` (line 4).

Lines 67–68 (`collectDate_returnsCollected_andPersistsAllTenFields`) become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
assertThat(result.tradeDate()).isEqualTo(DATE);
```

Line 121 (`collectDate_returnsNoData_andPersistsNothing_whenApiReturnsNoStat`) becomes:
```java
assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
```

Lines 133–134 (`collectDate_returnsError_andPersistsNothing_whenClientThrows`) become:
```java
assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
DateCollectionResult.Error error = (DateCollectionResult.Error) result;
assertThat(error.message()).contains("connection timeout");
```

- [ ] **Step 15: Run the collector tests**

Run: `mvn -q -pl eagleeye-collector test`
Expected: BUILD SUCCESS.

- [ ] **Step 16: Run the shell tests**

Run: `mvn -q -pl eagleeye-shell test`
Expected: BUILD SUCCESS.

- [ ] **Step 17: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 18: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/service/DateCollectionResult.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/service/InstitutionalFlowService.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarginTransactionService.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/service/FuturesAhService.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/runner/FuturesAhBackfillRunner.java \
        eagleeye-shell/src/main/java/com/eagleeye/shell/commands/InstitutionalFlowCommands.java \
        eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarginTransactionCommands.java \
        eagleeye-shell/src/main/java/com/eagleeye/shell/commands/FuturesAhCommands.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/InstitutionalFlowServiceTest.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/InstitutionalFlowServiceIT.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarginTransactionServiceTest.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarginTransactionServiceIT.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/FuturesAhServiceTest.java
git commit -m "refactor(collector,shell): seal DateCollectionResult variants

Same transform as CollectionResult: sealed interface with Collected,
NoData, Error variants. Updates three services, FuturesAhBackfillRunner,
three shell commands, and five test files."
```

---

## Task 3: Sealed `MarketIndexCollectionResult`

**Goal:** Same transform for `MarketIndexCollectionResult`. Keyed by `YearMonth`, with `barsCount` on the `Collected` variant.

**Files:**
- Rewrite: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexCollectionResult.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexService.java`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/MarketIndexBackfillRunner.java`
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarketIndexCommands.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarketIndexServiceTest.java`

- [ ] **Step 1: Rewrite `MarketIndexCollectionResult.java`**

Replace the entire file contents with:

```java
package com.eagleeye.collector.service;

import java.time.YearMonth;
import java.util.Objects;

public sealed interface MarketIndexCollectionResult {

    YearMonth yearMonth();

    record Collected(YearMonth yearMonth, int barsCount) implements MarketIndexCollectionResult {}

    record NoData(YearMonth yearMonth) implements MarketIndexCollectionResult {}

    record Error(YearMonth yearMonth, String message) implements MarketIndexCollectionResult {
        public Error {
            Objects.requireNonNull(message, "message");
        }
    }
}
```

- [ ] **Step 2: Update `MarketIndexService.collectMonth()` returns**

In `MarketIndexService.java`:

Line 42: `return MarketIndexCollectionResult.noData(yearMonth);` → `return new MarketIndexCollectionResult.NoData(yearMonth);`
Line 60: `return MarketIndexCollectionResult.collected(yearMonth, bars.size());` → `return new MarketIndexCollectionResult.Collected(yearMonth, bars.size());`
Line 64: `return MarketIndexCollectionResult.error(yearMonth, e.getMessage());` → `return new MarketIndexCollectionResult.Error(yearMonth, e.getMessage());`

- [ ] **Step 3: Update `MarketIndexBackfillRunner.printRow()` and `printSummary()`**

In `MarketIndexBackfillRunner.java`:

Replace `printRow` (lines 77–82) with:
```java
private void printRow(MarketIndexCollectionResult result) {
    String label = switch (result) {
        case MarketIndexCollectionResult.Collected c -> "OK     bars: " + c.barsCount();
        case MarketIndexCollectionResult.NoData n    -> "NODATA bars: 0";
        case MarketIndexCollectionResult.Error e     -> "ERROR  " + e.message();
    };
    System.out.printf("  %-8s  %s%n", result.yearMonth(), label);
}
```

Replace `printSummary` body (lines 85–88) with:
```java
long collected = results.stream().filter(MarketIndexCollectionResult.Collected.class::isInstance).count();
long noData    = results.stream().filter(MarketIndexCollectionResult.NoData.class::isInstance).count();
long errors    = results.stream().filter(MarketIndexCollectionResult.Error.class::isInstance).count();
long totalBars = results.stream()
        .mapToLong(r -> r instanceof MarketIndexCollectionResult.Collected c ? c.barsCount() : 0L)
        .sum();
```

- [ ] **Step 4: Remove `CollectionStatus` import from `MarketIndexBackfillRunner`**

Delete:
```java
import com.eagleeye.collector.service.CollectionStatus;
```

- [ ] **Step 5: Update `MarketIndexCommands.formatResult()`**

In `MarketIndexCommands.java`, replace the switch at lines 85–89 with:

```java
return switch (r) {
    case MarketIndexCollectionResult.Collected c -> c.yearMonth() + " — bars: " + c.barsCount();
    case MarketIndexCollectionResult.NoData n    -> n.yearMonth() + " — no data";
    case MarketIndexCollectionResult.Error e     -> e.yearMonth() + " — ERROR: " + e.message();
};
```

- [ ] **Step 6: Update `MarketIndexServiceTest` assertions**

In `MarketIndexServiceTest.java`:

Lines 75–77 (`collectMonth_whenDataPresent_upsertsBarAndReturnsCollected`):
```java
assertThat(result).isInstanceOf(MarketIndexCollectionResult.Collected.class);
MarketIndexCollectionResult.Collected collected = (MarketIndexCollectionResult.Collected) result;
assertThat(collected.barsCount()).isEqualTo(1);
assertThat(collected.yearMonth()).isEqualTo(MARCH_2026);
```

Lines 87–88 (`collectMonth_whenNoData_returnsNoData`):
```java
assertThat(result).isInstanceOf(MarketIndexCollectionResult.NoData.class);
assertThat(result.yearMonth()).isEqualTo(MARCH_2026);
```

Lines 98–99 (`collectMonth_whenClientThrows_returnsError`):
```java
assertThat(result).isInstanceOf(MarketIndexCollectionResult.Error.class);
MarketIndexCollectionResult.Error error = (MarketIndexCollectionResult.Error) result;
assertThat(error.message()).contains("connection timeout");
```

Lines 111–112 (`collectMonthContaining_delegatesToCollectMonth`):
```java
assertThat(result.yearMonth()).isEqualTo(MARCH_2026);
assertThat(result).isInstanceOf(MarketIndexCollectionResult.Collected.class);
```

Remove the `CollectionStatus` import if present.

- [ ] **Step 7: Run the collector tests**

Run: `mvn -q -pl eagleeye-collector test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Run the shell tests**

Run: `mvn -q -pl eagleeye-shell test`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexCollectionResult.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexService.java \
        eagleeye-collector/src/main/java/com/eagleeye/collector/runner/MarketIndexBackfillRunner.java \
        eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarketIndexCommands.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/service/MarketIndexServiceTest.java
git commit -m "refactor(collector,shell): seal MarketIndexCollectionResult variants

Final sealed-hierarchy conversion: YearMonth-keyed result type with
Collected/NoData/Error variants and barsCount on Collected only."
```

---

## Task 4: `Optional.map().orElse()` in shell `list` commands

**Goal:** Replace early-return null-handling with the value-returning `Optional` idiom in three shell `list` commands.

**Files:**
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/InstitutionalFlowCommands.java`
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarketIndexCommands.java`
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarginTransactionCommands.java`

- [ ] **Step 1: Update `InstitutionalFlowCommands.list()`**

In `InstitutionalFlowCommands.java`, replace lines 37–40 (the body of `list`) with:

```java
LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
return repository.findByTradeDate(d)
        .map(flow -> formatter.formatInstitutionalFlow(List.of(flow)))
        .orElse("No data for " + d);
```

The local `Optional<InstitutionalFlow> flow = ...` variable goes away. Remove the now-unused import `import java.util.Optional;` if it's no longer referenced elsewhere in the file.

- [ ] **Step 2: Update `MarketIndexCommands.list()`**

In `MarketIndexCommands.java`, replace lines 37–41 (the body of `list`) with:

```java
LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
return repository.findByTradeDate(d)
        .map(bar -> formatter.formatMarketIndex(List.of(bar)))
        .orElse("No data for " + d);
```

Remove the unused `import java.util.Optional;` if no longer referenced.

- [ ] **Step 3: Update `MarginTransactionCommands.list()`**

In `MarginTransactionCommands.java`, replace lines 37–40 (the body of `list`) with:

```java
LocalDate d = (date == null || date.isEmpty()) ? LocalDate.now() : LocalDate.parse(date);
return repository.findByTradeDate(d)
        .map(tx -> formatter.formatMarginTransaction(List.of(tx)))
        .orElse("No data for " + d);
```

Remove the unused `import java.util.Optional;` if no longer referenced.

- [ ] **Step 4: Run the shell tests**

Run: `mvn -q -pl eagleeye-shell test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add eagleeye-shell/src/main/java/com/eagleeye/shell/commands/InstitutionalFlowCommands.java \
        eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarketIndexCommands.java \
        eagleeye-shell/src/main/java/com/eagleeye/shell/commands/MarginTransactionCommands.java
git commit -m "refactor(shell): use Optional.map().orElse() in list commands

Replaces if-isEmpty-early-return with the value-returning Optional
idiom in three shell 'list' command bodies."
```

---

## Task 5: `Math.clamp` in `DashboardService`

**Goal:** Make the sublist start-index intent explicit.

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java`

- [ ] **Step 1: Replace `Math.max` with `Math.clamp`**

In `DashboardService.java`, change line 74:

From:
```java
int start = Math.max(0, allTradingDates.size() - days);
```
To:
```java
int start = Math.clamp(allTradingDates.size() - days, 0, allTradingDates.size());
```

- [ ] **Step 2: Run the web tests (if any) and the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java
git commit -m "refactor(web): use Math.clamp for dashboard sublist start index

Java 21 idiom. Same behavior (size - days never exceeds size when
days >= 0), explicit range intent."
```

---

## Task 6: `String.formatted()` in `TableFormatter`

**Goal:** Adopt the post-Java-15 string-formatting idiom in one spot.

**Files:**
- Modify: `eagleeye-shell/src/main/java/com/eagleeye/shell/formatter/TableFormatter.java`

- [ ] **Step 1: Replace `String.format` with `.formatted`**

In `TableFormatter.java`, change line 239:

From:
```java
sb.append(String.format(fmt, truncate(cell, widths[i])));
```
To:
```java
sb.append(fmt.formatted(truncate(cell, widths[i])));
```

- [ ] **Step 2: Run the shell tests**

Run: `mvn -q -pl eagleeye-shell test`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add eagleeye-shell/src/main/java/com/eagleeye/shell/formatter/TableFormatter.java
git commit -m "refactor(shell): use String.formatted() in TableFormatter"
```

---

## Task 7: Final verification

- [ ] **Step 1: Run the full suite from a clean state**

Run: `mvn -q clean test`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Confirm `CollectionStatus` is still present and only referenced by `CollectResult`**

Run:
```
git grep -l CollectionStatus -- '*.java'
```
Expected: only `eagleeye-collector/.../service/CollectionStatus.java` and `eagleeye-collector/.../collector/CollectResult.java`. If any of the three sealed `*Result` types, their services, runners, shell commands, or tests still reference `CollectionStatus`, an import was missed — clean it up and amend the relevant commit (or add a small follow-up commit).

- [ ] **Step 3: Confirm no stale factory calls remain**

Run:
```
git grep -nE "CollectionResult\.(collected|noData|error)|DateCollectionResult\.(collected|noData|error)|MarketIndexCollectionResult\.(collected|noData|error)" -- '*.java'
```
Expected: zero matches.

Run:
```
git grep -n "isTradeDay\|isTradeMonth" -- '*.java'
```
Expected: zero matches.

- [ ] **Step 4: Confirm spec invariants**

Open the spec at `docs/superpowers/specs/2026-05-29-java25-modernization-design.md` and verify:
- Three sealed types created ✓
- `CollectionStatus` retained ✓
- Two `Optional` idiom changes applied ✓
- `Math.clamp` applied ✓
- `String.formatted` applied ✓
- Virtual threads, JPA entity records, and `DashboardController` clamp NOT touched ✓

- [ ] **Step 5: Review the commit log**

Run: `git log --oneline main..HEAD`
Expected: 6 commits (Tasks 1–6), each focused on a single concern.

If everything is green, the refactor is complete.
