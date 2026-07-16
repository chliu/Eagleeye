# 外資期貨 TX-Equivalent Net Position Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Combine TX, MTX, and TMF FINI futures positions into TX-equivalent lots (MTX/4, TMF/20) so the dashboard's 外資期貨部位 panel reflects true combined exposure instead of TX alone.

**Architecture:** `DashboardService.buildViewModel` currently queries `FuturesPositionRepository` once for contract `"TX"`. Extend it to query `"MTX"` and `"TMF"` as well, index each by date, and replace the per-date long/short lookup with a small helper that sums `TX + MTX/4.0 + TMF/20.0` and rounds to the nearest whole lot. `DashboardViewModel` field names/types are unchanged, so `dashboard.html` needs no edits.

**Tech Stack:** Java 25, Spring Boot 4, JUnit 5, Mockito, AssertJ.

Spec: `docs/superpowers/specs/2026-07-16-futures-tx-equivalent-net-position-design.md`

---

### Task 1: Write failing tests for TX-equivalent combination

**Files:**
- Modify: `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java`

- [ ] **Step 1: Add a contract-aware overload of the `futures()` helper**

The existing helper at line 50 hardcodes contract `"TX"`. Add an overload that accepts the contract code, needed to stub MTX/TMF positions:

```java
    FuturesPosition futures(LocalDate date, String contract, long oiLong, long oiShort) {
        FuturesPosition fp = new FuturesPosition(date, contract, TraderType.FINI);
        fp.setOiLongVolume(oiLong);
        fp.setOiShortVolume(oiShort);
        fp.setOiNetVolume(oiLong - oiShort);
        return fp;
    }
```

Insert this immediately after the existing `futures(LocalDate date, long oiLong, long oiShort)` helper (after line 56 in the current file).

- [ ] **Step 2: Add the three new test cases**

Insert these after `buildViewModel_computesFuturesOI` (after line 136 in the current file):

```java
    @Test
    void buildViewModel_combinesTxMtxTmfIntoTxEquivalentNetPosition() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "TX", 1000L, 600L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "MTX", 400L, 200L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "TMF", 200L, 100L)));

        DashboardViewModel vm = service.buildViewModel(20);

        // long:  1000 + 400/4  + 200/20 = 1000 + 100 + 10 = 1110
        // short:  600 + 200/4  + 100/20 =  600 +  50 +  5 =  655
        assertThat(vm.futuresLongOI()).containsExactly(1110L);
        assertThat(vm.futuresShortOI()).containsExactly(655L);
    }

    @Test
    void buildViewModel_roundsFractionalTxEquivalentToNearestLot() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "TX", 1000L, 600L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, "MTX", 3L, 1L))); // 3/4=0.75, 1/4=0.25
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of());

        DashboardViewModel vm = service.buildViewModel(20);

        // long:  1000 + 0.75 = 1000.75 -> rounds to 1001
        // short:  600 + 0.25 =  600.25 -> rounds to  600
        assertThat(vm.futuresLongOI()).containsExactly(1001L);
        assertThat(vm.futuresShortOI()).containsExactly(600L);
    }

    @Test
    void buildViewModel_futuresRowStaysTxOnlyWhenMtxTmfMissingForOneDate() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2100000L), taiex(d2, 2110000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d1, "TX", 1000L, 600L), futures(d2, "TX", 900L, 900L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d1, "MTX", 400L, 200L))); // only d1 has MTX data
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of());

        DashboardViewModel vm = service.buildViewModel(20);

        // d1: 1000 + 400/4 = 1100 long, 600 + 200/4 = 650 short
        // d2: TX only that day (no MTX/TMF row) -> 900 long, 900 short
        assertThat(vm.futuresLongOI()).containsExactly(1100L, 900L);
        assertThat(vm.futuresShortOI()).containsExactly(650L, 900L);
    }
```

- [ ] **Step 3: Run the new tests to verify they fail**

Run: `mvn -pl eagleeye-web -am test -Dtest=DashboardServiceTest -q`
Expected: The three new tests FAIL (actual long/short values equal the TX-only figures, e.g. `1000L` instead of `1110L`) because `DashboardService` doesn't query MTX/TMF yet. The pre-existing tests still PASS.

- [ ] **Step 4: Commit**

```bash
git add eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java
git commit -m "test(dashboard): add failing tests for TX-equivalent futures net position"
```

---

### Task 2: Implement TX-equivalent combination in DashboardService

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java:68-69` (repository queries)
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java:86` (indexByDate map)
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java:126` (per-date lookup)
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java:146-147` (long/short assignment)
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java:170-172` (add helper method near `balanceDelta`)

- [ ] **Step 1: Replace the single TX futures query with TX/MTX/TMF queries**

Replace (current lines 68-69):

```java
        List<FuturesPosition>   futuresList = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TX", TraderType.FINI, from, to);
```

with:

```java
        List<FuturesPosition>   futuresTxList  = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TX", TraderType.FINI, from, to);
        List<FuturesPosition>   futuresMtxList = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("MTX", TraderType.FINI, from, to);
        List<FuturesPosition>   futuresTmfList = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TMF", TraderType.FINI, from, to);
```

- [ ] **Step 2: Replace the single futures index map with three maps**

Replace (current line 86):

```java
        Map<LocalDate, FuturesPosition>   futMap   = indexByDate(futuresList, FuturesPosition::getTradeDate);
```

with:

```java
        Map<LocalDate, FuturesPosition>   futTxMap  = indexByDate(futuresTxList,  FuturesPosition::getTradeDate);
        Map<LocalDate, FuturesPosition>   futMtxMap = indexByDate(futuresMtxList, FuturesPosition::getTradeDate);
        Map<LocalDate, FuturesPosition>   futTmfMap = indexByDate(futuresTmfList, FuturesPosition::getTradeDate);
```

- [ ] **Step 3: Replace the per-date futures lookup**

Replace (current line 126):

```java
            FuturesPosition   fp = futMap.get(date);
```

with:

```java
            FuturesPosition   txFp  = futTxMap.get(date);
            FuturesPosition   mtxFp = futMtxMap.get(date);
            FuturesPosition   tmfFp = futTmfMap.get(date);
```

- [ ] **Step 4: Replace the long/short assignment to use the combined TX-equivalent value**

Replace (current lines 146-147):

```java
            futuresLongOI.add(fp != null ? fp.getOiLongVolume()  : null);
            futuresShortOI.add(fp != null ? fp.getOiShortVolume() : null);
```

with:

```java
            futuresLongOI.add(txEquivalent(txFp, mtxFp, tmfFp, FuturesPosition::getOiLongVolume));
            futuresShortOI.add(txEquivalent(txFp, mtxFp, tmfFp, FuturesPosition::getOiShortVolume));
```

- [ ] **Step 5: Add the `txEquivalent` helper method**

Add this method immediately after `balanceDelta` (current lines 170-172):

```java
    /**
     * Combines TX, MTX (1 TX = 4 MTX), and TMF (1 TX = 20 TMF) into a single
     * TX-equivalent lot count, rounded to the nearest whole lot. Missing MTX/TMF
     * data for a date contributes 0; a missing TX position yields null (no row).
     */
    private static Long txEquivalent(FuturesPosition tx, FuturesPosition mtx, FuturesPosition tmf,
                                      Function<FuturesPosition, Long> field) {
        if (tx == null) return null;
        double total = field.apply(tx)
            + (mtx != null ? field.apply(mtx) / 4.0 : 0.0)
            + (tmf != null ? field.apply(tmf) / 20.0 : 0.0);
        return Math.round(total);
    }
```

`Function` is already imported in this file (`java.util.function.Function`), so no import changes are needed.

- [ ] **Step 6: Run the full DashboardServiceTest suite**

Run: `mvn -pl eagleeye-web -am test -Dtest=DashboardServiceTest -q`
Expected: All tests PASS, including the three new ones from Task 1 and every pre-existing test (`buildViewModel_computesFuturesOI` et al. keep passing because unstubbed MTX/TMF calls return empty lists, contributing 0).

- [ ] **Step 7: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java
git commit -m "feat(dashboard): combine TX/MTX/TMF into TX-equivalent 外資期貨 net position"
```

---

### Task 3: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full eagleeye-web test suite**

Run: `mvn -pl eagleeye-web -am test -q`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Run the full multi-module build**

Run: `mvn test -q`
Expected: BUILD SUCCESS across all modules (confirms no other module referenced the removed `futuresList`/`futMap` variable names — they're private to `DashboardService`, so this is a sanity check, not an expected source of failures).
