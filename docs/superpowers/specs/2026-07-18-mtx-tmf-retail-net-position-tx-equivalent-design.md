# MTX/TMF 散戶淨部位 → TX-Equivalent (大台等值) — Design Spec

**Date:** 2026-07-18
**Status:** Approved

---

## 1. Goal

The MTX (小台指) and TMF (微台指) 散戶淨部位 panels currently show each contract's raw, native lot count. Since 1 TX = 4 MTX = 20 TMF, the two panels aren't on a comparable scale with each other or with any TX-based figure (e.g. the existing 外資期貨 TX-equivalent panel from [2026-07-16-futures-tx-equivalent-net-position-design.md](2026-07-16-futures-tx-equivalent-net-position-design.md)). Convert both panels' net position to 大台 (TX) equivalent lots so all dashboard position figures share one baseline.

Conversion ratios (same as the existing `txEquivalent()` helper):
- MTX: divide by 4
- TMF: divide by 20

This is a follow-on to [2026-07-18-mtx-tmf-retail-net-position-design.md](2026-07-18-mtx-tmf-retail-net-position-design.md), which introduced these panels as raw contract-count net position. That design's formula for retail long/short is unchanged; only the final result is now converted to TX-equivalent.

## 2. Scope

In scope:
- `DashboardService.retailNetPosition`: divide the computed net position by a per-contract divisor and round to the nearest whole lot.
- `dashboard.html`: annotate the two chart titles and dataset legend labels to indicate the values are now TX-equivalent, so they aren't mistaken for native MTX/TMF lot counts.
- Update existing unit tests whose expected values assert raw (pre-conversion) net position numbers.

Out of scope:
- The retail long/short formula itself (`全合約OI − 三大法人多單/空單`) — unchanged.
- `FuturesMarketOi` data source and collector — unchanged.
- Panel layout, canvas DOM ids, chart type, y-axis symmetry, sign-based coloring, "口" unit formatter — unchanged.
- TX itself (already the baseline unit; no conversion needed).

## 3. Backend Calculation (`DashboardService`)

`retailNetPosition` gains a `divisor` parameter. The institutional/retail long-short sums stay in the contract's native units (unchanged); only the final net position is divided and rounded, mirroring `txEquivalent()`'s `Math.round` pattern:

```java
private static Long retailNetPosition(List<FuturesPosition> institutional, FuturesMarketOi marketOi, double divisor) {
    if (marketOi == null || marketOi.getTotalOi() == null) return null;
    long totalOi = marketOi.getTotalOi();
    long institutionalLong  = sumOi(institutional, FuturesPosition::getOiLongVolume);
    long institutionalShort = sumOi(institutional, FuturesPosition::getOiShortVolume);
    long retailLong  = totalOi - institutionalLong;
    long retailShort = totalOi - institutionalShort;
    return Math.round((retailLong - retailShort) / divisor);
}
```

Call sites:
```java
mtxNetPosition.add(retailNetPosition(mtxByDate.get(date), mtxOiMap.get(date), 4.0));
tmfNetPosition.add(retailNetPosition(tmfByDate.get(date), tmfOiMap.get(date), 20.0));
```

`DashboardViewModel` field names and types (`List<Long>`) are unchanged.

## 4. UI (`dashboard.html`)

- Chart titles:
  - "小台指散戶淨部位 vs 收盤價" → "小台指散戶淨部位（大台等值）vs 收盤價"
  - "微台指散戶淨部位 vs 收盤價" → "微台指散戶淨部位（大台等值）vs 收盤價"
- Dataset legend labels:
  - "小台指散戶淨部位" → "小台指散戶淨部位（大台等值）"
  - "微台指散戶淨部位" → "微台指散戶淨部位（大台等值）"
- No other UI changes: data binding, `fmtLots` formatter, sign-based coloring, and symmetric y-axis all continue to work unchanged since the backend still emits whole-number `Long` values.

## 5. Testing

`DashboardServiceTest`:
- `buildViewModel_computesMtxRetailNetPosition_whenAllInstitutionalTypesPresent`: raw net position -175 → `-175 / 4.0 = -43.75` → `Math.round` → **-44**. Update assertion and inline comment.
- `buildViewModel_mtxRetailNetPosition_missingTraderTypeContributesZero`: raw -100 → `-100 / 4.0 = -25.0` → **-25**. Update assertion and inline comment.
- `buildViewModel_mtxRetailNetPosition_null_whenTotalOiMissing`: unaffected (null short-circuits before division).
- `buildViewModel_computesTmfRetailNetPosition_independentlyOfMtx`: raw net position is 0, so `0 / 20.0 = 0` — this doesn't exercise the `/20` rounding path. Leave this test as-is (it verifies MTX/TMF independence, not rounding). Add a new test, `buildViewModel_tmfRetailNetPosition_roundsToNearestTxEquivalentLot`, with stubbed institutional/totalOi inputs chosen so the raw net position is not an exact multiple of 20, asserting the rounded TX-equivalent result.

## 6. Edge Cases

Unchanged from the prior feature: row visibility (null-gated by `totalOi` per contract, independently for MTX/TMF) and partial institutional data (missing trader type contributes 0) are not affected by adding the divisor step.
