# Multi-Day VPOC Structure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show previous N trading days' VPOC / VAH / VAL as fading price lines on the vpoc.html K-line chart, with a 3 / 5 / 10 day toggle.

**Architecture:** New `VpHistoryEntry` record + `VolumeProfileService.getHistory()` method + `GET /api/vp/history` endpoint. Frontend adds days-toggle buttons and `renderHistoryLines()` using `createPriceLine` with opacity decreasing by age.

**Tech Stack:** Java 25, Spring Boot 4, JUnit 5 + AssertJ + Mockito, lightweight-charts v4, Thymeleaf.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `eagleeye-web/src/main/java/com/eagleeye/web/vp/VpHistoryEntry.java` | `(String date, int vpoc, int vah, int val)` DTO |
| Modify | `eagleeye-web/src/main/java/com/eagleeye/web/VolumeProfileService.java` | Add `getHistory()` |
| Modify | `eagleeye-web/src/main/java/com/eagleeye/web/VpController.java` | Add `GET /history` endpoint |
| Modify | `eagleeye-web/src/main/resources/templates/vpoc.html` | Days toggle + `loadHistory()` + `renderHistoryLines()` |
| Modify | `eagleeye-web/src/test/java/com/eagleeye/web/VolumeProfileServiceTest.java` | Add `getHistory` tests |

---

## Task 1: VpHistoryEntry DTO

**Files:**
- Create: `eagleeye-web/src/main/java/com/eagleeye/web/vp/VpHistoryEntry.java`

- [ ] **Step 1: Create the record**

```java
// eagleeye-web/src/main/java/com/eagleeye/web/vp/VpHistoryEntry.java
package com.eagleeye.web.vp;

public record VpHistoryEntry(String date, int vpoc, int vah, int val) {}
```

- [ ] **Step 2: Compile check**

```bash
/opt/homebrew/bin/mvn -f eagleeye-web/pom.xml compile -q
```

Expected: no output (BUILD SUCCESS)

- [ ] **Step 3: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/vp/VpHistoryEntry.java
git commit -m "feat(vp): add VpHistoryEntry DTO"
```

---

## Task 2: getHistory() in VolumeProfileService (TDD)

**Files:**
- Modify: `eagleeye-web/src/test/java/com/eagleeye/web/VolumeProfileServiceTest.java`
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/VolumeProfileService.java`

- [ ] **Step 1: Add test helper and failing tests**

Add `tickFor()` helper and 5 new tests at the end of `VolumeProfileServiceTest`, before the closing `}`:

```java
    TxTick tickFor(LocalDate date, String time, int price, int volume) {
        return new TxTick(date, time, price, volume, "202606", false);
    }

    // ── getHistory tests ──────────────────────────────────────────────────────

    @Test
    void getHistory_returnsMostRecentDatesBeforeCurrent() {
        LocalDate d4 = LocalDate.of(2026, 6, 4);
        LocalDate d3 = LocalDate.of(2026, 6, 3);
        when(repo.findDistinctTradeDates()).thenReturn(List.of(
            LocalDate.of(2026, 6, 5), d4, d3, LocalDate.of(2026, 6, 2)
        ));
        when(repo.findByTradeDateOrderByTimeAsc(d4)).thenReturn(List.of(
            tickFor(d4, "090000", 40200, 100),
            tickFor(d4, "090001", 40300, 200),
            tickFor(d4, "090002", 40100,  50)
        ));
        when(repo.findByTradeDateOrderByTimeAsc(d3)).thenReturn(List.of(
            tickFor(d3, "090000", 40100, 150),
            tickFor(d3, "090001", 40200,  80)
        ));

        List<VpHistoryEntry> result = service.getHistory(LocalDate.of(2026, 6, 5), 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo("20260604");
        assertThat(result.get(1).date()).isEqualTo("20260603");
    }

    @Test
    void getHistory_doesNotIncludeCurrentDate() {
        LocalDate current = LocalDate.of(2026, 6, 5);
        LocalDate prev    = LocalDate.of(2026, 6, 4);
        when(repo.findDistinctTradeDates()).thenReturn(List.of(current, prev));
        when(repo.findByTradeDateOrderByTimeAsc(prev)).thenReturn(List.of(
            tickFor(prev, "090000", 40000, 100)
        ));

        List<VpHistoryEntry> result = service.getHistory(current, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo("20260604");
    }

    @Test
    void getHistory_limitsToRequestedDays() {
        LocalDate d4 = LocalDate.of(2026, 6, 4);
        LocalDate d3 = LocalDate.of(2026, 6, 3);
        LocalDate d2 = LocalDate.of(2026, 6, 2);
        when(repo.findDistinctTradeDates()).thenReturn(List.of(
            LocalDate.of(2026, 6, 5), d4, d3, d2
        ));
        when(repo.findByTradeDateOrderByTimeAsc(d4)).thenReturn(List.of(
            tickFor(d4, "090000", 40000, 100)
        ));

        List<VpHistoryEntry> result = service.getHistory(LocalDate.of(2026, 6, 5), 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo("20260604");
    }

    @Test
    void getHistory_skipsEmptyTickDates() {
        LocalDate d4 = LocalDate.of(2026, 6, 4);
        LocalDate d3 = LocalDate.of(2026, 6, 3);
        when(repo.findDistinctTradeDates()).thenReturn(List.of(
            LocalDate.of(2026, 6, 5), d4, d3
        ));
        when(repo.findByTradeDateOrderByTimeAsc(d4)).thenReturn(List.of());  // empty
        when(repo.findByTradeDateOrderByTimeAsc(d3)).thenReturn(List.of(
            tickFor(d3, "090000", 40000, 100)
        ));

        List<VpHistoryEntry> result = service.getHistory(LocalDate.of(2026, 6, 5), 2);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo("20260603");
    }

    @Test
    void getHistory_computesVpocVahVal() {
        LocalDate d4 = LocalDate.of(2026, 6, 4);
        when(repo.findDistinctTradeDates()).thenReturn(List.of(
            LocalDate.of(2026, 6, 5), d4
        ));
        // VPOC=40200(vol=300), total=500, target=350
        // Start:300. up(40300,vol=100) wins → vah=40300, acc=400≥350
        // val=40200(vpoc itself, lower never expands) → val=40200
        when(repo.findByTradeDateOrderByTimeAsc(d4)).thenReturn(List.of(
            tickFor(d4, "090000", 40100, 100),
            tickFor(d4, "090001", 40200, 300),
            tickFor(d4, "090002", 40300, 100)
        ));

        List<VpHistoryEntry> result = service.getHistory(LocalDate.of(2026, 6, 5), 1);

        assertThat(result.get(0).vpoc()).isEqualTo(40200);
        assertThat(result.get(0).vah()).isEqualTo(40300);
        assertThat(result.get(0).val()).isEqualTo(40200);
    }
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
/opt/homebrew/bin/mvn -f eagleeye-web/pom.xml test 2>&1 | tail -5
```

Expected: compile error — `cannot find symbol: method getHistory`

- [ ] **Step 3: Implement getHistory()**

Add to `VolumeProfileService.java`, after `getPlan()` and before `// ── Private calculations`:

```java
    public List<VpHistoryEntry> getHistory(LocalDate current, int days) {
        return repo.findDistinctTradeDates().stream()
                .filter(d -> d.isBefore(current))
                .sorted(Comparator.reverseOrder())
                .limit(days)
                .map(d -> {
                    List<TxTick> ticks = loadTicks(d);
                    if (ticks.isEmpty()) return null;
                    NavigableMap<Integer, Integer> profile = buildProfile(ticks, 1);
                    int vpoc = calcVpoc(profile);
                    ValueArea va = calcValueArea(profile, vpoc);
                    return new VpHistoryEntry(d.format(DATE_FMT), vpoc, va.vah(), va.val());
                })
                .filter(Objects::nonNull)
                .toList();
    }
```

`Objects` is covered by the existing `import java.util.*;`. No new import needed.

- [ ] **Step 4: Run all tests — expect pass**

```bash
/opt/homebrew/bin/mvn -f eagleeye-web/pom.xml test 2>&1 | tail -8
```

Expected:
```
Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/VolumeProfileService.java \
        eagleeye-web/src/test/java/com/eagleeye/web/VolumeProfileServiceTest.java
git commit -m "feat(vp): add getHistory() returning previous N days VPOC/VAH/VAL with tests"
```

---

## Task 3: GET /api/vp/history endpoint

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/VpController.java`

- [ ] **Step 1: Add endpoint**

Add inside `VpController.java`, after the `candles()` method and before `plan()`:

```java
    @GetMapping("/history")
    public List<VpHistoryEntry> history(
            @RequestParam String date,
            @RequestParam(defaultValue = "5") int days) {
        return service.getHistory(parse(date), days);
    }
```

`VpHistoryEntry` is already in scope via `import com.eagleeye.web.vp.*;`.

- [ ] **Step 2: Compile + full test suite**

```bash
/opt/homebrew/bin/mvn -f eagleeye-web/pom.xml test 2>&1 | tail -8
```

Expected: `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/VpController.java
git commit -m "feat(vp): add GET /api/vp/history endpoint"
```

---

## Task 4: vpoc.html — days toggle + history lines

**Files:**
- Modify: `eagleeye-web/src/main/resources/templates/vpoc.html`

- [ ] **Step 1: Add CSS for divider and days buttons**

Inside `<style>`, add after `.interval-btn.active { ... }`:

```css
.nav-divider { color: var(--border); margin: 0 4px; }
```

(`.interval-btn` and `.interval-btn.active` CSS is already shared — the new days buttons reuse the same classes.)

- [ ] **Step 2: Add days-toggle HTML to header**

Find the line:
```html
    <button class="interval-btn" id="btn-markers" onclick="toggleMarkers()">▲▼ 箭頭</button>
```

Replace with:

```html
    <button class="interval-btn" id="btn-markers" onclick="toggleMarkers()">▲▼ 箭頭</button>
    <span class="nav-divider">|</span>
    <div class="interval-btns">
      <button class="interval-btn"        id="btn-days-3"  onclick="setDays(3)">3天</button>
      <button class="interval-btn active" id="btn-days-5"  onclick="setDays(5)">5天</button>
      <button class="interval-btn"        id="btn-days-10" onclick="setDays(10)">10天</button>
    </div>
```

- [ ] **Step 3: Add currentDays to state**

Find:
```javascript
let currentDate = null, currentInterval = 5, showMarkers = false;
```

Replace with:
```javascript
let currentDate = null, currentInterval = 5, showMarkers = false, currentDays = 5;
```

- [ ] **Step 4: Add setDays(), loadHistory(), renderHistoryLines()**

Add these three functions after `toggleMarkers()`:

```javascript
function setDays(n) {
  currentDays = n;
  [3, 5, 10].forEach(d =>
    document.getElementById(`btn-days-${d}`)
      .classList.toggle('active', d === n));
  if (currentDate) loadHistory();
}

async function loadHistory() {
  const data = await fetch(
    `/api/vp/history?date=${currentDate}&days=${currentDays}`
  ).then(r => r.json());
  renderHistoryLines(data);
}

function renderHistoryLines(entries) {
  if (candleSeries._histLines) {
    candleSeries._histLines.forEach(l => candleSeries.removePriceLine(l));
  }
  candleSeries._histLines = [];

  entries.forEach((e, i) => {
    const op    = Math.max(0.18, 0.65 - i * 0.10).toFixed(2);
    const label = `${e.date.slice(4, 6)}/${e.date.slice(6, 8)}`;
    const line  = (price, r, g, b, title, axis) =>
      candleSeries._histLines.push(candleSeries.createPriceLine({
        price, lineWidth: 1,
        lineStyle: LightweightCharts.LineStyle.Dashed,
        color: `rgba(${r},${g},${b},${op})`,
        axisLabelVisible: axis,
        title,
      }));

    line(e.vpoc, 217, 119,  6, `VPOC ${label}`, true);
    line(e.vah,  239,  68, 68, '',              false);
    line(e.val,   34, 197, 94, '',              false);
  });
}
```

- [ ] **Step 5: Call loadHistory() at end of loadAll()**

Find the last block of `loadAll()`:
```javascript
  addTradeMarkers(trades);
  addBadges(plan);
  renderPlan(plan);
```

Replace with:
```javascript
  addTradeMarkers(trades);
  addBadges(plan);
  renderPlan(plan);
  loadHistory();
```

- [ ] **Step 6: Run full test suite**

```bash
/opt/homebrew/bin/mvn -f eagleeye-web/pom.xml test 2>&1 | tail -8
```

Expected: `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS`

- [ ] **Step 7: Start server and verify in browser**

```bash
/opt/homebrew/bin/mvn -f eagleeye-web/pom.xml spring-boot:run \
  -Dspring-boot.run.profiles=prod -q
```

Open `http://localhost:8080/vpoc`. Verify:
- Header shows `3天 / 5天 / 10天` buttons, 5天 active by default
- K-line chart shows fading dashed lines for previous 5 trading days
- Most recent day's VPOC line is brightest, right-axis label shows `VPOC MM/DD`
- VAH (red) and VAL (green) dashed lines appear for each day without labels
- Clicking `3天` reduces lines, `10天` adds more (all older ones are dimmer)
- Switching date → history lines update
- Switching 1m/5m/15m → history lines stay (no extra fetch)

- [ ] **Step 8: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/vpoc.html
git commit -m "feat(vpoc): add multi-day VPOC/VAH/VAL overlay with fading opacity and day toggle"
```

- [ ] **Step 9: Push**

```bash
git push
```

---

## Self-Review

**Spec coverage:**
- ✅ `VpHistoryEntry(date, vpoc, vah, val)` → Task 1
- ✅ `getHistory()` filter/sort/limit/skip-empty → Task 2
- ✅ `GET /api/vp/history?date=&days=` → Task 3
- ✅ 3/5/10 day toggle, default 5 → Task 4 Steps 2–3
- ✅ `renderHistoryLines()` with fading opacity formula → Task 4 Step 4
- ✅ VPOC label `MM/DD`, VAH/VAL no label → Task 4 Step 4
- ✅ `loadHistory()` called on date change but NOT on interval change → Task 4 Steps 5, 7
- ✅ 5 unit tests covering all spec scenarios → Task 2

**Placeholder scan:** None found.

**Type consistency:**
- `candleSeries._histLines` — defined and cleared in `renderHistoryLines()` only ✓
- `setDays(n)` / `btn-days-3` / `btn-days-5` / `btn-days-10` — consistent across HTML and JS ✓
- `VpHistoryEntry` used in service, controller, test — all aligned ✓
- `loadHistory()` called from `loadAll()` and `setDays()` only — no duplicate calls on interval switch ✓
