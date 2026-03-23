# EagleEye — 外資追蹤儀表板 Design Spec

**Date:** 2026-03-23
**Status:** Approved

---

## 1. Goal

Build a web dashboard for short-term analysis of foreign institutional investor (外資) activity versus the TAIEX weighted index (加權指數). The primary purpose is **divergence detection and signal analysis**, not monitoring. The dashboard surfaces when 外資 positioning contradicts TAIEX price action — a key signal for professional traders.

---

## 2. Scope

### Signals Tracked

| Signal | Source Entity | Derived Metric |
|--------|--------------|----------------|
| 外資現貨 淨買賣 | `InstitutionalFlow` | `foreignNet = foreignBuy - foreignSell` (NTD); 40-day cumulative |
| 外資期貨 多空比 | `FuturesPosition` (FINI) | `(longOI - shortOI) / (longOI + shortOI)`, range -1 to +1 |
| 外資選擇權 淨部位 | `OptionsPosition` (FINI) | `callLongOI - putLongOI` (directional bias) |
| 融資券 增減率 | `MarginTransaction` | `(todayBalance - prevBalance) / prevBalance` (retail sentiment) |
| 加權指數 | `TaiexIndex` | Close price, daily return |

### Out of Scope

- Real-time / intraday data (batch collection only, updated daily at 16:00)
- Individual stock analysis
- Portfolio or P&L tracking
- Authentication / multi-user

---

## 3. Architecture

### New Module: `eagleeye-web`

A new Maven module added alongside existing modules. Depends on `eagleeye-domain` for repository access.

```
eagleeye/
├── eagleeye-domain      [existing]
├── eagleeye-collector   [existing]
├── eagleeye-shell       [existing]
└── eagleeye-web         [NEW]
    ├── pom.xml
    └── src/main/
        ├── java/com/eagleeye/web/
        │   ├── EagleeyeWebApplication.java
        │   ├── DashboardController.java
        │   ├── DashboardService.java
        │   └── DashboardViewModel.java
        └── resources/
            ├── application.yml
            └── templates/
                └── dashboard.html
```

### Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 4.x + Spring Web |
| Templating | Thymeleaf |
| Charts | Chart.js 4.x (CDN) |
| Styling | Inline CSS (dark theme, no framework dependency) |
| Port | 8080 |
| Database | Same as collector (H2/SQLite/PostgreSQL via profile) |

### Data Flow

```
Browser GET /dashboard?days=40
  → DashboardController
  → DashboardService.buildViewModel(days)
      → TaiexIndexRepository.findByTradeDateBetween(from, to)
      → InstitutionalFlowRepository.findByTradeDateBetween(from, to)
      → FuturesPositionRepository.findByTradeDateBetweenAndTraderType(from, to, FINI)
      → OptionsPositionRepository.findByTradeDateBetweenAndTraderType(from, to, FINI)
      → MarginTransactionRepository.findByTradeDateBetween(from, to)
      → compute signals, detect divergences
      → return DashboardViewModel
  → Thymeleaf renders dashboard.html
  → Chart.js renders charts with injected JSON data
```

---

## 4. Components

### `DashboardController`

```java
@Controller
public class DashboardController {
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "40") int days, Model model) {
        model.addAttribute("vm", dashboardService.buildViewModel(days));
        return "dashboard";
    }
}
```

### `DashboardService`

Responsibilities:
- Query all 5 repositories for the requested date range
- Align data by trade date (some dates may have gaps — use inner join on available dates)
- Compute derived metrics (cumulative net, futures ratio, options net OI, margin change %)
- Detect divergence signals (see Section 5)
- Return `DashboardViewModel`

### `DashboardViewModel`

```java
public record DashboardViewModel(
    List<String>  dateLabels,        // "M/d" format for chart x-axis
    List<Long>    taiexClose,        // TAIEX close ×100 (fixed-point)
    List<Long>    spotNetFlow,       // 外資現貨 daily net (NTD)
    List<Long>    spotCumulative,    // 外資現貨 cumulative net (NTD)
    List<Double>  futuresLSRatio,    // 外資期貨 L/S ratio [-1, +1]
    List<Long>    optionsNetOI,      // 外資選擇權 call-put net OI (lots)
    List<Double>  marginChangeRate,  // 融資 daily change %
    List<AlertItem> alerts,          // divergence alert signals
    int           days               // selected range (20/40/60)
)

public record AlertItem(String signal, Severity severity, String message) {}
public enum Severity { RED, YELLOW, GREEN }
```

### `dashboard.html` (Thymeleaf)

Layout — Stacked Panels:
1. **Header bar** — title + 20/40/60天 toggle buttons (links with `?days=N`)
2. **Alert bar** — rendered alert items with severity-colored dots
3. **TAIEX chart** — full-width line chart (Chart.js)
4. **2×2 grid**:
   - 外資現貨: bar chart (red=buy, green=sell) + TAIEX line overlay (dual y-axis)
   - 外資期貨: L/S ratio area line + TAIEX overlay
   - 外資選擇權: net OI line + TAIEX overlay
   - 融資券: margin change % bars + TAIEX overlay
5. Each panel has a **stats row** (cumulative total, today's value)

Chart.js data injected via Thymeleaf inline JavaScript:
```html
<script th:inline="javascript">
  const taiexData = /*[[${vm.taiexClose}]]*/ [];
  const spotNet   = /*[[${vm.spotNetFlow}]]*/ [];
  // ...
</script>
```

---

## 5. Divergence Signal Logic

### Detection Rules

| Signal | Divergence Condition | Severity |
|--------|---------------------|----------|
| 外資現貨 | `sign(foreignNet) ≠ sign(taiexReturn)` for ≥ 2 consecutive days | RED |
| 外資期貨 | Futures L/S ratio trending down (3-day MA slope < -0.05) while TAIEX up | YELLOW |
| 外資選擇權 | Options net OI contradicts TAIEX 5-day direction | YELLOW |
| 融資券 | Margin balance growing > 1.5% while 外資 net selling | YELLOW |
| Combined | 外資現貨 + 期貨 both diverge simultaneously | RED (escalate) |
| Aligned | All signals consistent with TAIEX | GREEN |

### Alert Bar Display

Alerts are rendered with severity colors:
- 🔴 RED — strong divergence, watch for reversal
- 🟡 YELLOW — moderate signal, monitor
- 🟢 GREEN — signals aligned with TAIEX trend

---

## 6. URL Structure

| URL | Description |
|-----|-------------|
| `/dashboard` | Default view (40 days) |
| `/dashboard?days=20` | 20-day short-term view |
| `/dashboard?days=40` | 40-day medium view |
| `/dashboard?days=60` | 60-day extended view |

---

## 7. Configuration

`eagleeye-web/src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:~/.eagleeye/data/eagleeye  # same as collector
  jpa:
    ddl-auto: none  # web module never modifies schema
```

Shares database profiles with `eagleeye-collector` (H2 dev, SQLite prod, PostgreSQL enterprise).

---

## 8. Maven Structure

`eagleeye-web/pom.xml` key dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>com.eagleeye</groupId>
    <artifactId>eagleeye-domain</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
  </dependency>
</dependencies>
```

Chart.js loaded via CDN in `dashboard.html` — no npm/Node.js build step required.

---

## 9. Testing Strategy

| Layer | Approach |
|-------|---------|
| `DashboardService` | Unit tests with mock repositories; verify signal computation and divergence logic |
| `DashboardController` | `@WebMvcTest` with mocked service; verify template rendering and query param handling |
| Integration | Manual browser test against local H2 with backfilled data |

---

## 10. Deployment

The web module runs as a standalone Spring Boot JAR:

```bash
java -jar eagleeye-web-exec.jar            # dev (H2)
java -jar eagleeye-web-exec.jar --spring.profiles.active=prod  # SQLite
```

Can run alongside `eagleeye-collector` on the same machine, sharing the same database file.
