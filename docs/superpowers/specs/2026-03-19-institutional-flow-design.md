# Institutional Flow (法人買賣超) Collection Design

> **Status:** Approved
> **Date:** 2026-03-19
> **Feature:** Daily trading value of Foreign & Other Investors (TWSE BFI82U)

---

## Overview

Collect daily **trading value (NTD)** by institutional investor type from the TWSE BFI82U endpoint. Store totals for three investor types (Foreign Investors, Investment Trust, Dealers) with buy/sell/net values — 9 fields total per trading date. Integrated into the existing combined daily collection schedule at 15:30 Taiwan time.

**Source:** `GET https://www.twse.com.tw/rwd/en/fund/BFI82U?type=day&dayDate=YYYYMMDD&response=json`

---

## 1. Domain Layer (`eagleeye-domain`)

### Entity: `InstitutionalFlow`

| Column | Type | Description |
|--------|------|-------------|
| `id` | Long (PK, auto) | Surrogate key |
| `trade_date` | LocalDate | Trading date (unique) |
| `foreign_buy` | Long | Foreign Investors buy value (NTD) |
| `foreign_sell` | Long | Foreign Investors sell value (NTD) |
| `foreign_net` | Long | Foreign Investors net value (NTD) |
| `investment_trust_buy` | Long | Investment Trust buy value (NTD) |
| `investment_trust_sell` | Long | Investment Trust sell value (NTD) |
| `investment_trust_net` | Long | Investment Trust net value (NTD) |
| `dealer_buy` | Long | Dealer buy value (NTD) |
| `dealer_sell` | Long | Dealer sell value (NTD) |
| `dealer_net` | Long | Dealer net value (NTD) |

- Table name: `institutional_flow`
- Unique constraint: `uq_institutional_flow_trade_date` on `(trade_date)`
- Values stored as raw NTD integers (no fixed-point encoding)
- Protected no-arg constructor + public `(LocalDate tradeDate)` constructor
- All fields nullable (allow partial data)

### Repository: `InstitutionalFlowRepository`

```java
public interface InstitutionalFlowRepository extends JpaRepository<InstitutionalFlow, Long> {
    Optional<InstitutionalFlow> findByTradeDate(LocalDate tradeDate);
    List<InstitutionalFlow> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
}
```

---

## 2. Collector Layer (`eagleeye-collector`)

### API Client: `TwseClient`

Add method:
```java
private static final String INSTITUTIONAL_FLOW_PATH = "/rwd/en/fund/BFI82U";

public String fetchInstitutionalFlowJson(LocalDate date) {
    // GET INSTITUTIONAL_FLOW_PATH?type=day&dayDate=YYYYMMDD&response=json
}
```

### Parser: `TwseParser`

Add method `parseInstitutionalFlow(String json, LocalDate date)`:
- Validates `stat == "OK"`
- Navigates `tables[0].data` (confirmed TWSE response structure from BFI82U)
- Identifies investor rows by label match: "Foreign Investors", "Investment Trust", "Dealers"
- Maps columns: `[1]` = buy, `[2]` = sell, `[3]` = net per row
- Uses existing `toLong()` helper for comma-formatted values
- Returns `null` on any failure (invalid JSON, missing data, parse error)
- Debug-logs raw rows when parsing fails

> **Note:** Exact row ordering confirmed at implementation time via debug logging against the live API, following the pattern established during margin transaction development.

### Result Record: `InstitutionalFlowResult`

```java
public record InstitutionalFlowResult(LocalDate tradeDate, Status status, String errorMessage) {
    enum Status { COLLECTED, NO_DATA, ERROR }
    static InstitutionalFlowResult collected(LocalDate date) { ... }
    static InstitutionalFlowResult noData(LocalDate date) { ... }
    static InstitutionalFlowResult error(LocalDate date, String msg) { ... }
}
```

### Service: `InstitutionalFlowService`

```java
@Service
public class InstitutionalFlowService {
    // Constructor injection: TwseClient, TwseParser, InstitutionalFlowRepository

    @Transactional
    public InstitutionalFlowResult collectDate(LocalDate date) {
        // fetch → parse → upsert → return result
        // null parse result → NO_DATA
        // exception → ERROR
    }

    private void upsert(InstitutionalFlow parsed) {
        // findByTradeDate().orElseGet() → set all 9 fields → save
    }
}
```

### Scheduler: `InstitutionalFlowScheduler`

```java
@Component
public class InstitutionalFlowScheduler {
    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Taipei")
    public void collect() {
        service.collectDate(LocalDate.now());
    }
}
```

### Combined Backfill Runner: `CombinedBackfillRunner`

- Add `InstitutionalFlowService` as 5th dependency (after `MarginTransactionService`)
- `@Autowired` constructor: 5 args; package-private test constructor: 6 args (+ `requestDelayMs`)
- In weekday loop: call `institutionalFlowService.collectDate(day)` after margin, with `Thread.sleep(requestDelayMs)`
- Add `printInstitutionalFlow(LocalDate, InstitutionalFlowResult)` helper

---

## 3. Shell Layer (`eagleeye-shell`)

### Formatter: `TableFormatter`

Add `formatInstitutionalFlow(List<InstitutionalFlow> flows)`:
- Columns: `Date | F-Buy | F-Sell | F-Net | IT-Buy | IT-Sell | IT-Net | D-Buy | D-Sell | D-Net`
- New width constant: `W_FLOW = 14` (accommodates large NTD values, e.g. `"123,456,789,012"`)
- Numbers right-aligned, comma-formatted via existing `fmtVol()`
- Returns `"No data found."` for empty list

### Commands: `InstitutionalFlowCommands`

```java
@Component
public class InstitutionalFlowCommands {
    // Constructor injection: InstitutionalFlowService, InstitutionalFlowRepository, TableFormatter

    @Command(name = "institutional-flow list")   // --date (default: today)
    @Command(name = "institutional-flow show")   // --from (default: 30 days ago), --to (default: today)
    @Command(name = "institutional-flow collect") // --date (default: today)
    @Command(name = "institutional-flow backfill") // --from (default: 12 months ago), --to (default: today)
}
```

Backfill throttles at 500ms between requests (same as `MarginTransactionCommands`).

---

## 4. Testing

All layers follow TDD (Red-Green-Refactor):

| Test | Type | Location |
|------|------|----------|
| `InstitutionalFlowTest` | Unit | `eagleeye-domain` |
| `InstitutionalFlowRepositoryIT` | Integration (@SpringBootTest + H2) | `eagleeye-collector` |
| `TwseParserTest` (extended) | Unit | `eagleeye-collector` |
| `InstitutionalFlowServiceTest` | Unit (Mockito) | `eagleeye-collector` |
| `InstitutionalFlowServiceIT` | Integration (@SpringBootTest + H2) | `eagleeye-collector` |
| `CombinedBackfillRunnerTest` (extended) | Unit (Mockito) | `eagleeye-collector` |
| `InstitutionalFlowFormatterTest` | Unit | `eagleeye-shell` |
| `InstitutionalFlowCommandsTest` | Unit (Mockito) | `eagleeye-shell` |

Parser fixture JSON uses real BFI82U response structure (`tables[0].data`), confirmed against live API during development.

---

## 5. File Map

**New files:**
- `eagleeye-domain/src/.../entity/InstitutionalFlow.java`
- `eagleeye-domain/src/.../repository/InstitutionalFlowRepository.java`
- `eagleeye-collector/src/.../service/InstitutionalFlowResult.java`
- `eagleeye-collector/src/.../service/InstitutionalFlowService.java`
- `eagleeye-collector/src/.../scheduler/InstitutionalFlowScheduler.java`
- `eagleeye-shell/src/.../commands/InstitutionalFlowCommands.java`
- Test counterparts for each of the above
- `eagleeye-collector/src/test/.../collector/InstitutionalFlowRepositoryIT.java`
- `eagleeye-collector/src/test/.../service/InstitutionalFlowServiceIT.java`

**Modified files:**
- `TwseClient.java` — add `fetchInstitutionalFlowJson(LocalDate)`
- `TwseParser.java` — add `parseInstitutionalFlow(String, LocalDate)`
- `CombinedBackfillRunner.java` — add `InstitutionalFlowService`, weekday call, print helper
- `CombinedBackfillRunnerTest.java` — add mock + stubs + verify
- `TableFormatter.java` — add `W_FLOW`, `formatInstitutionalFlow()`

---

## 6. Data Notes

- Values are raw NTD integers (e.g. `123456789012`) — no fixed-point encoding needed
- "Net" = Buy − Sell; can be negative (foreign selling pressure)
- Holiday dates return `stat != "OK"` → `NO_DATA` result, nothing persisted
- Upsert: second collection for same date updates all 9 fields (same as margin pattern)
