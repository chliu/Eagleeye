# 小台指/微台指 散戶多空比 Dashboard Panels — Design Spec

**Date:** 2026-07-17
**Status:** Approved

---

## 1. Goal

Add two new dashboard panels showing 散戶(retail investor) long-short ratio vs. 加權指數 for MTX (小型臺指期貨) and TMF (微型臺指期貨), matching the reference layout (bar chart of the ratio, colored by sign, with TAIEX index as a secondary-axis line).

Formula (as given):
```
多單 (retail long)  = 契約全合約OI − 三大法人多單
空單 (retail short) = 契約全合約OI − 三大法人空單
多空比 = (多單 − 空單) / 契約全合約OI × 100%
```
where 三大法人 = DEALER + INVESTMENT_TRUST + FINI combined (not FINI alone), and 全合約OI = total open interest across all trader types and all contract months for that commodity, on that date.

## 2. Scope

This requires a **new data source**: total market open interest per contract. The existing `futures_position` table only stores OI broken out by the three institutional trader types (from TAIFEX's 三大法人-區分各期貨契約 report) — there is no market-wide total. A new collector is added to fetch this from TAIFEX's daily market report.

In scope:
- New TAIFEX collector for total OI (MTX, TMF only — not TX, not needed for this feature).
- `DashboardService` calculation of the ratio for MTX/TMF.
- Two new `dashboard.html` panels.

Out of scope:
- Header stat boxes (散戶多單/空單 badges, big % number) and footnote text shown in the reference image — panels use the existing chart-title + canvas pattern only, consistent with every other panel in `dashboard.html`.
- Using MTX/TMF's own settlement price as the line — panels reuse 加權指數 (`taiexDs()`), consistent with every other panel.
- TX total OI / ratio (not requested).

## 3. New Data Source: Total Market OI

### 3.1 TAIFEX endpoint

`POST https://www.taifex.com.tw/enl/eng3/futDailyMarketReport`

Form body: `queryType=2&marketCode=0&dateaddcnt=&commodity_id=<MTX|TMF>&commodity_id2=&queryDate=<yyyy/MM/dd>`

Verified live against production TAIFEX data (2026/07/16):
- `commodity_id=MTX` → 8 contract-month rows + a `Subtotal:` row with Open Interest = **35655**, exactly matching the sum of the 8 individual rows' OI values (44+2+30580+3538+43+916+423+109).
- `commodity_id=TMF` → 6 contract-month rows + `Subtotal:` row with OI = **74882**, exactly matching the sum (59759+9284+103+2998+1917+821).
- An empty `commodity_id` returns "No Data" — the report **must** be queried per-contract (matches the existing per-contract fetch pattern already used elsewhere in `DashboardService`).
- `marketCode=0` = Regular Trading Session (the value needed here; `1` = After Hours, not used).

### 3.2 Response structure

The results page contains two `table.table_f` elements: the main data table (first in document order) and a "Spread Trading" table (second, irrelevant here). Row layout of the main table — 17 `<td>`/`<TD>` cells per row (case varies in the wild — Jsoup's `select("td")` handles both uniformly):

```
[0]  Contract          [1]  Contract Month     [2]  Open           [3]  High
[4]  Low                [5]  Last               [6]  Change         [7]  %
[8]  *Volume-AfterHours [9]  *Volume-Regular    [10] *Volume-Total  [11] Settlement Price
[12] *Open Interest     [13] Best Bid           [14] Best Ask       [15] Historical High
[16] Historical Low
```

The final row of the table is a `Subtotal:` row (label appears in cell index 7, replacing the Change/% columns), with the same 17-cell shape; cell index 12 holds the OI subtotal across all contract months. This is the value the formula's "全合約OI" refers to.

### 3.3 Parser

New class `TaifexMarketReportParser` (separate from `TaifexParser`, which is documented specifically for the 3-trader-type institutional report and has a different row shape):

```java
public Long parseTotalOi(String html, LocalDate tradeDate, String contract)
```

- `isNoDataPage()` check reused/duplicated from `TaifexParser`'s logic (checks for "No Data"/"查無資料").
- Select first `table.table_f`.
- Iterate `tr`; for the row whose `td` list contains a cell with trimmed text `"Subtotal:"`, take `td.get(12)`, strip commas, parse as `Long`.
- Return `null` (with a warning log) if no Subtotal row is found — mirrors existing parser's "could not find data table" warning pattern.

### 3.4 Entity / Repository

New `FuturesMarketOi` entity, table `futures_market_oi`:
```java
@Entity
@Table(name = "futures_market_oi",
       uniqueConstraints = @UniqueConstraint(columnNames = {"trade_date", "contract"}))
public class FuturesMarketOi {
    Long id;
    LocalDate tradeDate;   // LocalDateToIsoStringConverter, same as other entities
    String contract;
    Long totalOi;
}
```

`FuturesMarketOiRepository`:
```java
Optional<FuturesMarketOi> findByTradeDateAndContract(LocalDate, String);
List<FuturesMarketOi> findByContractAndTradeDateBetweenOrderByTradeDateAsc(String, LocalDate, LocalDate);
```

### 3.5 Client

New method on `TaifexClient`:
```java
public String fetchDailyMarketReportHtml(LocalDate date, String contract)
```
Requires a new private POST-based fetch helper (the existing `fetch()` private method is GET-only — used for the currently-collected reports, which accept GET even though sourced from a form-actioned page).

### 3.6 Service

New `FuturesMarketOiService`, following the `MarginTransactionService.collectDate` shape:
```java
@Transactional
public DateCollectionResult collectDate(LocalDate date) {
    // for each of "MTX", "TMF": fetch, parse, upsert (find-or-create by tradeDate+contract)
    // NoData if both contracts return no data; Error on any exception; Collected otherwise
}
```
Reuses the existing `DateCollectionResult` sealed interface (`Collected`/`NoData`/`Error`) — no new result type needed.

### 3.7 Collector + Deployment

```java
@Component
public class TaifexMarketOiCollector implements ScheduledCollector {
    @Override public String name() { return "MKTOI"; }
    @Override public CollectorOutcome collect(LocalDate date) {
        var result = service.collectDate(date);
        return switch (result) { ... };  // same 3-case mapping as MarginCollector
    }
}
```

- New `deploy/com.eagleeye.collector.mktoi.plist`: Mon–Fri, `StartCalendarInterval` 15:35 Asia/Taipei (staggered 5 minutes after the existing 15:30 TAIFEX job to avoid any resource contention; TAIFEX's daily market report is published on the same ~15:00 schedule as the institutional report).
- Add `mktoi` to the `COLLECTORS` array in `deploy/install.sh` and its printed schedule summary.
- Add `FuturesMarketOiService` to `CombinedBackfillRunner`'s sequence so historical data backfills alongside the other sources (without this, the two new panels would start empty and grow one day at a time).

## 4. Dashboard Calculation (`DashboardService`)

New dependency: `FuturesMarketOiRepository`.

For each of `"MTX"`, `"TMF"`, per trading date in the existing date range:
1. `totalOi` = `FuturesMarketOi.totalOi` for that contract+date, or `null` if missing (row becomes null → chart gap, same convention as other series).
2. `institutionalLong`/`institutionalShort` = sum of `oiLongVolume`/`oiShortVolume` across **all** rows returned by the existing trader-type-agnostic repository method `FuturesPositionRepository.findByContractAndTradeDateBetweenOrderByTradeDateAsc(contract, from, to)` for that date (up to 3 rows: DEALER, INVESTMENT_TRUST, FINI). A missing trader type for a date contributes 0 (same null-safe pattern as the existing TX-equivalent code).
3. If `totalOi == null`, the date's ratio is `null`. Otherwise:
   ```
   retailLong  = totalOi - institutionalLong
   retailShort = totalOi - institutionalShort
   ratio       = (retailLong - retailShort) / (double) totalOi * 100.0
   ```

New `DashboardViewModel` fields (2 total): `List<Double> mtxRatio`, `List<Double> tmfRatio`. Retail long/short are computed but not exposed to the view (no header stat boxes per §2).

## 5. UI (`dashboard.html`)

Two new `.chart-card` blocks (canvas ids `mtxRatioChart`, `tmfRatioChart`), added as a new `chart-grid` row. Each chart:
- Single `bar` dataset = the ratio series, `backgroundColor` red/green by sign (matches the 融資變化 panel exactly).
- `taiexDs()` line on `y2` (same helper already used by every other chart).
- Symmetric y-axis via the existing `absMax` helper (`min = -absMax(ratio), max = absMax(ratio)`), matching `marginChart`'s pattern.
- Tooltip formatted as a percentage (reuse pattern from `fmtYiVal`-style formatter, adapted to `%`).

No new CSS — reuses `.chart-card`/`.chart-title` exactly as-is.

## 6. Testing

- `TaifexMarketReportParserTest`: synthetic HTML with a `Subtotal:` row → correct `totalOi` extraction; No-Data page → `null`; missing Subtotal row → `null` with warning (no exception).
- `FuturesMarketOiServiceTest`: Mockito on client/parser/repo — insert vs. update upsert per contract, both-contracts-no-data → `NoData`, exception → `Error`.
- `ScheduledCollectorTest`: add `TaifexMarketOiCollector` section (3-case outcome mapping), matching existing collectors' test shape.
- `DashboardServiceTest`: ratio calculation with all 3 institutional trader types present; missing trader type contributes 0; missing `totalOi` → null ratio for that date; multi-date null-safe gaps.
- Manual: run the app, hit `/dashboard`, confirm both panels render bars (colored by sign) + TAIEX line, and gaps appear correctly for dates without OI data.

## 7. Edge Cases

- **Row visibility:** MTX/TMF ratio rows are independently null-gated by their own `totalOi` availability — not tied to TX's presence (unlike the existing TX-equivalent 外資期貨部位 panel, which anchors on TX).
- **Partial institutional data:** if only 1 or 2 of the 3 trader types have a row for a date, the other(s) contribute 0 to the institutional sum (not null-the-whole-row) — same as existing `txEquivalent` behavior.
- **Subtotal row absent:** if TAIFEX ever omits the Subtotal row (unexpected), parser returns `null` for that date/contract rather than throwing.
