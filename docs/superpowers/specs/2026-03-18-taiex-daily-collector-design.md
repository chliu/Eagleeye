# TAIEX Daily Bar Collector — Design Spec

**Date:** 2026-03-18
**Status:** Approved
**Scope:** Collect TAIEX index daily OHLC + volume data from TWSE and store it in the existing eagleeye database.

---

## Goal

Add daily TAIEX OHLC/volume collection to the eagleeye platform, following the same architecture as the existing TAIFEX futures/options collector. Data is collected automatically each trading day and is also triggerable manually via the Shell CLI.

---

## Data Source

**Provider:** Taiwan Stock Exchange (TWSE) official API
**Endpoint:**
```
GET https://www.twse.com.tw/rwd/en/index/TAIEX?date=YYYYMMDD&response=json
```
- Returns all trading days in the month containing `date`
- Response format: JSON with a `data` array; each row contains: date, open, high, low, close, volume (shares), turnover (NTD)
- Empty `data` array (or status field indicating no data) indicates a non-trading month
- No authentication required; consistent with existing TAIFEX scraping approach

**Historical backfill:** The initial backfill covers the last 12 months as documentation guidance for the one-time setup. This is not a coded constraint. Two distinct mechanisms exist for triggering backfill — see the Data Flow section.

---

## Domain Model

### New entity: `TaiexDailyBar` (`eagleeye-domain`)

| Column | Java Type | DB Type | Notes |
|---|---|---|---|
| `id` | `Long` | `BIGINT` PK | Auto-generated |
| `trade_date` | `LocalDate` | `DATE` | Unique constraint |
| `open` | `Long` | `BIGINT` | See fixed-point note below |
| `high` | `Long` | `BIGINT` | |
| `low` | `Long` | `BIGINT` | |
| `close` | `Long` | `BIGINT` | |
| `volume` | `Long` | `BIGINT` | Shares traded |
| `turnover` | `Long` | `BIGINT` | NTD value traded |

Unique constraint on `trade_date`. Upsert pattern (find-or-create, then save) — identical to `FuturesPosition`.

**Fixed-point encoding for OHLC:** TAIEX index values are fractional (e.g., `"20,234.56"`). The `TwseParser` is responsible for converting each raw API string to a `Long` by stripping commas, parsing as `BigDecimal`, multiplying by 100, and truncating (`BigDecimal.multiply(new BigDecimal("100")).longValue()`). The stored value `2023456` represents `20,234.56`. Consumers must divide by 100 to recover the display value.

### New repository: `TaiexDailyBarRepository` (`eagleeye-domain`)
- Extends `JpaRepository<TaiexDailyBar, Long>`
- Method: `Optional<TaiexDailyBar> findByTradeDate(LocalDate tradeDate)`

---

## New Classes

### `eagleeye-domain`
| Class | Location | Purpose |
|---|---|---|
| `TaiexDailyBar` | `entity/` | JPA entity for daily TAIEX bar |
| `TaiexDailyBarRepository` | `repository/` | Spring Data repository |

### `eagleeye-collector`
| Class | Location | Purpose |
|---|---|---|
| `TwseClient` | `twse/` | HTTP client for TWSE JSON API (mirrors `TaifexClient`) |
| `TwseParser` | `twse/` | Parses TWSE JSON → `List<TaiexDailyBar>`; applies fixed-point encoding |
| `MarketIndexService` | `service/` | Orchestrates fetch → parse → upsert; returns `MarketIndexCollectionResult` |
| `MarketIndexCollectionResult` | `service/` | Java `record` with fields `YearMonth yearMonth`, `int barsCount`, `Status status`, `String errorMessage`; statuses: `COLLECTED`, `NO_DATA`, `ERROR`; factory methods: `collected(YearMonth, int)`, `noData(YearMonth)`, `error(YearMonth, String)`; does NOT reuse `CollectionResult` which is TAIFEX-specific |
| `MarketIndexScheduler` | `scheduler/` | New standalone scheduler bean for TAIEX daily collection; does NOT modify existing `CollectionScheduler` |
| `MarketIndexBackfillRunner` | `runner/` | New standalone runner for TAIEX backfill; does NOT modify existing `BackfillRunner` |

### `eagleeye-shell`
| Class | Location | Purpose |
|---|---|---|
| `MarketIndexCommands` | `commands/` | Shell commands for manual collection |

---

## Data Flow

### Scheduled daily collection

`MarketIndexScheduler` is a new `@Component` with its own `@Scheduled` method — it does not extend or modify `CollectionScheduler`.

```
18:00 Asia/Taipei (MON-FRI)
  →  MarketIndexScheduler.collectTaiex()
       →  MarketIndexService.collectMonth(YearMonth.now())
            →  TwseClient.fetchMonthJson(yearMonth)    // whole month per request
            →  TwseParser.parse(json)                  // List<TaiexDailyBar> (with fixed-point)
            →  upsert each bar via TaiexDailyBarRepository
            →  return MarketIndexCollectionResult
```

**Why 18:00 (not 16:15):** TWSE publishes daily closing index data later than TAIFEX's institutional position data. 18:00 provides a safe buffer after the 13:30 market close and TWSE's post-close publication window.

TWSE returns a full month per request. Fetching the current month and upserting all rows is idempotent and safe to re-run.

### `MarketIndexService` methods

| Method | Description |
|---|---|
| `collectMonth(YearMonth)` | Fetches and upserts all bars for the given month |
| `collectDate(LocalDate)` | Calls `collectMonth` for the month of the given date; used by the shell command for single-day invocation |

### Manual shell commands

Commands follow the existing `@Option` named-option convention (not positional args), mirroring `CollectCommands` and `FuturesCommands`. Shell commands call `MarketIndexService` directly in a loop — they do **not** invoke `MarketIndexBackfillRunner` and do **not** call `System.exit()`.

```
market-index collect --date 2025-03-18
  →  MarketIndexService.collectDate(LocalDate)

market-index backfill [--from 2024-01-01] [--to 2025-03-18]
  →  MarketIndexCommands calls MarketIndexService.collectMonth() in a loop
     over each YearMonth in the range; defaults: from = today − 12 months, to = today
  →  Prints a result line per month to shell output
```

### Standalone backfill (collector jar — separate from shell)

`MarketIndexBackfillRunner` mirrors the existing `BackfillRunner`:
- Activated by `@ConditionalOnProperty(name = "market-index.backfill.from")`
- Reads `@Value("${market-index.backfill.from}")` and `@Value("${market-index.backfill.to:#{null}}")`
- Calls `MarketIndexService.collectMonth()` in a loop, prints summary, calls `System.exit(0)`
- Invoked as: `java -jar eagleeye-collector-exec.jar --market-index.backfill.from=2024-01-01`

---

## Error & No-Data Handling

| Scenario | Behaviour |
|---|---|
| TWSE returns empty `data` array | `TwseParser` returns empty list → `MarketIndexCollectionResult.noData(date)` logged at INFO |
| Network / HTTP error | Exception caught in `MarketIndexService` → `MarketIndexCollectionResult.error(date, message)` logged at ERROR |
| Duplicate date on re-run | Upsert overwrites existing row — idempotent |

---

## Scheduling

| Job | Class | Cron | Timezone |
|---|---|---|---|
| TAIEX daily collect | `MarketIndexScheduler` | `0 0 18 * * MON-FRI` | `Asia/Taipei` |

Configured via `@Scheduled(cron = "...", zone = "Asia/Taipei")` — consistent with `CollectionScheduler`.

---

## Testing

### `TwseParserTest` (unit)
- Inline JSON fixture string (analogous to inline HTML in `TaifexParserTest`) with valid monthly data → assert correct `TaiexDailyBar` field values including fixed-point encoding (e.g., `"20,234.56"` → `2023456L`)
- Empty `data` array → returns empty list
- Malformed JSON → throws `IllegalArgumentException` with a message including the offending input

### `MarketIndexServiceTest` (unit, Mockito)
- Mocked `TwseClient` + mocked `TaiexDailyBarRepository`; no Spring context loaded
- Valid JSON response → bars upserted, `MarketIndexCollectionResult` with `status=COLLECTED` returned
- No-data response (empty list from parser) → `MarketIndexCollectionResult` with `status=NO_DATA` returned
- Client throws exception → `MarketIndexCollectionResult` with `status=ERROR` returned

---

## Out of Scope

- Intraday / minute-level bars (TWSE does not provide finer than 5-min; no intraday source selected)
- Individual stock OHLC (only TAIEX index)
- REST API endpoints (Shell CLI only, consistent with rest of project)
