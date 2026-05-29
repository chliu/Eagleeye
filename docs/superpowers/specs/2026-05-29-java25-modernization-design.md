# Java 25 Modernization Pass — Design

**Date:** 2026-05-29
**Scope:** Type-safety + idiom polish using Java 21–25 language features. Behavior-preserving.

## Goal

Apply modern Java idioms — primarily **sealed type hierarchies with exhaustive pattern matching** — to the small set of places in Eagleeye where the codebase still uses pre-Java-21 patterns. The codebase is already largely modern (records, switch expressions, `var`, modern `instanceof`); this is targeted refinement, not a foundational rewrite.

## Non-Goals

- No behavior change. All observable outputs of the system stay identical.
- No new features. No new dependencies.
- No entity refactoring. JPA entities (`FuturesPosition`, `OptionsPosition`, etc.) stay as-is — they cannot be records.
- No virtual-thread changes to backfill loops. The `Thread.sleep(500)` calls are TAIFEX/TWSE rate-limit pacing, not threading inefficiency. Parallelizing risks IP rate-limit / ban.
- No changes to `DashboardController` clamp logic — that's a UX/API-shape question, not a Java 25 idiom, and is deferred to its own ticket.

---

## Change 1 — Sealed Result Hierarchies *(main change)*

### Problem

Three parallel result types (`CollectionResult`, `DateCollectionResult`, `MarketIndexCollectionResult`) and a `CollectionStatus` enum encode variant state via a flat record + nullable fields:

```java
public enum CollectionStatus { COLLECTED, NO_DATA, ERROR }

public record CollectionResult(
    LocalDate date, int futuresCount, int optionsCount,
    CollectionStatus status, String errorMessage  // both partially-meaningful
) { /* static factories + isTradeDay() */ }
```

- `errorMessage` is null unless `status==ERROR`.
- `futuresCount` / `optionsCount` are zero unless `status==COLLECTED`.
- The compiler cannot enforce which fields are meaningful in which state.
- Adding a new status forces auditing every `switch` by hand.

### Solution

Convert each result type into a **sealed interface with variant records**. Delete `CollectionStatus`.

```java
// eagleeye-collector/.../service/CollectionResult.java
public sealed interface CollectionResult {
    LocalDate date();

    record Collected(LocalDate date, int futuresCount, int optionsCount) implements CollectionResult {}
    record NoData   (LocalDate date)                                     implements CollectionResult {}
    record Error    (LocalDate date, String message)                     implements CollectionResult {
        public Error { Objects.requireNonNull(message); }
    }
}
```

```java
// eagleeye-collector/.../service/DateCollectionResult.java
public sealed interface DateCollectionResult {
    LocalDate tradeDate();

    record Collected(LocalDate tradeDate) implements DateCollectionResult {}
    record NoData   (LocalDate tradeDate) implements DateCollectionResult {}
    record Error    (LocalDate tradeDate, String message) implements DateCollectionResult {
        public Error { Objects.requireNonNull(message); }
    }
}
```

```java
// eagleeye-collector/.../service/MarketIndexCollectionResult.java
public sealed interface MarketIndexCollectionResult {
    YearMonth yearMonth();

    record Collected(YearMonth yearMonth, int barsCount) implements MarketIndexCollectionResult {}
    record NoData   (YearMonth yearMonth)                implements MarketIndexCollectionResult {}
    record Error    (YearMonth yearMonth, String message) implements MarketIndexCollectionResult {
        public Error { Objects.requireNonNull(message); }
    }
}
```

### Call-site changes

**Factories.** `CollectionResult.collected(d, f, o)` → `new CollectionResult.Collected(d, f, o)` (and likewise for `NoData` / `Error`). Same shape for the other two hierarchies.

Touched services:
- `CollectionService.collectAll(LocalDate)` — three `return` statements
- `InstitutionalFlowService` — three `return` statements
- `MarginTransactionService` — three `return` statements
- `MarketIndexService` — three `return` statements
- `FuturesAhService` — three `return` statements

**Exhaustive switch expressions.** Replace status-enum switches with type-pattern switches:

```java
// BackfillRunner.run() — was: switch (result.status()) { case COLLECTED -> ...; case NO_DATA -> ...; case ERROR -> ...; }
switch (result) {
    case CollectionResult.Collected c -> printRow(c.date(), "OK",
            c.futuresCount() + " rows", c.optionsCount() + " rows");
    case CollectionResult.NoData n    -> printRow(n.date(), "HOLIDAY", "-", "-");
    case CollectionResult.Error e     -> printRow(e.date(), "ERROR", e.message(), "");
}
```

Touched switch sites:
- `BackfillRunner.run()` — `result.status()` switch
- `InstitutionalFlowCommands.formatResult()`
- `MarginTransactionCommands.formatResult()`
- `MarketIndexCommands.formatResult()`
- `FuturesAhCommands.collectAh()` — inline switch on `result.status()`

**Aggregation in `BackfillRunner.printSummary`.** The flat record allowed:
```java
long totalFut = results.stream().mapToLong(CollectionResult::futuresCount).sum();
```
With sealed types, `futuresCount` only exists on `Collected`. It becomes:
```java
long collected = results.stream().filter(CollectionResult.Collected.class::isInstance).count();
long noData    = results.stream().filter(CollectionResult.NoData.class::isInstance).count();
long errors    = results.stream().filter(CollectionResult.Error.class::isInstance).count();
long totalFut  = results.stream()
        .mapToLong(r -> r instanceof CollectionResult.Collected c ? c.futuresCount() : 0L)
        .sum();
long totalOpt  = results.stream()
        .mapToLong(r -> r instanceof CollectionResult.Collected c ? c.optionsCount() : 0L)
        .sum();
```

This is slightly more verbose at the aggregation site, but the rest of the codebase gains compile-time exhaustiveness.

### Helpers deleted

- `CollectionStatus` enum — deleted entirely.
- `CollectionResult.isTradeDay()` — replaced by `instanceof CollectionResult.Collected`.
- `MarketIndexCollectionResult.isTradeMonth()` — replaced by `instanceof MarketIndexCollectionResult.Collected`.

### Why this is the right trade

- **Compiler-enforced exhaustiveness.** Adding a 4th variant breaks all switches at compile time. No silent fall-throughs.
- **No more nullable fields.** `Error.message` exists only on `Error`; `Collected.futuresCount` only on `Collected`.
- **Destructuring patterns** become available at call sites later (`case Collected(var d, var f, var o) -> ...`).
- **Cost is contained.** ~7 files change, all behavior-preserving, all mechanical.

---

## Change 2 — `Optional.map().orElse()` in shell `list` commands

### Problem

Four shell `list` commands use early-return null-style handling on `Optional`:

```java
// MarginTransactionCommands:37-40 (and 3 sibling files)
Optional<MarginTransaction> tx = repository.findByTradeDate(d);
if (tx.isEmpty()) return "No data for " + d;
return formatter.formatMarginTransaction(List.of(tx.get()));
```

### Solution

Use the value-returning Optional idiom (`.map().orElse()` — note: not `ifPresentOrElse`, which is void-returning):

```java
return repository.findByTradeDate(d)
    .map(t -> formatter.formatMarginTransaction(List.of(t)))
    .orElse("No data for " + d);
```

### Touched files

- `InstitutionalFlowCommands.java:37-40` (`list` method)
- `MarketIndexCommands.java:37-41` (`list` method)
- `MarginTransactionCommands.java:37-40` (`list` method)

(`FuturesAhCommands` uses a different shape; no change needed there.)

---

## Change 3 — `Math.clamp` in `DashboardService`

### Problem

```java
// DashboardService:74
int start = Math.max(0, allTradingDates.size() - days);
```

`Math.max(0, x - y)` works but obscures the intent. The intent is "clamp to a valid sublist start index".

### Solution

```java
int start = Math.clamp(allTradingDates.size() - days, 0, allTradingDates.size());
```

Same behavior (`x - y` cannot exceed `x` when `days >= 0`, so the upper bound never binds), but the range is explicit.

---

## Change 4 — `String.formatted()` in `TableFormatter`

### Problem

```java
// TableFormatter:239
sb.append(String.format(fmt, truncate(cell, widths[i])));
```

### Solution

```java
sb.append(fmt.formatted(truncate(cell, widths[i])));
```

Idiomatic post-Java-15. The `System.out.printf(...)` calls in `BackfillRunner` stay — converting them to `System.out.print("...".formatted(...))` is *worse*, not better.

---

## Testing Impact

- **Existing tests stay green by design.** No behavior change.
- **Test construction calls** change from `CollectionResult.collected(d, 1, 2)` → `new CollectionResult.Collected(d, 1, 2)`. Mechanical find-and-replace.
- **New compile-time guarantee**: tests pattern-matching `*.Error` no longer need null checks on `message` — the compact constructor enforces non-null at construction.
- **No new tests required** for this refactor. It's a type-shape change, not new behavior. Existing coverage in `CollectionServiceTest` + shell command tests + `BackfillRunnerTest` (if present) is sufficient.
- **Verification command**: `mvn clean test` after each step.

## Execution Order (for the implementation plan)

Each step is independently committable.

1. Sealed `CollectionResult` + delete `CollectionStatus` → update `CollectionService` + `BackfillRunner` switches and aggregation → tests green.
2. Sealed `DateCollectionResult` → update `InstitutionalFlowService`, `MarginTransactionService`, `FuturesAhService` + their shell commands → tests green.
3. Sealed `MarketIndexCollectionResult` → update `MarketIndexService` + `MarketIndexCommands` → tests green.
4. `Optional.map().orElse()` in the three shell `list` commands → tests green.
5. `Math.clamp` in `DashboardService` + `String.formatted` in `TableFormatter` → tests green.

## Files Touched (summary)

**Created/rewritten (3):**
- `eagleeye-collector/src/main/java/com/eagleeye/collector/service/CollectionResult.java`
- `eagleeye-collector/src/main/java/com/eagleeye/collector/service/DateCollectionResult.java`
- `eagleeye-collector/src/main/java/com/eagleeye/collector/service/MarketIndexCollectionResult.java`

**Deleted (1):**
- `eagleeye-collector/src/main/java/com/eagleeye/collector/service/CollectionStatus.java`

**Edited (~11):**
- `eagleeye-collector/.../service/CollectionService.java`
- `eagleeye-collector/.../service/InstitutionalFlowService.java`
- `eagleeye-collector/.../service/MarginTransactionService.java`
- `eagleeye-collector/.../service/MarketIndexService.java`
- `eagleeye-collector/.../service/FuturesAhService.java`
- `eagleeye-collector/.../runner/BackfillRunner.java`
- `eagleeye-shell/.../commands/InstitutionalFlowCommands.java`
- `eagleeye-shell/.../commands/MarginTransactionCommands.java`
- `eagleeye-shell/.../commands/MarketIndexCommands.java`
- `eagleeye-shell/.../commands/FuturesAhCommands.java`
- `eagleeye-shell/.../formatter/TableFormatter.java`
- `eagleeye-web/.../DashboardService.java`

Test files that construct result objects will need the same factory→constructor rename. Tests stay otherwise untouched.
