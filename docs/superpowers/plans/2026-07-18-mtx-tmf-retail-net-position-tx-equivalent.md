# MTX/TMF 散戶淨部位 TX-Equivalent Conversion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the MTX/TMF 散戶淨部位 dashboard panels from native per-contract lot counts to 大台 (TX) equivalent lots (MTX ÷ 4, TMF ÷ 20), so all position figures on the dashboard share one baseline.

**Architecture:** Add a `divisor` parameter to `DashboardService.retailNetPosition`, dividing the already-computed `retailLong - retailShort` by that divisor and rounding to the nearest whole lot with `Math.round` — the same pattern the existing `txEquivalent()` helper uses for the 外資期貨 panel. Update the two call sites (MTX → 4.0, TMF → 20.0), annotate the two chart panels' titles/labels in `dashboard.html` to say "（大台等值）", and update/add `DashboardServiceTest` cases for the new math.

**Tech Stack:** Java 25, Spring Boot 4, JUnit 5, Mockito (strict stubs), AssertJ, Thymeleaf + Chart.js.

**Spec:** `docs/superpowers/specs/2026-07-18-mtx-tmf-retail-net-position-tx-equivalent-design.md`

---

### Task 1: Update `DashboardServiceTest` for TX-equivalent expectations (RED)

**Files:**
- Modify: `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java:214-234` (MTX, all institutional types)
- Modify: `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java:236-252` (MTX, missing trader type)
- Modify: `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java` (new test, insert after the existing TMF independence test at line 289)

The two MTX tests currently assert the raw (pre-conversion) net position. Once Task 2 lands, the divisor makes these fail unless updated first. The existing TMF test (`buildViewModel_computesTmfRetailNetPosition_independentlyOfMtx`) asserts `0L`, which is `0` under any divisor — leave it as-is, since it verifies MTX/TMF independence, not rounding. Add a new test that exercises TMF's `/20` rounding, since no existing test covers a non-multiple-of-20 TMF net position.

- [ ] **Step 1: Update the "all institutional types present" MTX test**

Replace the comment and assertion at `DashboardServiceTest.java:231-233`:

```java
        // institutionalLong=350, institutionalShort=175, totalOi=1000
        // retailLong=650, retailShort=825, raw netPosition=650-825=-175
        // TX-equivalent = -175 / 4.0 = -43.75 -> Math.round -> -44
        assertThat(vm.mtxNetPosition()).containsExactly(-44L);
```

- [ ] **Step 2: Update the "missing trader type" MTX test**

Replace the comment and assertion at `DashboardServiceTest.java:249-251`:

```java
        // institutionalLong=200, institutionalShort=100, totalOi=1000
        // retailLong=800, retailShort=900, raw netPosition=800-900=-100
        // TX-equivalent = -100 / 4.0 = -25.0 -> Math.round -> -25
        assertThat(vm.mtxNetPosition()).containsExactly(-25L);
```

- [ ] **Step 3: Add a new TMF rounding test**

Insert immediately after `buildViewModel_computesTmfRetailNetPosition_independentlyOfMtx` (after line 289, before the closing `}` of the test class):

```java
    @Test
    void buildViewModel_tmfRetailNetPosition_roundsToNearestTxEquivalentLot() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of());
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), any(), any()))
            .thenReturn(List.of(futures(d, "TMF", TraderType.FINI, 200L, 95L)));
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of());
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), any(), any()))
            .thenReturn(List.of(futuresMarketOi(d, "TMF", 2000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        // institutionalLong=200, institutionalShort=95, totalOi=2000
        // retailLong=1800, retailShort=1905, raw netPosition=1800-1905=-105
        // TX-equivalent = -105 / 20.0 = -5.25 -> Math.round -> -5
        assertThat(vm.tmfNetPosition()).containsExactly(-5L);
    }
```

Note the explicit empty-list stubs for the `"MTX"` variant of both `futuresRepo` and `futuresMarketOiRepo`: stubbing the `"TMF"` variant of a method without also stubbing the `"MTX"` variant on the same mock trips Mockito's strict stubbing (`PotentialStubbingProblem`) — this exact gap was hit and fixed once already for the sibling independence test (commit `44adcfb`).

- [ ] **Step 4: Run the tests to verify the three changed/new tests fail**

Run: `mvn -pl eagleeye-web -am test -Dtest=DashboardServiceTest`

Expected: FAIL — `buildViewModel_computesMtxRetailNetPosition_whenAllInstitutionalTypesPresent` and `buildViewModel_mtxRetailNetPosition_missingTraderTypeContributesZero` fail because the service still returns raw `-175`/`-100`; `buildViewModel_tmfRetailNetPosition_roundsToNearestTxEquivalentLot` fails because the service still returns raw `-105`. All other tests in the class still pass.

- [ ] **Step 5: Commit**

```bash
git add eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java
git commit -m "test(dashboard): assert TX-equivalent MTX/TMF 散戶淨部位 values"
```

---

### Task 2: Implement the TX-equivalent divisor in `DashboardService` (GREEN)

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java:187-188` (call sites)
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java:202-216` (`retailNetPosition` method + javadoc)

- [ ] **Step 1: Update the call sites**

In `buildViewModel`, replace lines 187-188:

```java
            mtxNetPosition.add(retailNetPosition(mtxByDate.get(date), mtxOiMap.get(date)));
            tmfNetPosition.add(retailNetPosition(tmfByDate.get(date), tmfOiMap.get(date)));
```

with:

```java
            mtxNetPosition.add(retailNetPosition(mtxByDate.get(date), mtxOiMap.get(date), 4.0));
            tmfNetPosition.add(retailNetPosition(tmfByDate.get(date), tmfOiMap.get(date), 20.0));
```

- [ ] **Step 2: Update `retailNetPosition` to accept and apply the divisor**

Replace the method and its javadoc (lines 202-216):

```java
    /**
     * 散戶淨部位 = retailLong - retailShort, where retail = market-wide total OI minus
     * the three institutional trader types combined (dealer+trust+FINI), converted to
     * TX-equivalent lots via {@code divisor} (MTX: 4.0, TMF: 20.0 — same ratios as
     * {@link #txEquivalent}) and rounded to the nearest whole lot.
     * Null when totalOi is unavailable for the date (chart gap); a missing trader type
     * in {@code institutional} contributes 0 (not a null-the-whole-row).
     */
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

- [ ] **Step 3: Run the tests to verify everything passes**

Run: `mvn -pl eagleeye-web -am test -Dtest=DashboardServiceTest`

Expected: PASS — all tests in the class, including the three updated/added in Task 1.

- [ ] **Step 4: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java
git commit -m "feat(dashboard): convert MTX/TMF 散戶淨部位 to TX-equivalent (大台等值)"
```

---

### Task 3: Update `dashboard.html` panel titles and legend labels

**Files:**
- Modify: `eagleeye-web/src/main/resources/templates/dashboard.html:194` (MTX chart title)
- Modify: `eagleeye-web/src/main/resources/templates/dashboard.html:199` (TMF chart title)
- Modify: `eagleeye-web/src/main/resources/templates/dashboard.html:421` (MTX dataset label)
- Modify: `eagleeye-web/src/main/resources/templates/dashboard.html:444` (TMF dataset label)

No backend data changes here — this task only relabels the two panels so the TX-equivalent values aren't mistaken for native MTX/TMF lot counts. There's no unit test for Thymeleaf templates in this project; verification is a manual dashboard check in Task 4.

- [ ] **Step 1: Update the MTX chart title**

At line 194, replace:

```html
      <div class="chart-title">小台指散戶淨部位 vs 收盤價</div>
```

with:

```html
      <div class="chart-title">小台指散戶淨部位（大台等值）vs 收盤價</div>
```

- [ ] **Step 2: Update the TMF chart title**

At line 199, replace:

```html
      <div class="chart-title">微台指散戶淨部位 vs 收盤價</div>
```

with:

```html
      <div class="chart-title">微台指散戶淨部位（大台等值）vs 收盤價</div>
```

- [ ] **Step 3: Update the MTX dataset legend label**

At line 421, replace:

```js
      { type: 'bar', label: '小台指散戶淨部位', data: mtxNetPosition, backgroundColor: mtxNetPosition.map(v => v >= 0 ? RED : GREEN), yAxisID: 'y', order: 1 },
```

with:

```js
      { type: 'bar', label: '小台指散戶淨部位（大台等值）', data: mtxNetPosition, backgroundColor: mtxNetPosition.map(v => v >= 0 ? RED : GREEN), yAxisID: 'y', order: 1 },
```

- [ ] **Step 4: Update the TMF dataset legend label**

At line 444, replace:

```js
      { type: 'bar', label: '微台指散戶淨部位', data: tmfNetPosition, backgroundColor: tmfNetPosition.map(v => v >= 0 ? RED : GREEN), yAxisID: 'y', order: 1 },
```

with:

```js
      { type: 'bar', label: '微台指散戶淨部位（大台等值）', data: tmfNetPosition, backgroundColor: tmfNetPosition.map(v => v >= 0 ? RED : GREEN), yAxisID: 'y', order: 1 },
```

- [ ] **Step 5: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/dashboard.html
git commit -m "feat(dashboard): label MTX/TMF 散戶淨部位 panels as 大台等值"
```

---

### Task 4: Full test suite + manual dashboard verification

**Files:** None (verification only).

- [ ] **Step 1: Run the full `eagleeye-web` test suite**

Run: `mvn -pl eagleeye-web -am test`

Expected: BUILD SUCCESS, no failing tests.

- [ ] **Step 2: Start the app and check the dashboard manually**

Run: `mvn -pl eagleeye-web spring-boot:run` (or the project's usual local-run command), then open `/dashboard` in a browser.

Verify:
- Both panel titles read "...散戶淨部位（大台等值）vs 收盤價".
- Hovering a bar shows the legend label with "（大台等值）" and a "口" value.
- For a known trading date, the MTX bar's magnitude is roughly the native MTX net position ÷ 4, and the TMF bar's magnitude is roughly the native TMF net position ÷ 20 (spot-check against raw numbers if available, e.g. via the shell `futures show` command for that date).

Stop the app once confirmed (Ctrl+C).
