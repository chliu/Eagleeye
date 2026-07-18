# MTX/TMF 散戶多空比 → 散戶淨部位 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the MTX/TMF 散戶多空比 (%) dashboard panels with 散戶淨部位 (retail net position, in contracts) for both MTX and TMF.

**Architecture:** `DashboardService.retailRatio(...)` (returns `Double`, a percentage) becomes `retailNetPosition(...)` (returns `Long`, a signed contract count) by dropping the `/ totalOi × 100` normalization it currently applies. This cascades through `DashboardViewModel`'s `mtxRatio`/`tmfRatio` fields (renamed + retyped to `mtxNetPosition`/`tmfNetPosition`, `List<Long>`) and into `dashboard.html`'s two chart panels (new "口" formatter instead of "%").

**Tech Stack:** Java 25 / Spring Boot, JUnit 5 + Mockito + AssertJ, Thymeleaf + Chart.js (vanilla, inline `<script th:inline="javascript">`).

**Reference:** [docs/superpowers/specs/2026-07-18-mtx-tmf-retail-net-position-design.md](../specs/2026-07-18-mtx-tmf-retail-net-position-design.md)

---

### Task 1: Backend calculation, ViewModel, and tests

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardViewModel.java`
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java`
- Test: `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java`

These three files don't compile independently of each other (the test calls `vm.mtxNetPosition()`, which only exists once the record is renamed; the service populates that record). They're done as one atomic task, following the same pattern used for the original 散戶多空比 feature.

`eagleeye-web/src/test/java/com/eagleeye/web/DashboardControllerTest.java` needs **no changes** — its `emptyVm(int days)` helper builds the record with `List.of()` for every field, which type-infers against whatever the record's declared field type is, so it adapts automatically to the `List<Double>` → `List<Long>` change.

- [ ] **Step 1: Update the 4 existing tests to expect net-position values**

In `DashboardServiceTest.java`, find:

```java
    @Test
    void buildViewModel_computesMtxRetailRatio_whenAllInstitutionalTypesPresent() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(
                futures(d, "MTX", TraderType.DEALER, 100L, 50L),
                futures(d, "MTX", TraderType.INVESTMENT_TRUST, 50L, 25L),
                futures(d, "MTX", TraderType.FINI, 200L, 100L)
            ));
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(futuresMarketOi(d, "MTX", 1000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        // institutionalLong=350, institutionalShort=175, totalOi=1000
        // retailLong=650, retailShort=825, ratio=(650-825)/1000*100=-17.5
        assertThat(vm.mtxRatio()).containsExactly(-17.5);
    }

    @Test
    void buildViewModel_mtxRetailRatio_missingTraderTypeContributesZero() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(futures(d, "MTX", TraderType.FINI, 200L, 100L)));
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(futuresMarketOi(d, "MTX", 1000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        // institutionalLong=200, institutionalShort=100, totalOi=1000
        // retailLong=800, retailShort=900, ratio=(800-900)/1000*100=-10.0
        assertThat(vm.mtxRatio()).containsExactly(-10.0);
    }

    @Test
    void buildViewModel_mtxRetailRatio_null_whenTotalOiMissing() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(futures(d, "MTX", TraderType.FINI, 200L, 100L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.mtxRatio()).containsExactly((Double) null);
    }

    @Test
    void buildViewModel_computesTmfRetailRatio_independentlyOfMtx() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of());
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), any(), any()))
            .thenReturn(List.of(futures(d, "TMF", TraderType.FINI, 500L, 500L)));
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of());
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), any(), any()))
            .thenReturn(List.of(futuresMarketOi(d, "TMF", 2000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        // institutionalLong=institutionalShort=500 -> retailLong=retailShort=1500 -> ratio=0.0
        assertThat(vm.tmfRatio()).containsExactly(0.0);
        assertThat(vm.mtxRatio()).containsExactly((Double) null);
    }
```

Replace with:

```java
    @Test
    void buildViewModel_computesMtxRetailNetPosition_whenAllInstitutionalTypesPresent() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(
                futures(d, "MTX", TraderType.DEALER, 100L, 50L),
                futures(d, "MTX", TraderType.INVESTMENT_TRUST, 50L, 25L),
                futures(d, "MTX", TraderType.FINI, 200L, 100L)
            ));
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(futuresMarketOi(d, "MTX", 1000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        // institutionalLong=350, institutionalShort=175, totalOi=1000
        // retailLong=650, retailShort=825, netPosition=650-825=-175
        assertThat(vm.mtxNetPosition()).containsExactly(-175L);
    }

    @Test
    void buildViewModel_mtxRetailNetPosition_missingTraderTypeContributesZero() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(futures(d, "MTX", TraderType.FINI, 200L, 100L)));
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(futuresMarketOi(d, "MTX", 1000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        // institutionalLong=200, institutionalShort=100, totalOi=1000
        // retailLong=800, retailShort=900, netPosition=800-900=-100
        assertThat(vm.mtxNetPosition()).containsExactly(-100L);
    }

    @Test
    void buildViewModel_mtxRetailNetPosition_null_whenTotalOiMissing() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of(futures(d, "MTX", TraderType.FINI, 200L, 100L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.mtxNetPosition()).containsExactly((Long) null);
    }

    @Test
    void buildViewModel_computesTmfRetailNetPosition_independentlyOfMtx() {
        LocalDate d = LocalDate.of(2025, 3, 3);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, 2100000L)));
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of());
        when(futuresRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), any(), any()))
            .thenReturn(List.of(futures(d, "TMF", TraderType.FINI, 500L, 500L)));
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("MTX"), any(), any()))
            .thenReturn(List.of());
        when(futuresMarketOiRepo.findByContractAndTradeDateBetweenOrderByTradeDateAsc(eq("TMF"), any(), any()))
            .thenReturn(List.of(futuresMarketOi(d, "TMF", 2000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        // institutionalLong=institutionalShort=500 -> retailLong=retailShort=1500 -> netPosition=0
        assertThat(vm.tmfNetPosition()).containsExactly(0L);
        assertThat(vm.mtxNetPosition()).containsExactly((Long) null);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl eagleeye-web -am -Dtest=DashboardServiceTest -q`
Expected: FAIL — compile error (`DashboardViewModel` doesn't yet have `mtxNetPosition()`/`tmfNetPosition()` accessors).

- [ ] **Step 3: Rename and retype the `DashboardViewModel` fields**

In `DashboardViewModel.java`, find:

```java
        List<Double> mtxRatio,
        List<Double> tmfRatio,
```

Replace with:

```java
        List<Long>   mtxNetPosition,
        List<Long>   tmfNetPosition,
```

- [ ] **Step 4: Update `DashboardService`'s list declarations**

In `DashboardService.java`, find:

```java
        List<Double> mtxRatio       = new ArrayList<>();
        List<Double> tmfRatio       = new ArrayList<>();
```

Replace with:

```java
        List<Long>   mtxNetPosition = new ArrayList<>();
        List<Long>   tmfNetPosition = new ArrayList<>();
```

- [ ] **Step 5: Update the per-date call sites**

Find:

```java
            mtxRatio.add(retailRatio(mtxByDate.get(date), mtxOiMap.get(date)));
            tmfRatio.add(retailRatio(tmfByDate.get(date), tmfOiMap.get(date)));
```

Replace with:

```java
            mtxNetPosition.add(retailNetPosition(mtxByDate.get(date), mtxOiMap.get(date)));
            tmfNetPosition.add(retailNetPosition(tmfByDate.get(date), tmfOiMap.get(date)));
```

- [ ] **Step 6: Update the `DashboardViewModel` constructor call**

Find:

```java
        return new DashboardViewModel(
            isoDates, dateLabels, taiexClose, spotNetFlow,
            marginChange,
            futuresLongOI, futuresShortOI,
            optionsCallOI, optionsPutOI,
            optionsCallNetValue, optionsPutNetValue,
            futuresAhLong, futuresAhShort, futuresAhNet,
            mtxRatio, tmfRatio,
            days);
    }
```

Replace with:

```java
        return new DashboardViewModel(
            isoDates, dateLabels, taiexClose, spotNetFlow,
            marginChange,
            futuresLongOI, futuresShortOI,
            optionsCallOI, optionsPutOI,
            optionsCallNetValue, optionsPutNetValue,
            futuresAhLong, futuresAhShort, futuresAhNet,
            mtxNetPosition, tmfNetPosition,
            days);
    }
```

- [ ] **Step 7: Replace `retailRatio` with `retailNetPosition`**

Find:

```java
    /**
     * 散戶多空比 = (retailLong - retailShort) / totalOi × 100, where retail = market-wide
     * total OI minus the three institutional trader types combined (dealer+trust+FINI).
     * Null when totalOi is unavailable for the date (chart gap); a missing trader type
     * in {@code institutional} contributes 0 (not a null-the-whole-row).
     */
    private static Double retailRatio(List<FuturesPosition> institutional, FuturesMarketOi marketOi) {
        if (marketOi == null || marketOi.getTotalOi() == null) return null;
        long totalOi = marketOi.getTotalOi();
        long institutionalLong  = sumOi(institutional, FuturesPosition::getOiLongVolume);
        long institutionalShort = sumOi(institutional, FuturesPosition::getOiShortVolume);
        long retailLong  = totalOi - institutionalLong;
        long retailShort = totalOi - institutionalShort;
        return (retailLong - retailShort) / (double) totalOi * 100.0;
    }
```

Replace with:

```java
    /**
     * 散戶淨部位 = retailLong - retailShort, where retail = market-wide total OI minus
     * the three institutional trader types combined (dealer+trust+FINI).
     * Null when totalOi is unavailable for the date (chart gap); a missing trader type
     * in {@code institutional} contributes 0 (not a null-the-whole-row).
     */
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

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn test -pl eagleeye-web -am -Dtest=DashboardServiceTest -q`
Expected: PASS — all tests green, including the 4 updated ones.

- [ ] **Step 9: Compile the whole reactor to catch any other callers**

Run: `mvn compile -pl eagleeye-web -am -q`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Run `DashboardControllerTest` to confirm it needed no changes**

Run: `mvn test -pl eagleeye-web -am -Dtest=DashboardControllerTest -q`
Expected: PASS — confirms the `List.of()`-based `emptyVm()` helper adapted to the field retype without modification.

- [ ] **Step 11: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/DashboardViewModel.java eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java
git commit -m "feat(dashboard): compute MTX/TMF 散戶淨部位 instead of 散戶多空比"
```

---

### Task 2: `dashboard.html` — update the two chart panels

**Files:**
- Modify: `eagleeye-web/src/main/resources/templates/dashboard.html`

Canvas DOM ids (`mtxRatioChart`/`tmfRatioChart`) and internal JS option-variable names (`mtxRatioOpts`, `tmfRatioOpts`, `mtxAbs`, `tmfAbs`) are left unchanged — they're implementation-local, not user-visible, and renaming them would only add diff noise.

- [ ] **Step 1: Update the two chart-card titles**

Find:

```html
  <div class="chart-grid" style="margin-top:10px;">
    <div class="chart-card">
      <div class="chart-title">小台指多空比 vs 收盤價</div>
      <canvas id="mtxRatioChart"></canvas>
    </div>

    <div class="chart-card">
      <div class="chart-title">微台指多空比 vs 收盤價</div>
      <canvas id="tmfRatioChart"></canvas>
    </div>
  </div>
```

Replace with:

```html
  <div class="chart-grid" style="margin-top:10px;">
    <div class="chart-card">
      <div class="chart-title">小台指散戶淨部位 vs 收盤價</div>
      <canvas id="mtxRatioChart"></canvas>
    </div>

    <div class="chart-card">
      <div class="chart-title">微台指散戶淨部位 vs 收盤價</div>
      <canvas id="tmfRatioChart"></canvas>
    </div>
  </div>
```

- [ ] **Step 2: Rename the Thymeleaf-bound data arrays**

Find:

```javascript
const mtxRatio      = /*[[${vm.mtxRatio}]]*/ [];
const tmfRatio      = /*[[${vm.tmfRatio}]]*/ [];
```

Replace with:

```javascript
const mtxNetPosition = /*[[${vm.mtxNetPosition}]]*/ [];
const tmfNetPosition = /*[[${vm.tmfNetPosition}]]*/ [];
```

- [ ] **Step 3: Replace the percentage formatter and the two chart configs**

Find:

```javascript
// 散戶多空比 = (散戶多單-散戶空單)/契約全合約OI×100%
const fmtPct = v => (v >= 0 ? '+' : '') + v.toFixed(2) + '%';

// Chart 5: 小台指多空比 vs 收盤價
const mtxAbs = absMax(mtxRatio);
const mtxRatioOpts = opts(fmtPct);
mtxRatioOpts.scales.y.min = -mtxAbs; mtxRatioOpts.scales.y.max = mtxAbs;
mtxRatioOpts.plugins.tooltip.callbacks = {
  label: ctx => {
    const v = ctx.parsed.y;
    if (ctx.dataset.yAxisID === 'y2') return ctx.dataset.label + ': ' + (v >= 1000 ? (v / 1000).toFixed(0) + 'K' : v);
    return ctx.dataset.label + ': ' + fmtPct(v);
  }
};

new Chart(document.getElementById('mtxRatioChart'), {
  data: {
    labels,
    datasets: [
      { type: 'bar', label: '小台指多空比', data: mtxRatio, backgroundColor: mtxRatio.map(v => v >= 0 ? RED : GREEN), yAxisID: 'y', order: 1 },
      taiexDs()
    ]
  },
  options: mtxRatioOpts
});

// Chart 6: 微台指多空比 vs 收盤價
const tmfAbs = absMax(tmfRatio);
const tmfRatioOpts = opts(fmtPct);
tmfRatioOpts.scales.y.min = -tmfAbs; tmfRatioOpts.scales.y.max = tmfAbs;
tmfRatioOpts.plugins.tooltip.callbacks = {
  label: ctx => {
    const v = ctx.parsed.y;
    if (ctx.dataset.yAxisID === 'y2') return ctx.dataset.label + ': ' + (v >= 1000 ? (v / 1000).toFixed(0) + 'K' : v);
    return ctx.dataset.label + ': ' + fmtPct(v);
  }
};

new Chart(document.getElementById('tmfRatioChart'), {
  data: {
    labels,
    datasets: [
      { type: 'bar', label: '微台指多空比', data: tmfRatio, backgroundColor: tmfRatio.map(v => v >= 0 ? RED : GREEN), yAxisID: 'y', order: 1 },
      taiexDs()
    ]
  },
  options: tmfRatioOpts
});
```

Replace with:

```javascript
// 散戶淨部位 = 散戶多單 - 散戶空單 (contracts)
const fmtLots = v => (v >= 0 ? '+' : '') + v.toFixed(0) + ' 口';

// Chart 5: 小台指散戶淨部位 vs 收盤價
const mtxAbs = absMax(mtxNetPosition);
const mtxRatioOpts = opts(fmtLots);
mtxRatioOpts.scales.y.min = -mtxAbs; mtxRatioOpts.scales.y.max = mtxAbs;
mtxRatioOpts.plugins.tooltip.callbacks = {
  label: ctx => {
    const v = ctx.parsed.y;
    if (ctx.dataset.yAxisID === 'y2') return ctx.dataset.label + ': ' + (v >= 1000 ? (v / 1000).toFixed(0) + 'K' : v);
    return ctx.dataset.label + ': ' + fmtLots(v);
  }
};

new Chart(document.getElementById('mtxRatioChart'), {
  data: {
    labels,
    datasets: [
      { type: 'bar', label: '小台指散戶淨部位', data: mtxNetPosition, backgroundColor: mtxNetPosition.map(v => v >= 0 ? RED : GREEN), yAxisID: 'y', order: 1 },
      taiexDs()
    ]
  },
  options: mtxRatioOpts
});

// Chart 6: 微台指散戶淨部位 vs 收盤價
const tmfAbs = absMax(tmfNetPosition);
const tmfRatioOpts = opts(fmtLots);
tmfRatioOpts.scales.y.min = -tmfAbs; tmfRatioOpts.scales.y.max = tmfAbs;
tmfRatioOpts.plugins.tooltip.callbacks = {
  label: ctx => {
    const v = ctx.parsed.y;
    if (ctx.dataset.yAxisID === 'y2') return ctx.dataset.label + ': ' + (v >= 1000 ? (v / 1000).toFixed(0) + 'K' : v);
    return ctx.dataset.label + ': ' + fmtLots(v);
  }
};

new Chart(document.getElementById('tmfRatioChart'), {
  data: {
    labels,
    datasets: [
      { type: 'bar', label: '微台指散戶淨部位', data: tmfNetPosition, backgroundColor: tmfNetPosition.map(v => v >= 0 ? RED : GREEN), yAxisID: 'y', order: 1 },
      taiexDs()
    ]
  },
  options: tmfRatioOpts
});
```

- [ ] **Step 4: Compile to verify the module still builds**

Run: `mvn compile -pl eagleeye-web -am -q`
Expected: BUILD SUCCESS (Thymeleaf templates aren't compiled, but this confirms nothing else broke).

- [ ] **Step 5: Grep for stale references**

Run: `command grep -n "mtxRatio\b\|tmfRatio\b\|fmtPct\|多空比" eagleeye-web/src/main/resources/templates/dashboard.html`
Expected: no matches (all instances renamed to `mtxNetPosition`/`tmfNetPosition`/`fmtLots`/`散戶淨部位` in this task's edits). Note: this repo's shell has a `grep` function that shells out to a broken Node binary — use `command grep` to bypass it, not plain `grep`.

- [ ] **Step 6: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/dashboard.html
git commit -m "feat(dashboard): render MTX/TMF 散戶淨部位 panels instead of 散戶多空比"
```

---

### Task 3: Full build + manual verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `mvn test -q`
Expected: BUILD SUCCESS, all modules pass.

- [ ] **Step 2: Start the web app**

Run: `mvn spring-boot:run -pl eagleeye-web -am`
Wait for `Started EagleeyeWebApplication` in the log.

- [ ] **Step 3: Load the dashboard and confirm the two panels**

Open `http://localhost:8080/dashboard` in a browser (or use the `claude-in-chrome` tooling available in this session). Confirm:
- The two panels are now titled "小台指散戶淨部位 vs 收盤價" and "微台指散戶淨部位 vs 收盤價".
- Bars are colored red/green matching sign, a 加權指數 line renders on the right axis, and hovering a bar shows a tooltip formatted like `+1234 口` / `-567 口` (not a `%`).
- No errors in the browser console or server log. If the local dev DB has no `futures_market_oi` rows for recent dates, the charts render empty/flat — that's a pre-existing data-availability condition, not a bug introduced by this change.

- [ ] **Step 4: Stop the app**

Kill the `spring-boot:run` process (Ctrl+C, or `run_in_background` termination if launched that way).

No commit for this task — it's verification only.
