# 外資期貨 TX-Equivalent Net Position — Design Spec

**Date:** 2026-07-16
**Status:** Approved

---

## 1. Goal

The 外資期貨部位 dashboard panel (`/dashboard`) currently reflects only the **TX** (大台指) contract for FINI (外資). Foreign investors also trade **MTX** (小台指) and **TMF** (微台指), so the displayed long/short/net figures understate true exposure. Convert MTX and TMF into TX-equivalent lots and combine with TX to produce the real net position.

Conversion ratios:
- 1 TX = 4 MTX → `MTX_volume / 4`
- 1 TX = 20 TMF → `TMF_volume / 20`

## 2. Scope

Applies only to the web dashboard's 外資期貨 panel (`DashboardService` → `DashboardViewModel.futuresLongOI/futuresShortOI` → `dashboard.html` chart + 淨部位 table). Shell commands (`futures list`, `futures show`) are unaffected — they report raw per-contract data by design and are out of scope.

MTX and TMF data is already collected into `futures_position` by the existing TAIFEX collector (verified present in the production DB); no collector or schema changes are needed.

## 3. Calculation

In `DashboardService.buildViewModel`, fetch FINI positions for TX, MTX, and TMF over the date range using the existing `FuturesPositionRepository.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc` (called three times instead of once). Index each result list by date.

Per trading date:

```
combinedLong  = round(TX.oiLongVolume  + (MTX?.oiLongVolume  ?? 0) / 4.0  + (TMF?.oiLongVolume  ?? 0) / 20.0)
combinedShort = round(TX.oiShortVolume + (MTX?.oiShortVolume ?? 0) / 4.0 + (TMF?.oiShortVolume ?? 0) / 20.0)
```

These values replace the existing `futuresLongOI`/`futuresShortOI` series in `DashboardViewModel` — field names and types (`List<Long>`) are unchanged, so `dashboard.html` requires no edits. The table's client-side `淨部位 = long - short` and the chart's `外資多單`/`外資空單` bars automatically reflect the combined TX-equivalent figures.

## 4. Edge Cases

- **Row visibility:** a date only appears (non-null) when **TX** data exists, matching current behavior (TX anchors gaps in the series today).
- **Partial contract data:** if MTX or TMF is missing for a date where TX exists, that contract contributes 0 rather than nulling the whole row.
- **Rounding:** `Math.round` applied once to the summed double total per long/short (not per-contract), producing a whole-lot integer for display.

## 5. Testing

- Existing `DashboardServiceTest` cases that stub only the TX contract continue to pass unchanged: Mockito returns an empty list for the unstubbed MTX/TMF repository calls, contributing 0, which reproduces today's TX-only result.
- New test cases to add:
  1. TX + MTX + TMF combine correctly using the 4/20 divisors.
  2. Rounding behaves as expected on fractional sums.
  3. MTX/TMF missing for a date still yields a TX-only combined value (not null).

## 6. Out of Scope

- Shell commands and any per-contract reporting.
- The divergence/alert signal logic described in the original foreign-investor-dashboard design doc (not currently implemented in `DashboardService`).
- Options (TXO) — no equivalent mini/micro contract conversion requested.
