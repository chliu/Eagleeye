# MTX/TMF 散戶多空比 → 散戶淨部位 — Design Spec

**Date:** 2026-07-18
**Status:** Approved

---

## 1. Goal

Replace the existing 小台指(MTX)/微台指(TMF) 散戶多空比 (%) dashboard panels with 散戶淨部位 (retail net position, in contracts). Same two panels, same layout, different metric: instead of normalizing the retail long/short imbalance by total open interest and expressing it as a percentage, show the raw contract-count imbalance.

This is a follow-on to [2026-07-17-mtx-tmf-retail-ratio-design.md](2026-07-17-mtx-tmf-retail-ratio-design.md), which introduced the 散戶多空比 panels and the `FuturesMarketOi` total-OI data source they depend on. That data source and its collector are unaffected by this change — only the derived metric and its presentation change.

## 2. Formula

```
多單 (retail long)  = 全合約OI − 三大法人多單
空單 (retail short) = 全合約OI − 三大法人空單
散戶淨部位 = 多單 − 空單
```

where 三大法人 = DEALER + INVESTMENT_TRUST + FINI combined, and 全合約OI = total open interest across all trader types and all contract months for that commodity, on that date — identical definitions to the existing 散戶多空比 feature.

The only change from the current formula is dropping the `/ 全合約OI × 100` normalization: the new value is a signed contract count, not a percentage.

Applies to both MTX and TMF (both panels convert, for consistency — not just MTX).

## 3. Scope

In scope:
- `DashboardService`: change the MTX/TMF calculation from ratio (%) to net position (contract count).
- `DashboardViewModel`: retype/rename the two affected fields.
- `dashboard.html`: update the two chart panels' data binding, formatting, titles, and legend labels.
- Update existing unit tests that assert on the old percentage values.

Out of scope (unchanged from the prior feature):
- The `FuturesMarketOi` data source, its TAIFEX collector, parser, entity, repository, and deployment wiring.
- Panel layout, canvas DOM ids, chart type (bar + TAIEX line), symmetric y-axis approach, and sign-based red/green coloring.
- TX (not part of either the original or this feature).

## 4. Backend Calculation (`DashboardService`)

`retailRatio(List<FuturesPosition> institutional, FuturesMarketOi marketOi)` (returns `Double`, a percentage) is replaced by:

```java
private static Long retailNetPosition(List<FuturesPosition> institutional, FuturesMarketOi marketOi) {
    if (marketOi == null || marketOi.getTotalOi() == null) return null;
    long totalOi = marketOi.getTotalOi();
    long institutionalLong  = sumOi(institutional, FuturesPosition::getOiLongVolume);
    long institutionalShort = sumOi(institutional, FuturesPosition::getOiShortVolume);
    long retailLong  = totalOi - institutionalLong;
    long retailShort = totalOi - institutionalShort;
    return retailLong - retailShort;
}
```

- `sumOi` is reused unchanged (missing trader type contributes 0 to the institutional sum, same as today).
- Null-gating on missing `totalOi` is unchanged (chart gap for that date).
- Call sites `mtxRatio.add(retailRatio(...))` / `tmfRatio.add(retailRatio(...))` become `mtxNetPosition.add(retailNetPosition(...))` / `tmfNetPosition.add(retailNetPosition(...))`, with the local list variables retyped `List<Long>`.

## 5. `DashboardViewModel`

```
List<Double> mtxRatio, tmfRatio    →    List<Long> mtxNetPosition, tmfNetPosition
```

Positional record field order otherwise unchanged.

## 6. UI (`dashboard.html`)

- Inline JS: `const mtxRatio = /*[[${vm.mtxRatio}]]*/ [];` → `const mtxNetPosition = /*[[${vm.mtxNetPosition}]]*/ [];` (same for tmf).
- New formatter, replacing `fmtPct` for these two charts:
  ```js
  const fmtLots = v => (v >= 0 ? '+' : '') + v.toFixed(0) + ' 口';
  ```
  Used for both the y-axis tick callback (`opts(fmtLots)`) and the tooltip label callback (replacing `fmtPct(v)`).
- Chart titles:
  - "小台指多空比 vs 收盤價" → "小台指散戶淨部位 vs 收盤價"
  - "微台指多空比 vs 收盤價" → "微台指散戶淨部位 vs 收盤價"
- Dataset legend labels:
  - "小台指多空比" → "小台指散戶淨部位"
  - "微台指多空比" → "微台指散戶淨部位"
- `mtxRatio.map(v => v >= 0 ? RED : GREEN)` → `mtxNetPosition.map(...)` (same sign-based coloring convention, unchanged threshold), same for tmf.
- Symmetric y-axis via `absMax` unchanged, just computed over the renamed net-position arrays.
- **Unchanged, deliberately:** canvas DOM ids (`mtxRatioChart`/`tmfRatioChart`) and internal JS option-variable names (`mtxRatioOpts`, `tmfRatioOpts`, `mtxAbs`, `tmfAbs`) — these are implementation-local and not worth renaming for this change.

## 7. Testing

- `DashboardServiceTest`: update the existing percentage-based assertions to net-position math:
  - `vm.mtxRatio()` / `vm.tmfRatio()` → `vm.mtxNetPosition()` / `vm.tmfNetPosition()`.
  - Recalculate expected values as `Long` (e.g. current `-17.5` (%) case's underlying retailLong/retailShort inputs now assert the raw `retailLong - retailShort` count instead).
  - Missing-`totalOi` → `null` case is preserved as-is (same gating, different type: `(Long) null`).
- `DashboardControllerTest`: update the `DashboardViewModel` constructor call to pass `List<Long>` for the renamed fields.
- Manual: run the app, hit `/dashboard`, confirm both panels render bars (colored by sign, unit "口") + TAIEX line, values match hand-computed `retailLong - retailShort` for a known date.

## 8. Edge Cases

Unchanged from the prior feature (still governed by the same `totalOi`/institutional-sum null-safety):
- Row visibility: MTX/TMF net-position rows are independently null-gated by their own `totalOi` availability.
- Partial institutional data: a missing trader type for a date contributes 0 to the institutional sum, not a null row.
