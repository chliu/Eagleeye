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
| 外資現貨 淨買賣 | `InstitutionalFlow` | `foreignNet = foreignBuy - foreignSell` (NTD); running cumulative |
| 外資期貨 多空比 | `FuturesPosition` contract=`"TX"`, traderType=`FINI` | `(oiLongVolume - oiShortVolume) / (oiLongVolume + oiShortVolume)`, range -1 to +1 |
| 外資選擇權 淨部位 | `OptionsPosition` contract=`"TXO"`, traderType=`FINI` | `oiNetVolume` (already stored: long OI − short OI, in lots) |
| 融資券 增減率 | `MarginTransaction` | `(marginBalance - marginPrevBalance) / (double) marginPrevBalance` (retail sentiment) |
| 加權指數 | `TaiexIndex` | Close price (stored as `close / 100.0` → `Double` for display), daily return |

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
      → TaiexIndexRepository.findByTradeDateBetweenOrderByTradeDateAsc(from, to)
      → InstitutionalFlowRepository.findByTradeDateBetweenOrderByTradeDateAsc(from, to)
      → FuturesPositionRepository.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TX", FINI, from, to)
      → OptionsPositionRepository.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TXO", FINI, from, to)
      → MarginTransactionRepository.findByTradeDateBetweenOrderByTradeDateAsc(from, to)
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
    private static final Set<Integer> ALLOWED_DAYS = Set.of(20, 40, 60);

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "40") int days, Model model) {
        if (!ALLOWED_DAYS.contains(days)) days = 40;  // silently normalize
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
    List<Double>  taiexClose,        // TAIEX close (entity value / 100.0) — display-ready
    List<Long>    spotNetFlow,       // 外資現貨 daily net NTD (foreignNet field)
    List<Long>    spotCumulative,    // 外資現貨 cumulative net NTD over selected period
    List<Double>  futuresLSRatio,    // 外資期貨 L/S ratio: (oiLongVolume-oiShortVolume)/(oiLongVolume+oiShortVolume)
    List<Long>    optionsNetOI,      // 外資選擇權 oiNetVolume (lots, already stored as long-short)
    List<Double>  marginChangeRate,  // 融資 (marginBalance-marginPrevBalance)/marginPrevBalance
    List<AlertItem> alerts,          // divergence alert signals
    int           days               // selected range: 20, 40, or 60 only
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
| 外資現貨 | `sign(foreignNet) ≠ sign(taiexReturn)` for ≥ 2 consecutive days; skip days where `taiexReturn == 0` (holidays/gaps) | RED |
| 外資期貨 | `oiLongVolume - oiShortVolume` 3-day MA slope < -0.05 while TAIEX 3-day return > 0 | YELLOW |
| 外資選擇權 | `oiNetVolume` 5-day trend contradicts TAIEX 5-day direction | YELLOW |
| 融資券 | `(marginBalance - marginPrevBalance) / marginPrevBalance > 0.015` while `foreignNet < 0` | YELLOW |
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

**Entity/repository scanning:** `eagleeye-domain` already contains `DomainConfiguration` (`@Configuration @EnableJpaRepositories("com.eagleeye.domain.repository")` + `PersistenceManagedTypesScanner` for `com.eagleeye.domain.entity`). Because `eagleeye-web` depends on `eagleeye-domain`, Spring Boot will pick up `DomainConfiguration` automatically via component scan — no additional `@EntityScan` or `@EnableJpaRepositories` needed on `EagleeyeWebApplication`.

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

The root `pom.xml` must also declare `eagleeye-web` as a `<module>` entry alongside the existing modules.

---

## 9. Testing Strategy

| Layer | Approach |
|-------|---------|
| `DashboardService` | Unit tests with mock repositories; key cases: (1) two consecutive spot divergence days → RED alert, (2) futures L/S ratio declining while TAIEX rising → YELLOW, (3) zero taiexReturn day skipped in divergence count |
| `DashboardController` | `@WebMvcTest` with mocked service; verify template renders, `?days=20/40/60` passes correctly, invalid `days` silently normalizes to 40 |
| Integration | Manual browser test against local H2 with backfilled data |

---

## 10. Deployment

The web module runs as a standalone Spring Boot JAR:

```bash
java -jar eagleeye-web-exec.jar            # dev (H2)
java -jar eagleeye-web-exec.jar --spring.profiles.active=prod  # SQLite
```

Can run alongside `eagleeye-collector` on the same machine, sharing the same database file.
