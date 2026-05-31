# Store complete callsAndPuts source per trader

**Date:** 2026-05-31
**Status:** Approved (design)

## Problem

The TAIFEX `callsAndPuts` report ("三大法人－區分各類選擇權買賣權契約金額")
carries 12 data columns per `(contract, right, trader)` row. The collector currently
keeps **only the last column** (`oiNetValue` = 未平倉餘額→買賣差額→契約金額) and stores
it on `OptionsPosition` as two scalar columns `oi_net_value_call` / `oi_net_value_put`.
The other 11 columns are discarded.

This caused real confusion: when verifying 5/29 figures, the only way to recover other
columns (e.g. 賣方未平倉契約金額) was to re-fetch the raw HTML from TAIFEX. The DB should
retain the **complete** source so the data is self-sufficient, with `oiNetValue`
(買賣差額契約金額) remaining the headline metric the dashboard plots.

## Goal

Persist the full 12-column callsAndPuts breakdown per `(trade_date, contract, trader, right)`,
and migrate the dashboard to read the call/put net value from the new store.

## Design

### 1. Data model — new dedicated table

New entity `OptionsCallPutPosition extends AbstractMarketPosition`, adding one
discriminator field:

```java
@Enumerated(EnumType.STRING)
@Column(name = "right_type", nullable = false, length = 4)
private RightType rightType;
```

- Table: `options_call_put_position`
- Unique constraint `uq_options_call_put_position` on
  `(trade_date, contract, trader_type, right_type)`
- Inherits the full 12 columns from `AbstractMarketPosition`
  (trading long/short/net vol+val, OI long/short/net vol+val).
  Here `oiNetValue` = 未平倉餘額→買賣差額→契約金額, the headline metric.
- Schema is created automatically by Hibernate `ddl-auto: update` — no manual SQL.

`OptionsPosition` reverts to a plain entity: drop `oiNetValueCall` / `oiNetValuePut`
and their accessors. The physical columns `oi_net_value_call` / `oi_net_value_put`
remain in the existing DB (Hibernate `update` never drops columns) but become unused
and are left in place intentionally.

### 2. DTO + parser

- `OptionsCallPutDto` becomes a wrapper that reuses the existing 12-field `PositionDto`:

  ```java
  public record OptionsCallPutDto(PositionDto position, RightType rightType) {}
  ```

- `TaifexParser.parseCallPut` keeps **all 12** parsed columns (the same extraction
  `parseColumns` already performs) instead of only the last column. It emits one
  `OptionsCallPutDto` per `(contract, right, trader)` row, wrapping a fully-populated
  `PositionDto`.

### 3. CollectionService

`upsertOptionsCallPut` finds/creates an `OptionsCallPutPosition` by
`(date, contract, trader, right)` via the new repository, applies all 12 columns
(mirrors the existing `applyDto` helper), and saves. The two-column logic on
`OptionsPosition` is removed.

### 4. Repository

New `OptionsCallPutPositionRepository extends JpaRepository<OptionsCallPutPosition, Long>`:

- `findByTradeDateAndContractAndTraderTypeAndRightType(date, contract, trader, right)`
  — used by the upsert.
- `findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
  contract, trader, right, from, to)` — used by the dashboard.

### 5. Dashboard

`DashboardService` reads the call/put series from `OptionsCallPutPositionRepository`
for `TXO / FINI / CALL` and `TXO / FINI / PUT`, mapping `getOiNetValue()` onto the
existing `optionsCallNetValue` / `optionsPutNetValue` lists. `DashboardViewModel` is
**unchanged**.

The existing `OptionsPosition` (TXO / FINI) query is retained — it still feeds
`optionsCallOI` / `optionsPutOI` from the regular options report's
`oiLongVolume` / `oiShortVolume`. Only the two net-value lines move to the new repo.

### 6. History / backfill

`ddl-auto: update` creates the empty table on first startup. The full 12-column data
is not present in the old two columns, so history is repopulated by re-running the
existing combined backfill (`combined.backfill.from=...`), which already calls
`collectionService.collectAll(day)` → `processOptionsCallPut`.

### 7. Tests

- `TaifexParserTest`: assert all 12 columns are parsed for a CALL row and a PUT row.
- `CollectionServiceTest`: assert the upsert writes a fully-populated
  `OptionsCallPutPosition` (replacing the current `getOiNetValueCall()` assertion).
- `DashboardServiceTest`: feed `OptionsCallPutPosition` entities instead of calling
  `setOiNetValueCall`.
- New `OptionsCallPutPositionRepositoryIT` covering upsert and the dashboard finder.

## Out of scope

- Dropping the now-unused `oi_net_value_call` / `oi_net_value_put` columns from the
  existing DB.
- Any change to the regular `optContractsDate` options report or `OptionsPosition`'s
  inherited columns.
- Dashboard layout/view changes (the view model contract is unchanged).
