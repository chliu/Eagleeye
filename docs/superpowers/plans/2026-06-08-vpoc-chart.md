# VPOC Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `/vpoc` page with lightweight-charts K-line, canvas overlays for large-trade bubbles + direction triangles, VPOC/VAH/VAL price lines, VA zone shading, and entry trigger badges from the trading plan.

**Architecture:** New page `vpoc.html` served by `VpocPageController`. Backend adds `GET /api/vp/candles` (OHLCV aggregation in `VolumeProfileService`) and a `direction` field to `LargeTrade`. All other existing APIs (`/summary`, `/profile`, `/large-trades`, `/plan`) are reused without modification. Canvas overlay sits absolutely over the lightweight-charts container; `priceToCoordinate` / `timeToCoordinate` map prices and times to pixel positions for bubbles and VA zone fill.

**Tech Stack:** Java 25, Spring Boot 4, JUnit 5 + AssertJ + Mockito, lightweight-charts v4.2 (CDN), HTML5 Canvas.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `eagleeye-web/src/main/java/com/eagleeye/web/vp/TradeDirection.java` | `UP / DOWN / NEUTRAL` enum |
| Create | `eagleeye-web/src/main/java/com/eagleeye/web/vp/VpCandle.java` | OHLCV record with UTC epoch `time` |
| Modify | `eagleeye-web/src/main/java/com/eagleeye/web/vp/LargeTrade.java` | Add `direction` component |
| Modify | `eagleeye-web/src/main/java/com/eagleeye/web/VolumeProfileService.java` | Add `getCandles()`, `calcDirections()`, rework `getLargeTrades()` |
| Modify | `eagleeye-web/src/main/java/com/eagleeye/web/VpController.java` | Add `GET /candles` endpoint |
| Create | `eagleeye-web/src/main/java/com/eagleeye/web/VpocPageController.java` | Route `/vpoc` → `vpoc` view |
| Create | `eagleeye-web/src/main/resources/templates/vpoc.html` | Full VPOC chart page |
| Modify | `eagleeye-web/src/test/java/com/eagleeye/web/VolumeProfileServiceTest.java` | Add `getCandles` + `calcDirections` + direction tests |

---

## Task 1: TradeDirection enum + VpCandle DTO

**Files:**
- Create: `eagleeye-web/src/main/java/com/eagleeye/web/vp/TradeDirection.java`
- Create: `eagleeye-web/src/main/java/com/eagleeye/web/vp/VpCandle.java`

- [ ] **Step 1: Create TradeDirection**

```java
// eagleeye-web/src/main/java/com/eagleeye/web/vp/TradeDirection.java
package com.eagleeye.web.vp;

public enum TradeDirection { UP, DOWN, NEUTRAL }
```

- [ ] **Step 2: Create VpCandle**

```java
// eagleeye-web/src/main/java/com/eagleeye/web/vp/VpCandle.java
package com.eagleeye.web.vp;

public record VpCandle(long time, int open, int high, int low, int close, int volume) {}
```

`time` is a UTC Unix epoch second — the format lightweight-charts v4 expects for intraday data.

- [ ] **Step 3: Compile check**

```bash
mvn -pl eagleeye-web compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/vp/TradeDirection.java \
        eagleeye-web/src/main/java/com/eagleeye/web/vp/VpCandle.java
git commit -m "feat(vp): add TradeDirection enum and VpCandle DTO"
```

---

## Task 2: getCandles() in VolumeProfileService (TDD)

**Files:**
- Modify: `eagleeye-web/src/test/java/com/eagleeye/web/VolumeProfileServiceTest.java`
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/VolumeProfileService.java`

- [ ] **Step 1: Write failing tests**

Add these tests at the end of `VolumeProfileServiceTest`, inside the class (before the closing `}`):

```java
// ── getCandles tests ──────────────────────────────────────────────────────

@Test
void getCandles_aggregatesTicksIntoOhlcv() {
    when(repo.findByTradeDateOrderByTimeAsc(any()))
        .thenReturn(List.of(
            tick("090000", 40100, 100),
            tick("090001", 40200, 200),
            tick("090130", 40050,  50)
        ));

    List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 5);

    assertThat(candles).hasSize(1);
    VpCandle c = candles.get(0);
    assertThat(c.open()).isEqualTo(40100);
    assertThat(c.high()).isEqualTo(40200);
    assertThat(c.low()).isEqualTo(40050);
    assertThat(c.close()).isEqualTo(40050);  // last tick in bucket
    assertThat(c.volume()).isEqualTo(350);
}

@Test
void getCandles_splitsAtIntervalBoundary() {
    when(repo.findByTradeDateOrderByTimeAsc(any()))
        .thenReturn(List.of(
            tick("090000", 40100, 100),  // 09:00 bucket
            tick("090500", 40200, 200),  // 09:05 bucket
            tick("090600", 40300,  50)   // 09:05 bucket
        ));

    List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 5);

    assertThat(candles).hasSize(2);
    assertThat(candles.get(0).open()).isEqualTo(40100);
    assertThat(candles.get(0).close()).isEqualTo(40100);
    assertThat(candles.get(1).open()).isEqualTo(40200);
    assertThat(candles.get(1).close()).isEqualTo(40300);
}

@Test
void getCandles_excludesAuctionTicks() {
    when(repo.findByTradeDateOrderByTimeAsc(any()))
        .thenReturn(List.of(
            auction("084500", 40000, 500),
            tick("090000", 40100, 100)
        ));

    List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 5);

    assertThat(candles).hasSize(1);
    assertThat(candles.get(0).open()).isEqualTo(40100);
}

@Test
void getCandles_timeIsUtcEpochSeconds() {
    when(repo.findByTradeDateOrderByTimeAsc(any()))
        .thenReturn(List.of(tick("090000", 40100, 100)));

    List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 5);

    // 2026-06-05 09:00 CST = 2026-06-05 01:00 UTC
    long epochDay = LocalDate.of(2026, 6, 5).toEpochDay();
    long expected = epochDay * 86400L + 9 * 3600L - 8 * 3600L;
    assertThat(candles.get(0).time()).isEqualTo(expected);
}

@Test
void getCandles_interval1MinBucketsEachMinute() {
    when(repo.findByTradeDateOrderByTimeAsc(any()))
        .thenReturn(List.of(
            tick("090000", 40100, 10),
            tick("090045", 40150, 20),  // same 1-min bucket
            tick("090100", 40200, 30)   // next 1-min bucket
        ));

    List<VpCandle> candles = service.getCandles(LocalDate.of(2026, 6, 5), 1);

    assertThat(candles).hasSize(2);
    assertThat(candles.get(0).volume()).isEqualTo(30);
    assertThat(candles.get(1).volume()).isEqualTo(30);
}
```

Also add `VpCandle` to the existing import: change `import com.eagleeye.web.vp.*;` (already there).

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
mvn -pl eagleeye-web test -Dtest=VolumeProfileServiceTest -q 2>&1 | tail -5
```

Expected: compile error — `cannot find symbol: method getCandles`

- [ ] **Step 3: Implement getCandles() and toEpochSec()**

Add to `VolumeProfileService.java`, after the `getProfile()` method:

```java
public List<VpCandle> getCandles(LocalDate date, int intervalMinutes) {
    List<TxTick> ticks = loadTicks(date);
    TreeMap<Long, VpCandle> map = new TreeMap<>();
    for (TxTick t : ticks) {
        long key = toEpochSec(date, t.getTime(), intervalMinutes);
        VpCandle prev = map.get(key);
        if (prev == null) {
            map.put(key, new VpCandle(key,
                t.getPrice(), t.getPrice(), t.getPrice(), t.getPrice(), t.getVolume()));
        } else {
            map.put(key, new VpCandle(key,
                prev.open(),
                Math.max(prev.high(), t.getPrice()),
                Math.min(prev.low(),  t.getPrice()),
                t.getPrice(),
                prev.volume() + t.getVolume()));
        }
    }
    return new ArrayList<>(map.values());
}

private long toEpochSec(LocalDate date, String hhmmss, int intervalMinutes) {
    int h = Integer.parseInt(hhmmss.substring(0, 2));
    int m = Integer.parseInt(hhmmss.substring(2, 4));
    int s = Integer.parseInt(hhmmss.substring(4, 6));
    int totalSec = h * 3600 + m * 60 + s;
    int bucketSec = (totalSec / (intervalMinutes * 60)) * (intervalMinutes * 60);
    return date.toEpochDay() * 86400L + bucketSec - 8 * 3600L;
}
```

`VpCandle` is already in scope via the `import com.eagleeye.web.vp.*;` at the top of the file.

- [ ] **Step 4: Run tests — expect all pass**

```bash
mvn -pl eagleeye-web test -Dtest=VolumeProfileServiceTest -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/VolumeProfileService.java \
        eagleeye-web/src/test/java/com/eagleeye/web/VolumeProfileServiceTest.java
git commit -m "feat(vp): add getCandles() OHLCV aggregation with tests"
```

---

## Task 3: calcDirections() + LargeTrade.direction (TDD)

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/vp/LargeTrade.java`
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/VolumeProfileService.java`
- Modify: `eagleeye-web/src/test/java/com/eagleeye/web/VolumeProfileServiceTest.java`

- [ ] **Step 1: Add direction to LargeTrade record**

Replace the entire content of `LargeTrade.java`:

```java
package com.eagleeye.web.vp;

public record LargeTrade(
        String time,
        int price,
        int volume,
        String session,
        int priceVsVpoc,
        TradeZone zone,
        TradeDirection direction
) {}
```

- [ ] **Step 2: Write failing tests**

Add these tests to `VolumeProfileServiceTest`:

```java
// ── calcDirections tests ──────────────────────────────────────────────────

@Test
void calcDirections_firstTickIsNeutral() {
    List<TradeDirection> dirs = service.calcDirections(
        List.of(tick("090000", 40100, 100)));
    assertThat(dirs).containsExactly(TradeDirection.NEUTRAL);
}

@Test
void calcDirections_detectsUpDownNeutral() {
    List<TxTick> ticks = List.of(
        tick("090000", 40100, 100),
        tick("090001", 40200, 100),   // UP
        tick("090002", 40100, 100),   // DOWN
        tick("090003", 40100, 100)    // NEUTRAL
    );
    assertThat(service.calcDirections(ticks)).containsExactly(
        TradeDirection.NEUTRAL,
        TradeDirection.UP,
        TradeDirection.DOWN,
        TradeDirection.NEUTRAL
    );
}

// ── getLargeTrades direction tests ────────────────────────────────────────

@Test
void getLargeTrades_includesDirection() {
    when(repo.findByTradeDateOrderByTimeAsc(any()))
        .thenReturn(List.of(
            tick("090000", 40000, 100),   // NEUTRAL (first tick)
            tick("090001", 40100, 200),   // UP
            tick("090002", 39900, 150)    // DOWN
        ));

    List<LargeTrade> trades = service.getLargeTrades(LocalDate.of(2026, 6, 5), 50);

    // sorted desc by volume: 200, 150, 100
    assertThat(trades.get(0).direction()).isEqualTo(TradeDirection.UP);
    assertThat(trades.get(1).direction()).isEqualTo(TradeDirection.DOWN);
    assertThat(trades.get(2).direction()).isEqualTo(TradeDirection.NEUTRAL);
}
```

- [ ] **Step 3: Run tests — expect failure**

```bash
mvn -pl eagleeye-web test -Dtest=VolumeProfileServiceTest -q 2>&1 | tail -5
```

Expected: compile errors — `cannot find symbol: method calcDirections`, constructor mismatch on `LargeTrade`.

- [ ] **Step 4: Add calcDirections() and rework getLargeTrades()**

Replace the existing `getLargeTrades()` method in `VolumeProfileService.java`:

```java
public List<LargeTrade> getLargeTrades(LocalDate date, int threshold) {
    List<TxTick> ticks = loadTicks(date);
    NavigableMap<Integer, Integer> profile = buildProfile(ticks, 1);
    int vpoc = calcVpoc(profile);
    ValueArea va = calcValueArea(profile, vpoc);
    List<TradeDirection> directions = calcDirections(ticks);

    List<LargeTrade> result = new ArrayList<>();
    for (int i = 0; i < ticks.size(); i++) {
        TxTick t = ticks.get(i);
        if (t.getVolume() < threshold) continue;
        result.add(new LargeTrade(
            formatTime(t.getTime()),
            t.getPrice(),
            t.getVolume(),
            classifySession(t.getTime()),
            t.getPrice() - vpoc,
            classifyZone(t.getPrice(), vpoc, va.vah(), va.val()),
            directions.get(i)
        ));
    }
    result.sort(Comparator.comparingInt(LargeTrade::volume).reversed());
    return result;
}

List<TradeDirection> calcDirections(List<TxTick> ticks) {
    List<TradeDirection> dirs = new ArrayList<>(ticks.size());
    if (!ticks.isEmpty()) dirs.add(TradeDirection.NEUTRAL);
    for (int i = 1; i < ticks.size(); i++) {
        int curr = ticks.get(i).getPrice();
        int prev = ticks.get(i - 1).getPrice();
        dirs.add(curr > prev ? TradeDirection.UP
               : curr < prev ? TradeDirection.DOWN
               : TradeDirection.NEUTRAL);
    }
    return dirs;
}
```

- [ ] **Step 5: Run all tests**

```bash
mvn -pl eagleeye-web test -Dtest=VolumeProfileServiceTest -q
```

Expected: `BUILD SUCCESS` — all existing + new tests pass.

- [ ] **Step 6: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/vp/LargeTrade.java \
        eagleeye-web/src/main/java/com/eagleeye/web/VolumeProfileService.java \
        eagleeye-web/src/test/java/com/eagleeye/web/VolumeProfileServiceTest.java
git commit -m "feat(vp): add trade direction (tick rule) to LargeTrade with tests"
```

---

## Task 4: /api/vp/candles endpoint + VpocPageController

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/VpController.java`
- Create: `eagleeye-web/src/main/java/com/eagleeye/web/VpocPageController.java`

- [ ] **Step 1: Add candles endpoint to VpController**

Add this method inside `VpController`, after the existing `largeTrades()` method:

```java
@GetMapping("/candles")
public List<VpCandle> candles(
        @RequestParam String date,
        @RequestParam(defaultValue = "TX") String product,
        @RequestParam(defaultValue = "5") int interval) {
    return service.getCandles(parse(date), interval);
}
```

The `VpCandle` type is already accessible via `import com.eagleeye.web.vp.*;`.

- [ ] **Step 2: Create VpocPageController**

```java
// eagleeye-web/src/main/java/com/eagleeye/web/VpocPageController.java
package com.eagleeye.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VpocPageController {

    @GetMapping("/vpoc")
    public String vpoc() {
        return "vpoc";
    }
}
```

- [ ] **Step 3: Compile check**

```bash
mvn -pl eagleeye-web compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/VpController.java \
        eagleeye-web/src/main/java/com/eagleeye/web/VpocPageController.java
git commit -m "feat(vp): add GET /api/vp/candles endpoint and /vpoc page route"
```

---

## Task 5: vpoc.html — skeleton + K-line chart

**Files:**
- Create: `eagleeye-web/src/main/resources/templates/vpoc.html`

- [ ] **Step 1: Create vpoc.html with skeleton, CSS, K-line only**

```html
<!DOCTYPE html>
<html lang="zh-TW" xmlns:th="http://www.thymeleaf.org">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>EagleEye — VPOC Chart</title>
<script src="https://cdn.jsdelivr.net/npm/lightweight-charts@4.2.0/dist/lightweight-charts.standalone.production.js"></script>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
:root {
  --bg: #1a1a1a; --surface: #222; --border: #333; --text: #e0e0e0;
  --muted: #888; --amber: #d97706; --amber-dim: #92510440;
  --red: #ef4444; --green: #22c55e; --blue: #378add; --grid: #2a2a2a;
  --mono: 'JetBrains Mono','Fira Code','SF Mono',monospace;
}
body { background: var(--bg); color: var(--text); font-family: var(--mono); font-size: 12px; }

.header { background: var(--surface); border-bottom: 1px solid var(--border); }
.header-inner {
  max-width: 1600px; margin: 0 auto; padding: 10px 16px;
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
}
.header-title { font-size: 13px; font-weight: 700; color: var(--amber); white-space: nowrap; }
.header-title span { color: var(--muted); font-weight: 400; }
.date-select {
  background: var(--bg); border: 1px solid var(--border); color: var(--text);
  font-family: var(--mono); font-size: 11px; padding: 4px 8px; border-radius: 3px; cursor: pointer;
}
.interval-btns { display: flex; gap: 4px; }
.interval-btn {
  background: var(--bg); border: 1px solid var(--border); color: var(--muted);
  padding: 2px 10px; border-radius: 3px; cursor: pointer; font-size: 10px; font-family: var(--mono);
}
.interval-btn.active { border-color: var(--amber); color: var(--amber); background: var(--amber-dim); }
.nav-link { color: var(--muted); font-size: 11px; text-decoration: none; margin-left: auto; }
.nav-link:hover { color: var(--amber); }

.metrics {
  max-width: 1600px; margin: 10px auto; padding: 0 16px;
  display: flex; gap: 8px; flex-wrap: wrap;
}
.metric-card {
  background: var(--surface); border: 1px solid var(--border); border-radius: 4px;
  padding: 8px 12px; min-width: 120px;
}
.metric-label { font-size: 10px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.6px; }
.metric-value { font-size: 18px; font-weight: 700; margin-top: 2px; font-variant-numeric: tabular-nums; }
.metric-sub   { font-size: 10px; color: var(--muted); }
.vpoc-card .metric-value { color: var(--amber); }
.vah-card  .metric-value { color: var(--red); }
.val-card  .metric-value { color: var(--green); }

.main-grid {
  max-width: 1600px; margin: 0 auto; padding: 0 16px 16px;
  display: grid; grid-template-columns: 1fr 200px; gap: 8px;
}
.chart-wrap { position: relative; height: 600px; background: var(--surface); border: 1px solid var(--border); border-radius: 4px; overflow: hidden; }
.vp-panel   { position: relative; background: var(--surface); border: 1px solid var(--border); border-radius: 4px; overflow: hidden; }
</style>
</head>
<body>

<div class="header">
  <div class="header-inner">
    <div class="header-title">EagleEye <span>/ VPOC Chart</span></div>
    <select id="dateSelect" class="date-select"><option>載入中...</option></select>
    <div class="interval-btns">
      <button class="interval-btn" id="btn-1"  onclick="setIntervalMin(1)">1m</button>
      <button class="interval-btn active" id="btn-5"  onclick="setIntervalMin(5)">5m</button>
      <button class="interval-btn" id="btn-15" onclick="setIntervalMin(15)">15m</button>
    </div>
    <a href="/dashboard" class="nav-link">← Dashboard</a>
    <a href="/vp" class="nav-link">VP →</a>
  </div>
</div>

<div class="metrics">
  <div class="metric-card vpoc-card">
    <div class="metric-label">VPOC</div>
    <div class="metric-value" id="m-vpoc">—</div>
    <div class="metric-sub"  id="m-vpoc-vol">—</div>
  </div>
  <div class="metric-card vah-card">
    <div class="metric-label">VAH</div>
    <div class="metric-value" id="m-vah">—</div>
    <div class="metric-sub">價值區上緣</div>
  </div>
  <div class="metric-card val-card">
    <div class="metric-label">VAL</div>
    <div class="metric-value" id="m-val">—</div>
    <div class="metric-sub">價值區下緣</div>
  </div>
  <div class="metric-card">
    <div class="metric-label">Close vs VPOC</div>
    <div class="metric-value" id="m-cvpoc">—</div>
    <div class="metric-sub"  id="m-close">—</div>
  </div>
  <div class="metric-card">
    <div class="metric-label">總量</div>
    <div class="metric-value" id="m-vol">—</div>
    <div class="metric-sub"  id="m-ohlc">—</div>
  </div>
</div>

<div class="main-grid">
  <div id="chartWrap" class="chart-wrap"></div>
  <div id="vpPanel"   class="vp-panel"></div>
</div>

<script>
// ── State ──────────────────────────────────────────────────────────────────
let currentDate = null, currentInterval = 5;
let chart = null, candleSeries = null;
let overlayCanvas = null, overlayCtx = null;
let summaryData = null, candleData = [], largeTrades = [], profileData = [], planData = null;

// ── Chart init ─────────────────────────────────────────────────────────────
function initChart() {
  const wrap = document.getElementById('chartWrap');
  chart = LightweightCharts.createChart(wrap, {
    width:  wrap.clientWidth,
    height: wrap.clientHeight,
    layout: { background: { color: '#222' }, textColor: '#888' },
    grid:   { vertLines: { color: '#2a2a2a' }, horzLines: { color: '#2a2a2a' } },
    crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
    rightPriceScale: { borderColor: '#333' },
    timeScale: {
      borderColor: '#333', timeVisible: true, secondsVisible: false,
      tickMarkFormatter: (t) => {
        const d = new Date((t + 8 * 3600) * 1000);
        return d.toISOString().slice(11, 16);
      }
    },
    localization: {
      timeFormatter: (t) => {
        const d = new Date((t + 8 * 3600) * 1000);
        return d.toISOString().slice(11, 16);
      }
    }
  });

  candleSeries = chart.addCandlestickSeries({
    upColor: '#22c55e', downColor: '#ef4444',
    borderVisible: false,
    wickUpColor: '#22c55e', wickDownColor: '#ef4444',
  });

  // Canvas overlay for VA zone + bubbles
  overlayCanvas = document.createElement('canvas');
  overlayCanvas.style.cssText = 'position:absolute;top:0;left:0;pointer-events:none;';
  wrap.appendChild(overlayCanvas);

  chart.timeScale().subscribeVisibleLogicalRangeChange(redrawOverlay);

  window.addEventListener('resize', () => {
    chart.resize(wrap.clientWidth, wrap.clientHeight);
    redrawOverlay();
  });
}

// ── Metrics ────────────────────────────────────────────────────────────────
function renderMetrics(s) {
  document.getElementById('m-vpoc').textContent     = s.vpoc.toLocaleString();
  document.getElementById('m-vpoc-vol').textContent = s.vpocVolume.toLocaleString() + ' 口';
  document.getElementById('m-vah').textContent      = s.vah.toLocaleString();
  document.getElementById('m-val').textContent      = s.val.toLocaleString();
  const sign = s.closeVsVpoc >= 0 ? '+' : '';
  document.getElementById('m-cvpoc').textContent  = sign + s.closeVsVpoc.toLocaleString();
  document.getElementById('m-close').textContent  = '收盤 ' + s.close.toLocaleString();
  document.getElementById('m-vol').textContent    = s.totalVolume.toLocaleString();
  document.getElementById('m-ohlc').textContent   =
    `O ${s.open.toLocaleString()} H ${s.high.toLocaleString()} L ${s.low.toLocaleString()}`;
}

// ── Overlay (placeholder — filled in Task 6 + 7) ──────────────────────────
function redrawOverlay() {
  const wrap = document.getElementById('chartWrap');
  overlayCanvas.width  = wrap.clientWidth;
  overlayCanvas.height = wrap.clientHeight;
  // VA zone + bubbles drawn in later tasks
}

// ── Load candles ───────────────────────────────────────────────────────────
async function loadCandles() {
  const data = await fetch(
    `/api/vp/candles?date=${currentDate}&product=TX&interval=${currentInterval}`
  ).then(r => r.json());
  candleData = data;
  candleSeries.setData(data.map(c => ({
    time: c.time, open: c.open, high: c.high, low: c.low, close: c.close
  })));
  chart.timeScale().fitContent();
  redrawOverlay();
}

// ── Load all data for a date ───────────────────────────────────────────────
async function loadAll(date) {
  currentDate = date;
  const [summary, trades, profile, plan] = await Promise.all([
    fetch(`/api/vp/summary?date=${date}&product=TX`).then(r => r.json()),
    fetch(`/api/vp/large-trades?date=${date}&product=TX&threshold=50`).then(r => r.json()),
    fetch(`/api/vp/profile?date=${date}&product=TX&step=50`).then(r => r.json()),
    fetch(`/api/vp/plan?date=${date}&product=TX`).then(r => r.json()),
  ]);
  summaryData = summary;
  largeTrades = trades;
  profileData = profile;
  planData    = plan;

  renderMetrics(summary);
  addPriceLines(summary);    // Task 6
  renderVpBars(profile);     // Task 9
  await loadCandles();
  addTradeMarkers(trades);   // Task 7
  addBadges(plan);           // Task 8
}

// ── Stubs (implemented in later tasks) ────────────────────────────────────
function addPriceLines(s) {}
function addTradeMarkers(trades) {}
function addBadges(plan) {}
function renderVpBars(profile) {}

// ── Controls ───────────────────────────────────────────────────────────────
function setIntervalMin(n) {
  currentInterval = n;
  ['1','5','15'].forEach(id =>
    document.getElementById('btn-' + id).classList.toggle('active', String(n) === id));
  if (currentDate) {
    loadCandles();
    if (candleSeries) candleSeries.setMarkers([]);  // clear old markers
    if (currentDate) addTradeMarkers(largeTrades);
  }
}

// ── Init ───────────────────────────────────────────────────────────────────
async function init() {
  initChart();
  const dates = await fetch('/api/vp/available-dates?product=TX').then(r => r.json());
  const sel = document.getElementById('dateSelect');
  sel.innerHTML = dates.map(d =>
    `<option value="${d}">${d.slice(0,4)}/${d.slice(4,6)}/${d.slice(6,8)}</option>`
  ).join('');
  sel.onchange = () => loadAll(sel.value);
  if (dates.length > 0) loadAll(dates[0]);
}

init();
</script>
</body>
</html>
```

- [ ] **Step 2: Start server and verify K-line renders**

```bash
mvn -pl eagleeye-web spring-boot:run -Dspring-boot.run.profiles=prod -q
```

Open `http://localhost:8080/vpoc`. Verify:
- Date selector populates
- K-line chart appears with candles
- Metric cards show VPOC / VAH / VAL / Close
- 1m / 5m / 15m buttons switch candle resolution

- [ ] **Step 3: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/vpoc.html
git commit -m "feat(vpoc): add vpoc.html with K-line chart skeleton"
```

---

## Task 6: VPOC / VAH / VAL price lines + VA zone canvas

**Files:**
- Modify: `eagleeye-web/src/main/resources/templates/vpoc.html`

- [ ] **Step 1: Replace addPriceLines() stub and expand redrawOverlay()**

Find the line `function addPriceLines(s) {}` and replace it:

```javascript
function addPriceLines(s) {
  // Remove old lines before re-adding (needed on date change)
  if (candleSeries._priceLines) {
    candleSeries._priceLines.forEach(l => candleSeries.removePriceLine(l));
  }
  candleSeries._priceLines = [
    candleSeries.createPriceLine({
      price: s.vpoc, color: '#d97706', lineWidth: 2,
      lineStyle: LightweightCharts.LineStyle.Solid,
      axisLabelVisible: true, title: 'VPOC'
    }),
    candleSeries.createPriceLine({
      price: s.vah, color: '#ef4444', lineWidth: 1,
      lineStyle: LightweightCharts.LineStyle.Dashed,
      axisLabelVisible: true, title: 'VAH'
    }),
    candleSeries.createPriceLine({
      price: s.val, color: '#22c55e', lineWidth: 1,
      lineStyle: LightweightCharts.LineStyle.Dashed,
      axisLabelVisible: true, title: 'VAL'
    }),
  ];
}
```

Find the `redrawOverlay()` function and replace it:

```javascript
function redrawOverlay() {
  if (!chart || !candleSeries) return;
  const wrap = document.getElementById('chartWrap');
  overlayCanvas.width  = wrap.clientWidth;
  overlayCanvas.height = wrap.clientHeight;
  const ctx = overlayCtx || (overlayCtx = overlayCanvas.getContext('2d'));
  ctx.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);

  // VA zone fill
  if (summaryData) {
    const yVah = candleSeries.priceToCoordinate(summaryData.vah);
    const yVal = candleSeries.priceToCoordinate(summaryData.val);
    if (yVah != null && yVal != null) {
      ctx.fillStyle = 'rgba(55,138,221,0.07)';
      ctx.fillRect(0, yVah, overlayCanvas.width, yVal - yVah);
    }
  }

  // Bubbles (drawn in Task 7)
  drawBubbles(ctx);
}
```

Add the drawBubbles stub after redrawOverlay:

```javascript
function drawBubbles(ctx) {}
```

- [ ] **Step 2: Verify in browser**

Reload `http://localhost:8080/vpoc`. Verify:
- VPOC amber solid line + label visible on chart
- VAH red dashed line + label visible
- VAL green dashed line + label visible
- Subtle blue-tinted band between VAH and VAL (VA zone)
- Band moves correctly when zooming/panning the chart

- [ ] **Step 3: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/vpoc.html
git commit -m "feat(vpoc): add VPOC/VAH/VAL price lines and VA zone canvas shading"
```

---

## Task 7: Large trade markers (triangles) + bubbles

**Files:**
- Modify: `eagleeye-web/src/main/resources/templates/vpoc.html`

- [ ] **Step 1: Add helper — tradeEpochSec**

Add before `addTradeMarkers()`:

```javascript
function tradeEpochSec(dateStr, timeStr) {
  // dateStr="20260605", timeStr="09:15:30" → UTC epoch seconds
  const [h, m, s] = timeStr.split(':').map(Number);
  const y = +dateStr.slice(0, 4), mo = +dateStr.slice(4, 6) - 1, d = +dateStr.slice(6, 8);
  return Date.UTC(y, mo, d, h - 8, m, s) / 1000;
}

function bucketToCandle(epochSec, intervalMinutes) {
  const b = intervalMinutes * 60;
  return Math.floor(epochSec / b) * b;
}

function bubbleColor(zone) {
  return zone === 'ABOVE_VAH' ? '#ef4444'
       : zone === 'BELOW_VAL' ? '#22c55e'
       : zone === 'AT_VPOC'   ? '#d97706'
       : '#378add';
}
```

- [ ] **Step 2: Replace addTradeMarkers() stub**

```javascript
function addTradeMarkers(trades) {
  if (!candleSeries || !trades.length) return;

  // Triangle flags via setMarkers API
  const markers = trades.map(t => {
    const epochSec  = tradeEpochSec(currentDate, t.time);
    const candleTime = bucketToCandle(epochSec, currentInterval);
    const isDown = t.direction === 'DOWN';
    return {
      time:     candleTime,
      position: isDown ? 'aboveBar' : 'belowBar',
      color:    t.direction === 'UP'   ? '#22c55e'
              : t.direction === 'DOWN' ? '#ef4444' : '#666',
      shape:    t.direction === 'UP'   ? 'arrowUp'
              : t.direction === 'DOWN' ? 'arrowDown' : 'circle',
      size: 1,
    };
  });
  candleSeries.setMarkers(markers.sort((a, b) => a.time - b.time));

  redrawOverlay();  // trigger bubble repaint
}
```

- [ ] **Step 3: Replace drawBubbles() stub**

```javascript
function drawBubbles(ctx) {
  if (!largeTrades.length || !candleSeries) return;
  const maxVol = Math.max(...largeTrades.map(t => t.volume));

  largeTrades.forEach(t => {
    const epochSec = tradeEpochSec(currentDate, t.time);
    const x = chart.timeScale().timeToCoordinate(bucketToCandle(epochSec, currentInterval));
    const y = candleSeries.priceToCoordinate(t.price);
    if (x == null || y == null) return;

    const r = 4 + (t.volume / maxVol) * 14;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fillStyle = bubbleColor(t.zone);
    ctx.globalAlpha = 0.70;
    ctx.fill();
    ctx.globalAlpha = 1.0;
  });
}
```

- [ ] **Step 4: Verify in browser**

Reload `http://localhost:8080/vpoc`. Verify:
- ▲ / ▼ / ● markers appear on candles where large trades occurred
- Coloured circles (bubbles) appear at the correct price + time positions
- Bubble sizes vary with trade volume
- Bubble colours match zone (red above VAH, blue in VA, amber at VPOC, green below VAL)
- Bubbles and triangles redraw correctly when zooming/panning

- [ ] **Step 5: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/vpoc.html
git commit -m "feat(vpoc): add large trade bubbles and direction triangle markers"
```

---

## Task 8: Entry trigger badges from plan

**Files:**
- Modify: `eagleeye-web/src/main/resources/templates/vpoc.html`

- [ ] **Step 1: Replace addBadges() stub**

```javascript
function addBadges(plan) {
  if (!plan || !candleSeries) return;

  // Remove old badge lines
  if (candleSeries._badgeLines) {
    candleSeries._badgeLines.forEach(l => candleSeries.removePriceLine(l));
  }
  candleSeries._badgeLines = [];

  const levels = plan.levels;

  // Scenario 1 (BULLISH): VAL — 回測不破進多
  if (levels.val) {
    candleSeries._badgeLines.push(candleSeries.createPriceLine({
      price: levels.val.price,
      color: '#22c55e',
      lineWidth: 1,
      lineStyle: LightweightCharts.LineStyle.SparseDotted,
      axisLabelVisible: false,
      title: `↑ ${levels.val.price.toLocaleString()} 回測不破進多`,
    }));
  }

  // Scenario 4 (BEARISH): support1 — 跌破留意
  if (levels.support1) {
    candleSeries._badgeLines.push(candleSeries.createPriceLine({
      price: levels.support1.price,
      color: '#ef4444',
      lineWidth: 1,
      lineStyle: LightweightCharts.LineStyle.SparseDotted,
      axisLabelVisible: false,
      title: `⚠ ${levels.support1.price.toLocaleString()} 跌破留意`,
    }));
  }
}
```

- [ ] **Step 2: Verify in browser**

Reload `http://localhost:8080/vpoc`. Verify:
- A green dotted line with label `↑ XXXXX 回測不破進多` appears at VAL
- A red dotted line with label `⚠ XXXXX 跌破留意` appears at support1 (when present)
- Lines do not duplicate when switching dates

- [ ] **Step 3: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/vpoc.html
git commit -m "feat(vpoc): add entry trigger badges from trading plan"
```

---

## Task 9: Synced VP bars on right panel

**Files:**
- Modify: `eagleeye-web/src/main/resources/templates/vpoc.html`

- [ ] **Step 1: Replace renderVpBars() stub and add vp-bar CSS**

Add inside the `<style>` block, before `</style>`:

```css
.vp-bar-row {
  position: absolute; left: 0; right: 0;
  display: flex; align-items: center;
}
.vp-bar-fill { height: 100%; border-radius: 1px; }
.vp-bar-label {
  font-size: 9px; color: var(--muted); white-space: nowrap;
  padding-left: 3px; font-variant-numeric: tabular-nums;
}
```

- [ ] **Step 2: Replace renderVpBars() stub**

```javascript
function vpBarFill(entry) {
  if (entry.type === 'VPOC') return '#d97706';
  if (entry.type === 'VAH')  return '#a32d2d';
  if (entry.type === 'VAL')  return '#0f6e56';
  if (entry.type === 'THIN') return '#555';
  return entry.inValueArea ? '#2a5f99' : '#378add';
}

function renderVpBars(profile) {
  if (!profile.length) return;
  const panel = document.getElementById('vpPanel');
  const maxVol = Math.max(...profile.map(e => e.volume));

  // Called once to lay out bars; repositioned by syncVpBars on every chart render
  panel._profile = profile;
  panel._maxVol  = maxVol;
  panel.innerHTML = '';

  profile.forEach((e, i) => {
    const row = document.createElement('div');
    row.className = 'vp-bar-row';
    row.dataset.idx = i;
    row.style.height = '1px';  // height set by syncVpBars

    const pct  = Math.round(e.volume / maxVol * 100);
    const fill = document.createElement('div');
    fill.className = 'vp-bar-fill';
    fill.style.width      = pct + '%';
    fill.style.background = vpBarFill(e);
    row.appendChild(fill);
    panel.appendChild(row);
  });

  syncVpBars();
  chart.timeScale().subscribeVisibleLogicalRangeChange(syncVpBars);
}

function syncVpBars() {
  if (!candleSeries) return;
  const panel   = document.getElementById('vpPanel');
  const profile = panel._profile;
  if (!profile) return;

  const step = 50;  // profile step matches /api/vp/profile?step=50
  const rows = panel.querySelectorAll('.vp-bar-row');

  profile.forEach((e, i) => {
    const yTop = candleSeries.priceToCoordinate(e.price + step);
    const yBot = candleSeries.priceToCoordinate(e.price);
    if (yTop == null || yBot == null) { rows[i].style.display = 'none'; return; }
    const top    = Math.min(yTop, yBot);
    const height = Math.max(1, Math.abs(yBot - yTop));
    rows[i].style.display = 'flex';
    rows[i].style.top     = top + 'px';
    rows[i].style.height  = height + 'px';
  });
}
```

- [ ] **Step 3: Verify in browser**

Reload `http://localhost:8080/vpoc`. Verify:
- Volume profile bars appear in the right panel
- Bar heights track the K-line y-axis when zooming or panning (bars expand/contract with price scale)
- VPOC bar is amber, VAH is dark red, VAL is dark green, VA bars are blue, thin nodes are grey

- [ ] **Step 4: Final full-page check — all acceptance criteria**

With the server running, verify all 8 spec acceptance criteria:
1. `/vpoc` loads, K-line shows candles ✓
2. 1m / 5m / 15m buttons switch resolution ✓
3. VPOC (amber) / VAH (red) / VAL (green) lines + VA blue zone ✓
4. Bubbles at correct time + price, size ∝ volume ✓
5. Triangle direction matches tick rule ✓
6. VP bars track K-line y-axis ✓
7. Entry badges at VAL and support1 ✓
8. (Backend) `GET /api/vp/candles` returns correct OHLCV ✓ (covered by unit tests)
9. (Backend) `LargeTrade.direction` correct ✓ (covered by unit tests)

- [ ] **Step 5: Run full test suite**

```bash
mvn -pl eagleeye-web test -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/vpoc.html
git commit -m "feat(vpoc): add synced VP bars aligned to chart price axis"
```

- [ ] **Step 7: Push**

```bash
git push
```

---

## Self-Review

**Spec coverage:**
- ✅ New page `/vpoc` → `VpocPageController` (Task 4)
- ✅ `GET /api/vp/candles` → `VolumeProfileService.getCandles()` (Task 2, 4)
- ✅ `TradeDirection` enum → `LargeTrade.direction` (Task 1, 3)
- ✅ lightweight-charts K-line, 1m/5m/15m (Task 5)
- ✅ VA zone shading (Task 6)
- ✅ VPOC/VAH/VAL price lines (Task 6)
- ✅ Large trade bubbles (Task 7)
- ✅ Direction triangle flags (Task 7)
- ✅ Entry badges from plan (Task 8)
- ✅ Synced VP bars (Task 9)
- ✅ All 9 acceptance criteria addressed

**Placeholder scan:** None found.

**Type consistency:**
- `candleSeries._priceLines` / `candleSeries._badgeLines` — custom properties used consistently in Task 6 and Task 8
- `drawBubbles(ctx)` defined in Task 7, called from `redrawOverlay()` introduced in Task 6 — stub present in Task 5 ✓
- `addPriceLines(s)`, `addTradeMarkers(trades)`, `addBadges(plan)`, `renderVpBars(profile)` — all stubbed in Task 5, implemented in Tasks 6–9 ✓
- `tradeEpochSec()` and `bucketToCandle()` defined in Task 7, used in Task 7 only ✓
- `syncVpBars()` defined and called in Task 9 ✓
