# VPOC Chart Design

**Date:** 2026-06-08  
**Status:** Approved  
**Goal:** 專業 VPOC K 線圖，整合大單氣泡、三角旗、VA 著色、進場觸發 badges，供交易人觀察明日進場點。

---

## 1. Overview

新增獨立頁面 `/vpoc`，使用 TradingView `lightweight-charts v4` 繪製 K 線 + 側邊 Volume Profile，大單直接疊加在圖上（氣泡 + 三角旗），VPOC / VAH / VAL 以水平線 + VA 著色標示，明日情境觸發價以 badge 呈現。

現有 `vp.html` 和所有 API 不改動。

---

## 2. Architecture

### 新增

| 檔案 / 元件 | 說明 |
|------------|------|
| `vpoc.html` | 新頁面 |
| `VpocPageController` | `@GetMapping("/vpoc")` → `vpoc` view |
| `VpCandle` record | `(long time, int open, int high, int low, int close, int volume)` |
| `VolumeProfileService.getCandles()` | OHLCV 聚合邏輯 |
| `GET /api/vp/candles` | 新 endpoint |
| `LargeTrade.direction` | 新欄位，加在現有 record |

### 重用（零修改）

| API | 用途 |
|-----|------|
| `GET /api/vp/summary` | VPOC / VAH / VAL / Close / OHLC |
| `GET /api/vp/profile` | 右側 VP 橫向條 |
| `GET /api/vp/large-trades` | 大單列表（加 direction） |
| `GET /api/vp/plan` | 明日情境觸發價 badges |

---

## 3. Backend

### 3.1 `VpCandle` DTO

```java
package com.eagleeye.web.vp;

public record VpCandle(long time, int open, int high, int low, int close, int volume) {}
```

`time` = epoch seconds（UTC），lightweight-charts 原生格式。

### 3.2 `VolumeProfileService.getCandles()`

```
輸入：date, intervalMinutes（1 / 5 / 15）
1. loadTicks(date)（已排除 auction）
2. 每個 tick 計算 bucket key：
     hhmmss → totalSeconds = hh*3600 + mm*60 + ss
     bucketSec = (totalSeconds / (intervalMinutes*60)) * (intervalMinutes*60)
     epochSec = LocalDate → epochDay*86400 + bucketSec - 8*3600（轉 UTC）
3. groupBy(bucketSec)，每組取 open/high/low/close/sumVolume
4. 回傳 List<VpCandle> 按 time 升序
```

### 3.3 `LargeTrade` direction 推算

`getLargeTrades()` 中，建立全 tick list 的 price diff：

```
direction = price[i] > price[i-1] → UP
            price[i] < price[i-1] → DOWN
            price[i] == price[i-1] → NEUTRAL
```

`LargeTrade` record 加 `TradeDirection direction` 欄位（新 enum：`UP / DOWN / NEUTRAL`）。

### 3.4 新 Endpoint

```java
// VpController.java
@GetMapping("/candles")
public List<VpCandle> candles(
    @RequestParam String date,
    @RequestParam(defaultValue = "TX") String product,
    @RequestParam(defaultValue = "5") int interval)
```

---

## 4. Frontend

### 4.1 頁面佈局

```
┌─ header ──────────────────────────────────────────────────────┐
│  EagleEye / VPOC Chart   [1m][5m][15m]   ← Dashboard  ← VP  │
└───────────────────────────────────────────────────────────────┘
┌─ metric bar ──────────────────────────────────────────────────┐
│  VPOC  VAH  VAL  Close vs VPOC  Total Vol                     │
└───────────────────────────────────────────────────────────────┘
┌─ main grid ───────────────────────────────────┬───────────────┐
│                                               │               │
│  lightweight-charts candlestick (flex:1)      │  VP bars      │
│                                               │  (240px)      │
│  layers（底 → 頂）：                           │               │
│  1. VA 半透明藍區（VAL～VAH）                  │  每條顏色同    │
│  2. VAL 綠色虛線 + 標籤                        │  現有 vp.html │
│  3. VPOC 琥珀粗線 + 標籤                       │               │
│  4. VAH 紅色虛線 + 標籤                        │  價格刻度與    │
│  5. 進場 badges（情境觸發價）                  │  K 線 y 軸    │
│  6. K 線（輕量 OHLC）                          │  對齊         │
│  7. SVG overlay：大單氣泡                      │               │
│  8. Price markers：▲ ▼ ● 三角旗               │               │
│                                               │               │
└───────────────────────────────────────────────┴───────────────┘
```

### 4.2 大單視覺規格

| 元素 | 規則 |
|------|------|
| 氣泡半徑 | `r = 4 + (volume / maxLargeVol) * 14`（4px～18px）|
| 氣泡顏色 | `ABOVE_VAH`=`#ef4444`、`IN_VA`=`#378add`、`AT_VPOC`=`#d97706`、`BELOW_VAL`=`#22c55e` |
| 氣泡透明度 | `0.7`，hover → `1.0` + tooltip 顯示時間 / 價格 / 量 / 區域 |
| 三角旗形狀 | `UP` → ▲ 綠，K 線下方 8px；`DOWN` → ▼ 紅，K 線上方 8px；`NEUTRAL` → ● 灰 |
| 同位置疊合 | 氣泡置中，三角旗 x 偏移 +8px |

### 4.3 水平線規格

| 線 | 顏色 | 寬度 | 樣式 |
|----|------|------|------|
| VPOC | `#d97706` | 2px | solid |
| VAH | `#ef4444` | 1px | dashed |
| VAL | `#22c55e` | 1px | dashed |
| VA 著色 | `rgba(55,138,221,0.08)` | — | 填滿 VAL～VAH |

### 4.4 進場 Badges

從 `/api/vp/plan` 取 scenarios：
- Scenario 1（`BULLISH`）→ 在 VAL 價位顯示 `↑ 回測 VAL 不破進多`（綠底）
- Scenario 4（`BEARISH`）→ 在 support1 顯示 `⚠ 跌破留意`（紅底）
- 用 lightweight-charts `createPriceLine({ price, title, color })` 實作

### 4.5 右側 VP 條

- 資料來源：`/api/vp/profile?step=50`
- 純 CSS div，`width = volume / maxVol * 100%`
- 價格刻度與 K 線 y 軸對齊：監聽 lightweight-charts `visibleLogicalRangeChange` 事件，動態計算每個價格 bucket 的 y 像素位置

### 4.6 狀態管理

```javascript
let currentDate, currentInterval = 5;
let chartData = { candles, largeTrades, summary, profile, plan };
```

切換日期或 interval → 重新 fetch candles，其餘資料（summary、profile、plan）只在切換日期時重新 fetch。

---

## 5. API 呼叫順序

```
初始化：
  1. GET /api/vp/available-dates
  2. GET /api/vp/summary?date=
  3. GET /api/vp/candles?date=&interval=5
  4. GET /api/vp/profile?date=&step=50
  5. GET /api/vp/large-trades?date=&threshold=50
  6. GET /api/vp/plan?date=

切換 interval：
  1. GET /api/vp/candles?date=&interval=N  （僅此一個）

切換日期：
  全部重新 fetch（同初始化）
```

---

## 6. 不在本次 Scope

- 多日 VPOC 軌跡對比
- 即時串流（WebSocket）
- 行動版佈局
- 夜盤（AH）資料整合

---

## 7. 驗收標準

1. `/vpoc` 頁面可正常載入，K 線顯示當日分鐘資料
2. 切換 1m / 5m / 15m 正確重繪
3. VPOC（琥珀）、VAH（紅）、VAL（綠）橫線和 VA 著色顯示正確
4. 大單氣泡出現在正確的時間 + 價格位置，大小與口數成比例
5. 三角旗方向與 tick rule 計算一致
6. 右側 VP 條價格刻度與 K 線 y 軸對齊
7. 進場 badges 出現在正確價位
8. `GET /api/vp/candles` 回傳正確 OHLCV（單元測試）
9. `LargeTrade.direction` 計算正確（單元測試）
