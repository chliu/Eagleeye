# Multi-Day VPOC Structure Design

**Date:** 2026-06-08  
**Status:** Approved  
**Goal:** 在 vpoc.html K 線圖上疊加前 N 個交易日的 VPOC / VAH / VAL，顏色按日期遠近漸淡，讓交易人一眼看出跨日 Value Area 結構。

---

## 1. Overview

在現有 vpoc.html 的 K 線圖上新增「歷史 VA 層」：前 N 個交易日各畫三條虛線（VPOC / VAH / VAL），最近一日最鮮明，越舊越透明。N 可選 3 / 5 / 10 天，預設 5。

新增一個後端 endpoint `GET /api/vp/history`，單次請求回傳所有歷史 VA 資料，前端一次渲染。

---

## 2. Architecture

### 新增

| 檔案 | 說明 |
|------|------|
| `VpHistoryEntry.java` | `(String date, int vpoc, int vah, int val)` record |
| `VolumeProfileService.getHistory()` | 取前 N 日 VPOC/VAH/VAL |
| `GET /api/vp/history?date=&days=` | 新 endpoint |
| `vpoc.html` — days toggle + renderHistoryLines() | 前端渲染邏輯 |

### 不改動

- 現有所有 API（`/summary`, `/profile`, `/candles`, `/plan` 等）
- `VpController` 以外的任何 controller
- `vpoc.html` 現有所有功能

---

## 3. Backend

### 3.1 `VpHistoryEntry` DTO

```java
package com.eagleeye.web.vp;

public record VpHistoryEntry(String date, int vpoc, int vah, int val) {}
```

`date` 格式同其他 DTO：`YYYYMMDD`（BasicIsoDate）。

### 3.2 `VolumeProfileService.getHistory()`

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

- 回傳順序：最近日在前（index 0 = 前一日）
- 不包含 `current` 日本身
- 空 tick 日（假日、停市）跳過

### 3.3 Endpoint

```java
@GetMapping("/history")
public List<VpHistoryEntry> history(
        @RequestParam String date,
        @RequestParam(defaultValue = "5") int days) {
    return service.getHistory(parse(date), days);
}
```

### 3.4 Unit Tests

| 測試 | 驗證 |
|------|------|
| 回傳數量 ≤ days | 資料不足時不補、不拋例外 |
| 不含 current 日 | `isBefore` 嚴格過濾 |
| 按日期 desc 排序 | index 0 = 最近日 |
| 空 tick 日跳過 | 不回傳 null entry |
| VPOC/VAH/VAL 正確 | 用 mock ticks 驗算 |

---

## 4. Frontend

### 4.1 Header 新增天數切換

現有 header 的 `▲▼ 箭頭` 按鈕後加：

```html
<span class="nav-divider">|</span>
<div class="interval-btns">
  <button class="interval-btn" id="btn-days-3"  onclick="setDays(3)">3天</button>
  <button class="interval-btn active" id="btn-days-5"  onclick="setDays(5)">5天</button>
  <button class="interval-btn" id="btn-days-10" onclick="setDays(10)">10天</button>
</div>
```

### 4.2 線條樣式規格

| 線 | 顏色 (RGB) | 樣式 | 右側標籤 |
|----|------------|------|---------|
| 前日 VPOC | `rgba(217,119,6, op)` | Dashed | `VPOC MM/DD` |
| 前日 VAH | `rgba(239,68,68, op)` | Dashed | 無 |
| 前日 VAL | `rgba(34,197,94, op)` | Dashed | 無 |

Opacity 公式：`op = Math.max(0.18, 0.65 - index * 0.10)`

| index | 前幾日 | opacity |
|-------|--------|---------|
| 0 | 前 1 日 | 0.65 |
| 1 | 前 2 日 | 0.55 |
| 2 | 前 3 日 | 0.45 |
| 3 | 前 4 日 | 0.35 |
| 4 | 前 5 日 | 0.25 |
| 5–9 | 前 6–10 日 | 0.18 |

### 4.3 State 與觸發邏輯

```javascript
let currentDays = 5;

// 切換天數
function setDays(n) {
  currentDays = n;
  [3, 5, 10].forEach(d =>
    document.getElementById(`btn-days-${d}`)
      .classList.toggle('active', d === n));
  if (currentDate) loadHistory();
}

// fetch + render
async function loadHistory() {
  const data = await fetch(
    `/api/vp/history?date=${currentDate}&days=${currentDays}`
  ).then(r => r.json());
  renderHistoryLines(data);
}

// 畫線
function renderHistoryLines(entries) {
  if (candleSeries._histLines) {
    candleSeries._histLines.forEach(l => candleSeries.removePriceLine(l));
  }
  candleSeries._histLines = [];

  entries.forEach((e, i) => {
    const op    = Math.max(0.18, 0.65 - i * 0.10).toFixed(2);
    const label = `${e.date.slice(4,6)}/${e.date.slice(6,8)}`;
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

### 4.4 loadAll() 整合

`loadAll()` 最後加一行：
```javascript
loadHistory();
```

切換 interval 時**不**重新 fetch（歷史 VA 價位不變）。

---

## 5. 不在本次 Scope

- Naked VPOC 特別高亮（未被回測的前日 VPOC）
- 跨日 Volume Delta
- 即時更新
- 夜盤（AH）納入歷史 VA 計算

---

## 6. 驗收標準

1. `GET /api/vp/history?date=20260608&days=5` 回傳 5 筆（或更少），不含 20260608
2. 回傳結果按日期 desc 排序
3. 切換日期 → 歷史線重新渲染
4. 切換 3 / 5 / 10 天 → 線條數量更新
5. opacity 視覺上越舊越淡
6. VPOC 線右側顯示 `MM/DD` 日期標籤
7. 切換 1m / 5m / 15m → 歷史線不重新 fetch（僅 K 線更新）
8. 單元測試：`getHistory()` 5 個情境全過
