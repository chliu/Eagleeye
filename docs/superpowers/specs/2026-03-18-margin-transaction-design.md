# Margin Transaction (融資融券) Data Collection Design

**Date:** 2026-03-18

## Goal

Collect daily Taiwan market-wide margin transaction summary (融資融券餘額) from TWSE and expose it through the same collect/backfill/list/show shell interface used by MarketIndex, Futures, and Options.

---

## API

**Endpoint:** `GET https://www.twse.com.tw/rwd/en/marginTrading/MI_MARGN`

**Parameters:**
| Parameter    | Value             | Notes                        |
|--------------|-------------------|------------------------------|
| `date`       | `YYYYMMDD`        | Single trading date          |
| `selectType` | `MS`              | Market Summary (market-wide) |
| `response`   | `json`            |                              |

**Response structure:** `stat: "OK"`, `data` array with 2 rows:
- Row 0 — Margin Purchase (融資)
- Row 1 — Short Sale (融券)

Each row has columns: `[Item, Buy/Cover, Sell/Short, Redemption, Prev Balance, Balance]`

**Availability:** Per-day (not per-month). Data published by TWSE ~14:30–15:00 Taipei time after 13:30 market close.

---

## Data Layer (`eagleeye-domain`)

### Entity: `MarginDailyBar`

Table: `margin_daily_bar`, unique constraint on `trade_date`.

| Field                  | Type      | API source (row, col)              |
|------------------------|-----------|------------------------------------|
| `tradeDate`            | LocalDate | date param                         |
| `marginPurchase`       | Long      | row[0], col[1] — 融資買進          |
| `marginSale`           | Long      | row[0], col[2] — 融資賣出          |
| `marginCashRedemption` | Long      | row[0], col[3] — 融資現償          |
| `marginPrevBalance`    | Long      | row[0], col[4] — 前日融資餘額      |
| `marginBalance`        | Long      | row[0], col[5] — 融資餘額          |
| `shortCovering`        | Long      | row[1], col[1] — 融券回補          |
| `shortSale`            | Long      | row[1], col[2] — 融券賣出          |
| `shortStockRedemption` | Long      | row[1], col[3] — 融券現償          |
| `shortPrevBalance`     | Long      | row[1], col[4] — 前日融券餘額      |
| `shortBalance`         | Long      | row[1], col[5] — 融券餘額          |

All values are plain trading units (lots/張). No fixed-point encoding needed.

### Repository: `MarginDailyBarRepository`

```java
Optional<MarginDailyBar> findByTradeDate(LocalDate tradeDate);
List<MarginDailyBar> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
```

---

## Collector Layer (`eagleeye-collector`)

### `TwseClient` — new method

```java
public String fetchMarginJson(LocalDate date) {
    // GET /rwd/en/marginTrading/MI_MARGN?date=YYYYMMDD&selectType=MS&response=json
}
```

Date formatted as `yyyyMMdd`.

### `TwseParser` — new method

```java
public MarginDailyBar parseMargin(String json, LocalDate date)
```

- Validates `stat == "OK"`, returns `null` on failure
- Reads `data[0]` (Margin row) cols 1–5 → `marginPurchase`, `marginSale`, `marginCashRedemption`, `marginPrevBalance`, `marginBalance`
- Reads `data[1]` (Short row) cols 1–5 → `shortCovering`, `shortSale`, `shortStockRedemption`, `shortPrevBalance`, `shortBalance`
- Strips commas, parses as Long

### `MarginCollectionResult` — new record

```java
record MarginCollectionResult(LocalDate tradeDate, Status status, String errorMessage) {
    enum Status { COLLECTED, NO_DATA, ERROR }
    static MarginCollectionResult collected(LocalDate date) { ... }
    static MarginCollectionResult noData(LocalDate date) { ... }
    static MarginCollectionResult error(LocalDate date, String msg) { ... }
}
```

Note: uses `LocalDate` (not `YearMonth`) since this API is per-day.

### `MarginTransactionService`

```java
@Transactional
public MarginCollectionResult collectDate(LocalDate date) {
    String json = twseClient.fetchMarginJson(date);
    MarginDailyBar bar = twseParser.parseMargin(json, date);
    if (bar == null) return MarginCollectionResult.noData(date);
    repository.save(bar);  // upsert via unique constraint
    return MarginCollectionResult.collected(date);
}
```

### `MarginTransactionScheduler`

```java
@Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Taipei")
public void collectMargin() {
    LocalDate today = LocalDate.now(TAIPEI);
    marginTransactionService.collectDate(today);
}
```

Fires at 15:30 Taipei time — 2h after TWSE close, data reliably published by then.

### `CombinedBackfillRunner` — update

Add `marginTransactionService.collectDate(day)` per weekday alongside TAIFEX:

```java
// per weekday in range:
collectionService.collectAll(day);          // TAIFEX futures + options (existing)
marginTransactionService.collectDate(day);  // Margin transaction (new)
Thread.sleep(requestDelayMs);
```

---

## Shell Layer (`eagleeye-shell`)

### `MarginTransactionCommands`

| Command | Options | Description |
|---------|---------|-------------|
| `margin list` | `--date` (default: today) | Single-date lookup |
| `margin show` | `--from`, `--to` (default: last 30 days) | Range view |
| `margin collect` | `--date` (default: today) | Trigger collection |
| `margin backfill` | `--from`, `--to` (default: 12 months ago → today) | Bulk collect with 500ms throttle |

Constructor injection: `(MarginTransactionService, MarginDailyBarRepository, TableFormatter)`.

### `TableFormatter.formatMarginTransaction(List<MarginDailyBar>)`

9-column table, all numeric columns right-aligned:

```
┌────────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
│ Date       │ M-Buy    │ M-Sell   │ M-Redeem │ M-Bal    │ S-Cover  │ S-Sell   │ S-Redeem │ S-Bal    │
```

Column widths: Date=10, all numeric=10. Values comma-formatted (no fixed-point conversion needed).

---

## Testing

Each new component gets its own test class following existing patterns:

| Test class | What it covers |
|-----------|----------------|
| `TwseParserTest` (extend) | `parseMargin` — valid JSON, `stat != OK`, null handling |
| `MarginTransactionServiceTest` | `collectDate` — collected, no data, error paths |
| `MarginTransactionCommandsTest` | All 4 shell commands, date defaults, output content |
| `MarginTransactionFormatterTest` | Table output — headers, null values, multiple rows |

---

## File Summary

**New files:**
- `eagleeye-domain/.../entity/MarginDailyBar.java`
- `eagleeye-domain/.../repository/MarginDailyBarRepository.java`
- `eagleeye-collector/.../service/MarginCollectionResult.java`
- `eagleeye-collector/.../service/MarginTransactionService.java`
- `eagleeye-collector/.../scheduler/MarginTransactionScheduler.java`
- `eagleeye-shell/.../commands/MarginTransactionCommands.java`
- (tests for each of the above)

**Modified files:**
- `eagleeye-collector/.../twse/TwseClient.java` — add `fetchMarginJson(LocalDate)`
- `eagleeye-collector/.../twse/TwseParser.java` — add `parseMargin(String, LocalDate)`
- `eagleeye-collector/.../runner/CombinedBackfillRunner.java` — add margin per weekday
- `eagleeye-shell/.../formatter/TableFormatter.java` — add `formatMarginTransaction`
