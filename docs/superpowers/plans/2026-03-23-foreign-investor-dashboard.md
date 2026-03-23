# Foreign Investor Dashboard Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `eagleeye-web` Spring Boot module that serves a dark-theme web dashboard comparing 外資 (foreign institutional investor) positioning against the TAIEX weighted index, with divergence signal detection.

**Architecture:** New Maven module `eagleeye-web` depends on `eagleeye-domain` for JPA repository access. A single `DashboardService` queries 5 repositories, computes signals, and returns a `DashboardViewModel` record. A `DashboardController` passes the view model to a Thymeleaf template that renders Chart.js dual-axis charts.

**Tech Stack:** Spring Boot 4.0.3, Spring Web, Thymeleaf, Chart.js 4.x (CDN), JUnit 5, AssertJ, Mockito, `@WebMvcTest`

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `pom.xml` | Modify | Add `<module>eagleeye-web</module>` |
| `eagleeye-web/pom.xml` | Create | Module dependencies: domain, web, thymeleaf, test, sqlite, hibernate-community-dialects |
| `eagleeye-web/src/main/java/com/eagleeye/web/EagleeyeWebApplication.java` | Create | Spring Boot entry point |
| `eagleeye-web/src/main/resources/application.yml` | Create | Port 8080, H2 datasource, JPA ddl-auto=none |
| `eagleeye-web/src/main/java/com/eagleeye/web/DashboardViewModel.java` | Create | Record holding all chart data series + alerts; nested `AlertItem` record + `Severity` enum |
| `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java` | Create | Query 5 repos, compute 4 signals, detect divergences, return `DashboardViewModel` |
| `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java` | Create | Unit tests for signal computation and divergence detection (mocked repos) |
| `eagleeye-web/src/main/java/com/eagleeye/web/DashboardController.java` | Create | `GET /dashboard?days=20\|40\|60`, normalizes invalid days to 40 |
| `eagleeye-web/src/test/java/com/eagleeye/web/DashboardControllerTest.java` | Create | `@WebMvcTest`: template renders, days param passed, invalid days normalized |
| `eagleeye-web/src/main/resources/templates/dashboard.html` | Create | Thymeleaf + Chart.js: header, alert bar, TAIEX chart, 2×2 grid of signal charts |

---

## Chunk 1: Module Scaffold

Wire up the Maven module and confirm Spring Boot starts with the domain entities loaded.

---

### Task 1: Register `eagleeye-web` in the root POM

**Files:**
- Modify: `pom.xml:21-25`

- [ ] **Step 1: Add the module entry**

In `pom.xml`, add `<module>eagleeye-web</module>` inside `<modules>`:

```xml
<modules>
    <module>eagleeye-domain</module>
    <module>eagleeye-collector</module>
    <module>eagleeye-shell</module>
    <module>eagleeye-web</module>
</modules>
```

- [ ] **Step 2: Create the module directory**

```bash
mkdir -p eagleeye-web/src/main/java/com/eagleeye/web
mkdir -p eagleeye-web/src/main/resources/templates
mkdir -p eagleeye-web/src/test/java/com/eagleeye/web
```

- [ ] **Step 3: Create `eagleeye-web/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.eagleeye</groupId>
        <artifactId>eagleeye</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>eagleeye-web</artifactId>
    <name>EagleEye Web</name>
    <description>Web dashboard for foreign investor analysis</description>

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
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>${sqlite-jdbc.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-community-dialects</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <classifier>exec</classifier>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Create `EagleeyeWebApplication.java`**

```java
// eagleeye-web/src/main/java/com/eagleeye/web/EagleeyeWebApplication.java
package com.eagleeye.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EagleeyeWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(EagleeyeWebApplication.class, args);
    }
}
```

- [ ] **Step 5: Create `application.yml`**

```yaml
# eagleeye-web/src/main/resources/application.yml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:~/.eagleeye/data/eagleeye
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    ddl-auto: none
    hibernate:
      ddl-auto: none

---
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: jdbc:sqlite:${user.home}/.eagleeye/data/eagleeye.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
```

- [ ] **Step 6: Create a minimal placeholder template** (so the app starts without errors)

```html
<!-- eagleeye-web/src/main/resources/templates/dashboard.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><title>EagleEye Dashboard</title></head>
<body><p>Dashboard coming soon</p></body>
</html>
```

- [ ] **Step 7: Verify the module compiles**

```bash
./mvnw -pl eagleeye-web -am clean package -DskipTests -q
```

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 8: Commit**

```bash
git add pom.xml eagleeye-web/
git commit -m "feat(web): scaffold eagleeye-web module with Spring Boot + Thymeleaf"
```

---

## Chunk 2: View Model

---

### Task 2: Create `DashboardViewModel`

**Files:**
- Create: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardViewModel.java`

- [ ] **Step 1: Create the view model file**

```java
// eagleeye-web/src/main/java/com/eagleeye/web/DashboardViewModel.java
package com.eagleeye.web;

import java.util.List;

public record DashboardViewModel(
        List<String>    dateLabels,       // "M/d" x-axis labels for all charts
        List<Double>    taiexClose,       // entity close / 100.0 (display-ready)
        List<Long>      spotNetFlow,      // InstitutionalFlow.foreignNet per day (NTD)
        List<Long>      spotCumulative,   // running sum of spotNetFlow
        List<Double>    futuresLSRatio,   // (oiLongVolume - oiShortVolume) / (oiLongVolume + oiShortVolume)
        List<Long>      optionsNetOI,     // OptionsPosition.oiNetVolume (lots)
        List<Double>    marginChangeRate, // (marginBalance - marginPrevBalance) / marginPrevBalance
        List<AlertItem> alerts,
        int             days              // 20, 40, or 60
) {
    public record AlertItem(String signal, Severity severity, String message) {}

    public enum Severity { RED, YELLOW, GREEN }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./mvnw -pl eagleeye-web -am compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/DashboardViewModel.java
git commit -m "feat(web): add DashboardViewModel record with AlertItem and Severity"
```

---

## Chunk 3: Dashboard Service (TDD)

Write tests first, then implement. The service is the core of the dashboard — all signal computation and divergence detection lives here.

---

### Task 3: Write failing tests for `DashboardService`

**Files:**
- Create: `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java`

- [ ] **Step 1: Create the test file**

```java
// eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java
package com.eagleeye.web;

import com.eagleeye.domain.entity.*;
import com.eagleeye.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock TaiexIndexRepository taiexRepo;
    @Mock InstitutionalFlowRepository flowRepo;
    @Mock FuturesPositionRepository futuresRepo;
    @Mock OptionsPositionRepository optionsRepo;
    @Mock MarginTransactionRepository marginRepo;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(taiexRepo, flowRepo, futuresRepo, optionsRepo, marginRepo);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    TaiexIndex taiex(LocalDate date, long close) {
        TaiexIndex t = new TaiexIndex(date);
        t.setClose(close);
        return t;
    }

    InstitutionalFlow flow(LocalDate date, long foreignNet) {
        InstitutionalFlow f = new InstitutionalFlow(date);
        f.setForeignNet(foreignNet);
        return f;
    }

    FuturesPosition futures(LocalDate date, long oiLong, long oiShort) {
        FuturesPosition fp = new FuturesPosition(date, "TX", TraderType.FINI);
        fp.setOiLongVolume(oiLong);
        fp.setOiShortVolume(oiShort);
        fp.setOiNetVolume(oiLong - oiShort);
        return fp;
    }

    OptionsPosition options(LocalDate date, long oiNet) {
        OptionsPosition op = new OptionsPosition(date, "TXO", TraderType.FINI);
        op.setOiNetVolume(oiNet);
        op.setOiLongVolume(oiNet > 0 ? oiNet : 0);
        op.setOiShortVolume(oiNet < 0 ? -oiNet : 0);
        return op;
    }

    MarginTransaction margin(LocalDate date, long balance, long prevBalance) {
        MarginTransaction m = new MarginTransaction(date);
        m.setMarginBalance(balance);
        m.setMarginPrevBalance(prevBalance);
        return m;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void buildViewModel_returnsCorrectDateLabels() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2100000L), taiex(d2, 2110000L)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 1_000_000_000L), flow(d2, -500_000_000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 800L), futures(d2, 900L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TXO"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(options(d1, 500L), options(d2, 300L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d1, 1_010_000L, 1_000_000L), margin(d2, 1_005_000L, 1_010_000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.dateLabels()).containsExactly("3/3", "3/4");
        assertThat(vm.days()).isEqualTo(20);
    }

    @Test
    void buildViewModel_convertsTaiexCloseToDouble() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        stubSingleDay(d, 2145678L, 1_000_000_000L, 1000L, 800L, 500L, 1_010_000L, 1_000_000L);

        DashboardViewModel vm = service.buildViewModel(20);

        // 2145678 / 100.0 = 21456.78
        assertThat(vm.taiexClose()).containsExactly(21456.78);
    }

    @Test
    void buildViewModel_computesCumulativeSpotFlow() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);
        LocalDate d3 = LocalDate.of(2025, 3, 5);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2100000L), taiex(d2, 2110000L), taiex(d3, 2105000L)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 3_000_000_000L), flow(d2, -1_000_000_000L), flow(d3, 2_000_000_000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 800L), futures(d2, 900L, 900L), futures(d3, 950L, 850L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(options(d1, 500L), options(d2, 300L), options(d3, 400L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(
                margin(d1, 1_010_000L, 1_000_000L),
                margin(d2, 1_005_000L, 1_010_000L),
                margin(d3, 1_020_000L, 1_005_000L)
            ));

        DashboardViewModel vm = service.buildViewModel(20);

        // cumulative: 3B, 3B-1B=2B, 2B+2B=4B
        assertThat(vm.spotCumulative()).containsExactly(3_000_000_000L, 2_000_000_000L, 4_000_000_000L);
    }

    @Test
    void buildViewModel_computesFuturesLSRatio() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        // oiLong=1000, oiShort=600 → (1000-600)/(1000+600) = 400/1600 = 0.25
        stubSingleDay(d, 2100000L, 1_000_000_000L, 1000L, 600L, 500L, 1_010_000L, 1_000_000L);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.futuresLSRatio()).containsExactly(0.25);
    }

    @Test
    void buildViewModel_computesMarginChangeRate() {
        LocalDate d = LocalDate.of(2025, 3, 3);
        // balance=1_010_000, prevBalance=1_000_000 → (10000/1000000) = 0.01
        stubSingleDay(d, 2100000L, 1_000_000_000L, 1000L, 600L, 500L, 1_010_000L, 1_000_000L);

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.marginChangeRate().get(0)).isCloseTo(0.01, within(0.0001));
    }

    @Test
    void buildViewModel_detectsSpotDivergenceAfterTwoConsecutiveDays() {
        // Day 1: foreignNet > 0 (buying) but TAIEX falls → divergence day 1
        // Day 2: foreignNet > 0 (buying) but TAIEX falls → divergence day 2 → RED alert
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2200000L), taiex(d2, 2100000L)));  // TAIEX falling
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 5_000_000_000L), flow(d2, 3_000_000_000L)));  // 外資 buying
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 900L), futures(d2, 1000L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(options(d1, 200L), options(d2, 200L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d1, 1_010_000L, 1_000_000L), margin(d2, 1_005_000L, 1_010_000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.alerts())
            .anySatisfy(a -> {
                assertThat(a.severity()).isEqualTo(DashboardViewModel.Severity.RED);
                assertThat(a.signal()).contains("現貨");
            });
    }

    @Test
    void buildViewModel_doesNotTriggerDivergenceOnSingleDay() {
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2200000L), taiex(d2, 2250000L)));  // TAIEX rising on d2
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 5_000_000_000L), flow(d2, -1_000_000_000L)));  // selling on d2
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 900L), futures(d2, 1000L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(options(d1, 200L), options(d2, 200L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d1, 1_010_000L, 1_000_000L), margin(d2, 1_005_000L, 1_010_000L)));

        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.alerts())
            .noneMatch(a -> a.severity() == DashboardViewModel.Severity.RED
                         && a.signal().contains("現貨"));
    }

    @Test
    void buildViewModel_skipsZeroReturnDaysInDivergenceCount() {
        // d1: TAIEX flat (return=0) + 外資 buying → skip (zero return)
        // d2: TAIEX down + 外資 buying → divergence day 1 only → no RED
        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);

        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d1, 2200000L), taiex(d2, 2100000L)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d1, 5_000_000_000L), flow(d2, 3_000_000_000L)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(futures(d1, 1000L, 900L), futures(d2, 1000L, 900L)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any(), any()))
            .thenReturn(List.of(options(d1, 200L), options(d2, 200L)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d1, 1_010_000L, 1_000_000L), margin(d2, 1_005_000L, 1_010_000L)));

        // Override: d1 has same close as "previous" so return = 0
        // We can't control previous day easily with only 2 days — set d1 close = d2 close to simulate flat
        // Reset: d1 same close as d2 → no return on d1 from service perspective (need prev day)
        // The service computes daily return using consecutive pairs: return[i] = close[i] - close[i-1]
        // With only 2 points: return[0] is undefined (no prev), return[1] = close[1]-close[0]
        // So d1 is never evaluated for divergence; only d2 is → 1 divergence day → no RED
        DashboardViewModel vm = service.buildViewModel(20);

        assertThat(vm.alerts())
            .noneMatch(a -> a.severity() == DashboardViewModel.Severity.RED
                         && a.signal().contains("現貨"));
    }

    // ── Private stub helper ────────────────────────────────────────────────────

    private void stubSingleDay(LocalDate d, long taiexClose, long foreignNet,
                               long oiLong, long oiShort, long optionsOiNet,
                               long marginBalance, long marginPrev) {
        when(taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(taiex(d, taiexClose)));
        when(flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(flow(d, foreignNet)));
        when(futuresRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TX"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(futures(d, oiLong, oiShort)));
        when(optionsRepo.findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(eq("TXO"), eq(TraderType.FINI), any(), any()))
            .thenReturn(List.of(options(d, optionsOiNet)));
        when(marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(any(), any()))
            .thenReturn(List.of(margin(d, marginBalance, marginPrev)));
    }
}
```

- [ ] **Step 2: Run tests — they must fail (class not found)**

```bash
./mvnw -pl eagleeye-web test -q 2>&1 | tail -20
```

Expected: compilation error — `DashboardService` does not exist yet.

---

### Task 4: Implement `DashboardService`

**Files:**
- Create: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java`

- [ ] **Step 1: Create the service**

```java
// eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java
package com.eagleeye.web;

import com.eagleeye.domain.entity.*;
import com.eagleeye.domain.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DashboardService {

    private static final Set<Integer> ALLOWED_DAYS = Set.of(20, 40, 60);
    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("M/d");

    private final TaiexIndexRepository taiexRepo;
    private final InstitutionalFlowRepository flowRepo;
    private final FuturesPositionRepository futuresRepo;
    private final OptionsPositionRepository optionsRepo;
    private final MarginTransactionRepository marginRepo;

    public DashboardService(TaiexIndexRepository taiexRepo,
                            InstitutionalFlowRepository flowRepo,
                            FuturesPositionRepository futuresRepo,
                            OptionsPositionRepository optionsRepo,
                            MarginTransactionRepository marginRepo) {
        this.taiexRepo = taiexRepo;
        this.flowRepo = flowRepo;
        this.futuresRepo = futuresRepo;
        this.optionsRepo = optionsRepo;
        this.marginRepo = marginRepo;
    }

    public DashboardViewModel buildViewModel(int days) {
        if (!ALLOWED_DAYS.contains(days)) days = 40;

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days * 2L); // fetch extra to account for weekends/holidays

        // Query all 5 repositories
        List<TaiexIndex> taiexList = taiexRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<InstitutionalFlow> flowList = flowRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<FuturesPosition> futuresList = futuresRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TX", TraderType.FINI, from, to);
        List<OptionsPosition> optionsList = optionsRepo
            .findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc("TXO", TraderType.FINI, from, to);
        List<MarginTransaction> marginList = marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);

        // Align by trade date — build maps for O(1) lookup on all sources
        Map<LocalDate, TaiexIndex>        taiexMap = indexByDate(taiexList,   TaiexIndex::getTradeDate);
        Map<LocalDate, InstitutionalFlow> flowMap  = indexByDate(flowList,    InstitutionalFlow::getTradeDate);
        Map<LocalDate, FuturesPosition>   futMap   = indexByDate(futuresList, FuturesPosition::getTradeDate);
        Map<LocalDate, OptionsPosition>   optMap   = indexByDate(optionsList, OptionsPosition::getTradeDate);
        Map<LocalDate, MarginTransaction> mgnMap   = indexByDate(marginList,  MarginTransaction::getTradeDate);

        // Keep only dates where ALL 5 sources have data, then take last `days` entries
        List<LocalDate> alignedDates = taiexList.stream()
            .map(TaiexIndex::getTradeDate)
            .filter(d -> flowMap.containsKey(d) && futMap.containsKey(d)
                      && optMap.containsKey(d) && mgnMap.containsKey(d))
            .toList();

        // Take last `days` entries
        int start = Math.max(0, alignedDates.size() - days);
        List<LocalDate> dates = alignedDates.subList(start, alignedDates.size());

        // Build series
        List<String>  dateLabels    = new ArrayList<>();
        List<Double>  taiexClose    = new ArrayList<>();
        List<Long>    spotNetFlow   = new ArrayList<>();
        List<Long>    spotCumul     = new ArrayList<>();
        List<Double>  futLSRatio    = new ArrayList<>();
        List<Long>    optNetOI      = new ArrayList<>();
        List<Double>  marginChange  = new ArrayList<>();

        long cumulative = 0L;

        for (LocalDate date : dates) {
            TaiexIndex ti = taiexMap.get(date);
            InstitutionalFlow fl = flowMap.get(date);
            FuturesPosition   fp = futMap.get(date);
            OptionsPosition   op = optMap.get(date);
            MarginTransaction mg = mgnMap.get(date);

            dateLabels.add(date.format(LABEL_FMT));
            taiexClose.add(ti.getClose() / 100.0);

            long net = fl.getForeignNet() != null ? fl.getForeignNet() : 0L;
            cumulative += net;
            spotNetFlow.add(net);
            spotCumul.add(cumulative);

            long oiLong = fp.getOiLongVolume() != null ? fp.getOiLongVolume() : 0L;
            long oiShort = fp.getOiShortVolume() != null ? fp.getOiShortVolume() : 0L;
            double ratio = (oiLong + oiShort) == 0 ? 0.0
                : (double)(oiLong - oiShort) / (oiLong + oiShort);
            futLSRatio.add(ratio);

            optNetOI.add(op.getOiNetVolume() != null ? op.getOiNetVolume() : 0L);

            long balance = mg.getMarginBalance() != null ? mg.getMarginBalance() : 0L;
            long prev    = mg.getMarginPrevBalance() != null ? mg.getMarginPrevBalance() : 1L;
            marginChange.add(prev == 0 ? 0.0 : (double)(balance - prev) / prev);
        }

        List<DashboardViewModel.AlertItem> alerts = detectAlerts(
            taiexClose, spotNetFlow, futLSRatio, optNetOI, marginChange);

        return new DashboardViewModel(
            dateLabels, taiexClose, spotNetFlow, spotCumul,
            futLSRatio, optNetOI, marginChange, alerts, days);
    }

    // ── Signal detection ──────────────────────────────────────────────────────

    private List<DashboardViewModel.AlertItem> detectAlerts(
            List<Double> taiexClose,
            List<Long>   spotNetFlow,
            List<Double> futLSRatio,
            List<Long>   optNetOI,
            List<Double> marginChange) {

        List<DashboardViewModel.AlertItem> alerts = new ArrayList<>();

        // 1. 外資現貨 divergence: sign(foreignNet) ≠ sign(taiexReturn) for ≥ 2 consecutive days
        int spotDivergeDays = 0;
        for (int i = 1; i < taiexClose.size(); i++) {
            double ret = taiexClose.get(i) - taiexClose.get(i - 1);
            if (ret == 0) continue; // skip flat days
            long net = spotNetFlow.get(i);
            boolean diverges = (net > 0 && ret < 0) || (net < 0 && ret > 0);
            spotDivergeDays = diverges ? spotDivergeDays + 1 : 0;
        }
        if (spotDivergeDays >= 2) {
            alerts.add(new DashboardViewModel.AlertItem(
                "外資現貨", DashboardViewModel.Severity.RED,
                "外資現貨 買賣方向與 TAIEX 走勢背離 " + spotDivergeDays + " 日"));
        }

        // 2. 外資期貨 L/S ratio: 3-day MA slope < -0.05 while TAIEX 3-day return > 0
        if (futLSRatio.size() >= 3) {
            int n = futLSRatio.size();
            double ratioSlope = futLSRatio.get(n - 1) - futLSRatio.get(n - 3);
            double taiexReturn3d = taiexClose.get(n - 1) - taiexClose.get(n - 3);
            if (ratioSlope < -0.05 && taiexReturn3d > 0) {
                alerts.add(new DashboardViewModel.AlertItem(
                    "外資期貨", DashboardViewModel.Severity.YELLOW,
                    "期貨多空比走弱，TAIEX 仍上漲"));
            }
        }

        // 3. 外資選擇權: oiNetOI 5-day trend contradicts TAIEX 5-day direction
        if (optNetOI.size() >= 5 && taiexClose.size() >= 5) {
            int n = optNetOI.size();
            long optTrend = optNetOI.get(n - 1) - optNetOI.get(n - 5);
            double taiexTrend = taiexClose.get(n - 1) - taiexClose.get(n - 5);
            if ((optTrend < 0 && taiexTrend > 0) || (optTrend > 0 && taiexTrend < 0)) {
                alerts.add(new DashboardViewModel.AlertItem(
                    "外資選擇權", DashboardViewModel.Severity.YELLOW,
                    "選擇權部位方向與 TAIEX 走勢相反"));
            }
        }

        // 4. 融資: margin growing > 1.5% while 外資 net selling on last day
        if (!marginChange.isEmpty() && !spotNetFlow.isEmpty()) {
            double lastMargin = marginChange.get(marginChange.size() - 1);
            long lastNet = spotNetFlow.get(spotNetFlow.size() - 1);
            if (lastMargin > 0.015 && lastNet < 0) {
                alerts.add(new DashboardViewModel.AlertItem(
                    "融資券", DashboardViewModel.Severity.YELLOW,
                    "融資大增，外資同步賣超，零售風險上升"));
            }
        }

        // 5. Combined escalation: 外資現貨 + 期貨 both diverge → RED (escalate)
        boolean spotDiverged = alerts.stream()
            .anyMatch(a -> a.signal().contains("現貨") && a.severity() == DashboardViewModel.Severity.RED);
        boolean futuresDiverged = alerts.stream()
            .anyMatch(a -> a.signal().contains("期貨") && a.severity() == DashboardViewModel.Severity.YELLOW);
        if (spotDiverged && futuresDiverged) {
            alerts.add(new DashboardViewModel.AlertItem(
                "綜合警示", DashboardViewModel.Severity.RED,
                "外資現貨 + 期貨同步背離 TAIEX，高度警戒"));
        }

        // 6. If no divergence alerts → GREEN
        boolean hasRed = alerts.stream()
            .anyMatch(a -> a.severity() == DashboardViewModel.Severity.RED);
        boolean hasYellow = alerts.stream()
            .anyMatch(a -> a.severity() == DashboardViewModel.Severity.YELLOW);
        if (!hasRed && !hasYellow) {
            alerts.add(new DashboardViewModel.AlertItem(
                "總覽", DashboardViewModel.Severity.GREEN, "各訊號與 TAIEX 走勢一致"));
        }

        return alerts;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private <T> Map<LocalDate, T> indexByDate(List<T> list,
                                               java.util.function.Function<T, LocalDate> keyFn) {
        Map<LocalDate, T> map = new LinkedHashMap<>();
        for (T item : list) map.put(keyFn.apply(item), item);
        return map;
    }
}
```

- [ ] **Step 2: Run tests — all must pass**

```bash
./mvnw -pl eagleeye-web test -q
```

Expected: all tests in `DashboardServiceTest` pass.

- [ ] **Step 3: Commit**

```bash
git add eagleeye-web/src/
git commit -m "feat(web): add DashboardService with signal computation and divergence detection"
```

---

## Chunk 3: Controller

---

### Task 5: Write failing controller test

**Files:**
- Create: `eagleeye-web/src/test/java/com/eagleeye/web/DashboardControllerTest.java`

- [ ] **Step 1: Create the test file**

```java
// eagleeye-web/src/test/java/com/eagleeye/web/DashboardControllerTest.java
package com.eagleeye.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean DashboardService service;

    DashboardViewModel emptyVm(int days) {
        return new DashboardViewModel(
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), days);
    }

    @Test
    void dashboard_defaultDays_is40() throws Exception {
        when(service.buildViewModel(40)).thenReturn(emptyVm(40));

        mvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"))
            .andExpect(model().attributeExists("vm"));
    }

    @Test
    void dashboard_acceptsValidDays() throws Exception {
        when(service.buildViewModel(20)).thenReturn(emptyVm(20));

        mvc.perform(get("/dashboard?days=20"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("vm", emptyVm(20)));
    }

    @Test
    void dashboard_normalizesInvalidDaysTo40() throws Exception {
        when(service.buildViewModel(40)).thenReturn(emptyVm(40));

        mvc.perform(get("/dashboard?days=999"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("vm", emptyVm(40)));
    }
}
```

- [ ] **Step 2: Run — must fail (controller does not exist)**

```bash
./mvnw -pl eagleeye-web test -Dtest=DashboardControllerTest -q 2>&1 | tail -10
```

Expected: compilation error — `DashboardController` not found.

---

### Task 6: Implement `DashboardController`

**Files:**
- Create: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardController.java`

- [ ] **Step 1: Create the controller**

```java
// eagleeye-web/src/main/java/com/eagleeye/web/DashboardController.java
package com.eagleeye.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

@Controller
public class DashboardController {

    private static final Set<Integer> ALLOWED_DAYS = Set.of(20, 40, 60);

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "40") int days, Model model) {
        if (!ALLOWED_DAYS.contains(days)) days = 40;
        model.addAttribute("vm", service.buildViewModel(days));
        return "dashboard";
    }
}
```

- [ ] **Step 2: Run all tests — all must pass**

```bash
./mvnw -pl eagleeye-web test -q
```

Expected: all tests pass.

- [ ] **Step 3: Commit**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/DashboardController.java \
        eagleeye-web/src/test/java/com/eagleeye/web/DashboardControllerTest.java
git commit -m "feat(web): add DashboardController with days normalization"
```

---

## Chunk 4: Dashboard Template

---

### Task 7: Build the Thymeleaf + Chart.js dashboard

**Files:**
- Modify: `eagleeye-web/src/main/resources/templates/dashboard.html`

Replace the placeholder with the full dashboard:

- [ ] **Step 1: Write the template**

```html
<!-- eagleeye-web/src/main/resources/templates/dashboard.html -->
<!DOCTYPE html>
<html lang="zh-TW" xmlns:th="http://www.thymeleaf.org">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>EagleEye — 外資追蹤儀表板</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { background: #0d1117; color: #e6edf3; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; font-size: 13px; }
.header { background: #161b22; border-bottom: 1px solid #30363d; padding: 12px 20px; display: flex; align-items: center; justify-content: space-between; }
.header-title { font-size: 15px; font-weight: 600; color: #58a6ff; }
.header-subtitle { font-size: 11px; color: #8b949e; margin-top: 2px; }
.range-toggle { display: flex; gap: 6px; }
.range-btn { background: #21262d; border: 1px solid #30363d; color: #8b949e; padding: 4px 14px; border-radius: 6px; cursor: pointer; font-size: 12px; text-decoration: none; }
.range-btn.active { background: #1f6feb; border-color: #1f6feb; color: #fff; }
.alert-bar { background: #161b22; border-bottom: 1px solid #30363d; padding: 8px 20px; display: flex; align-items: center; flex-wrap: wrap; gap: 16px; min-height: 36px; }
.alert-label { font-size: 11px; color: #8b949e; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.alert-item { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot-RED { background: #f85149; }
.dot-YELLOW { background: #d29922; }
.dot-GREEN { background: #3fb950; }
.dashboard { padding: 16px 20px; display: flex; flex-direction: column; gap: 14px; }
.chart-card { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 12px 16px; }
.chart-card-title { font-size: 12px; font-weight: 600; color: #8b949e; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }
.chart-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
canvas { max-height: 160px; }
.canvas-sm { max-height: 120px; }
.stat-row { display: flex; gap: 8px; margin-bottom: 8px; }
.stat-box { background: #0d1117; border-radius: 6px; padding: 6px 10px; flex: 1; text-align: center; }
.stat-val { font-size: 13px; font-weight: 600; }
.stat-lbl { font-size: 10px; color: #8b949e; margin-top: 1px; }
.val-red { color: #f85149; }
.val-green { color: #3fb950; }
.val-blue { color: #58a6ff; }
.val-yellow { color: #d29922; }
@media (max-width: 700px) { .chart-grid { grid-template-columns: 1fr; } }
</style>
</head>
<body>

<!-- Header -->
<div class="header">
  <div>
    <div class="header-title">🦅 EagleEye — 外資追蹤儀表板</div>
    <div class="header-subtitle">外資現貨 / 期貨 / 選擇權 / 融資券 vs 加權指數</div>
  </div>
  <div class="range-toggle">
    <a th:href="@{/dashboard(days=20)}" class="range-btn" th:classappend="${vm.days == 20} ? 'active'">20天</a>
    <a th:href="@{/dashboard(days=40)}" class="range-btn" th:classappend="${vm.days == 40} ? 'active'">40天</a>
    <a th:href="@{/dashboard(days=60)}" class="range-btn" th:classappend="${vm.days == 60} ? 'active'">60天</a>
  </div>
</div>

<!-- Alert Bar -->
<div class="alert-bar">
  <span class="alert-label">訊號</span>
  <div class="alert-item" th:each="alert : ${vm.alerts}">
    <div class="dot" th:classappend="'dot-' + ${alert.severity()}"></div>
    <span th:text="${alert.signal() + ': ' + alert.message()}">alert</span>
  </div>
</div>

<div class="dashboard">

  <!-- TAIEX — Full Width -->
  <div class="chart-card">
    <div class="chart-card-title">加權指數 TAIEX</div>
    <canvas id="taiexChart"></canvas>
  </div>

  <!-- 2x2 Grid -->
  <div class="chart-grid">

    <!-- 外資現貨 -->
    <div class="chart-card">
      <div class="chart-card-title">外資現貨 淨買賣</div>
      <div class="stat-row" th:if="${!vm.spotNetFlow.isEmpty()}">
        <div class="stat-box">
          <div class="stat-val"
               th:classappend="${vm.spotCumulative.get(vm.spotCumulative.size()-1) >= 0} ? 'val-red' : 'val-green'"
               th:text="${#numbers.formatDecimal(vm.spotCumulative.get(vm.spotCumulative.size()-1) / 1000000000.0, 1, 'COMMA', 1, 'POINT') + 'B'}">0B</div>
          <div class="stat-lbl">累積淨買</div>
        </div>
        <div class="stat-box">
          <div class="stat-val"
               th:classappend="${vm.spotNetFlow.get(vm.spotNetFlow.size()-1) >= 0} ? 'val-red' : 'val-green'"
               th:text="${#numbers.formatDecimal(vm.spotNetFlow.get(vm.spotNetFlow.size()-1) / 1000000000.0, 1, 'COMMA', 1, 'POINT') + 'B'}">0B</div>
          <div class="stat-lbl">今日</div>
        </div>
      </div>
      <canvas id="spotChart" class="canvas-sm"></canvas>
    </div>

    <!-- 外資期貨 -->
    <div class="chart-card">
      <div class="chart-card-title">外資期貨 多空比</div>
      <div class="stat-row" th:if="${!vm.futuresLSRatio.isEmpty()}">
        <div class="stat-box">
          <div class="stat-val val-blue"
               th:text="${#numbers.formatDecimal(vm.futuresLSRatio.get(vm.futuresLSRatio.size()-1), 1, 'COMMA', 2, 'POINT')}">0.00</div>
          <div class="stat-lbl">今日多空比</div>
        </div>
      </div>
      <canvas id="futuresChart" class="canvas-sm"></canvas>
    </div>

    <!-- 外資選擇權 -->
    <div class="chart-card">
      <div class="chart-card-title">外資選擇權 淨部位</div>
      <div class="stat-row" th:if="${!vm.optionsNetOI.isEmpty()}">
        <div class="stat-box">
          <div class="stat-val"
               th:classappend="${vm.optionsNetOI.get(vm.optionsNetOI.size()-1) >= 0} ? 'val-green' : 'val-red'"
               th:text="${vm.optionsNetOI.get(vm.optionsNetOI.size()-1)}">0</div>
          <div class="stat-lbl">今日淨OI（口）</div>
        </div>
      </div>
      <canvas id="optionsChart" class="canvas-sm"></canvas>
    </div>

    <!-- 融資券 -->
    <div class="chart-card">
      <div class="chart-card-title">融資券 增減率</div>
      <div class="stat-row" th:if="${!vm.marginChangeRate.isEmpty()}">
        <div class="stat-box">
          <div class="stat-val"
               th:classappend="${vm.marginChangeRate.get(vm.marginChangeRate.size()-1) >= 0} ? 'val-yellow' : 'val-green'"
               th:text="${#numbers.formatDecimal(vm.marginChangeRate.get(vm.marginChangeRate.size()-1) * 100, 1, 'COMMA', 2, 'POINT') + '%'}">0.00%</div>
          <div class="stat-lbl">今日融資增減</div>
        </div>
      </div>
      <canvas id="marginChart" class="canvas-sm"></canvas>
    </div>

  </div>
</div>

<script th:inline="javascript">
/*<![CDATA[*/
const labels       = /*[[${vm.dateLabels}]]*/ [];
const taiexData    = /*[[${vm.taiexClose}]]*/ [];
const spotNet      = /*[[${vm.spotNetFlow}]]*/ [];
const futRatio     = /*[[${vm.futuresLSRatio}]]*/ [];
const optNetOI     = /*[[${vm.optionsNetOI}]]*/ [];
const marginRate   = /*[[${vm.marginChangeRate}]]*/ [];

const TAIEX_COLOR  = '#58a6ff';
const GRID_COLOR   = 'rgba(48,54,61,0.8)';

const baseScales = {
  x: { ticks: { color: '#8b949e', font: { size: 9 }, maxTicksLimit: 10 }, grid: { color: GRID_COLOR } },
  y: { position: 'left',  ticks: { color: '#8b949e', font: { size: 9 } }, grid: { color: GRID_COLOR } },
  y2:{ position: 'right', ticks: { color: TAIEX_COLOR, font: { size: 9 } }, grid: { drawOnChartArea: false } }
};
const baseOpts = {
  responsive: true, maintainAspectRatio: true,
  interaction: { mode: 'index', intersect: false },
  plugins: { legend: { display: false } },
  scales: baseScales
};
const taiexOverlay = {
  type: 'line', data: taiexData,
  borderColor: TAIEX_COLOR, borderWidth: 1.5, pointRadius: 0,
  yAxisID: 'y2', tension: 0.3
};

// TAIEX main chart
new Chart(document.getElementById('taiexChart'), {
  type: 'line',
  data: { labels, datasets: [{ data: taiexData, borderColor: TAIEX_COLOR, borderWidth: 2, pointRadius: 0, fill: false, tension: 0.3 }] },
  options: { responsive: true, maintainAspectRatio: true, plugins: { legend: { display: false } },
    scales: { x: baseScales.x, y: { ticks: { color: '#8b949e', font: { size: 9 } }, grid: { color: GRID_COLOR } } } }
});

// 外資現貨 — bar (net) + TAIEX overlay
new Chart(document.getElementById('spotChart'), {
  data: { labels, datasets: [
    { type: 'bar', data: spotNet.map(v => v / 1e9),
      backgroundColor: spotNet.map(v => v >= 0 ? 'rgba(248,81,73,0.7)' : 'rgba(63,185,80,0.7)'), yAxisID: 'y' },
    { ...taiexOverlay }
  ]},
  options: baseOpts
});

// 外資期貨 — area line + TAIEX overlay
new Chart(document.getElementById('futuresChart'), {
  type: 'line',
  data: { labels, datasets: [
    { data: futRatio, borderColor: '#d29922', borderWidth: 2, pointRadius: 0,
      fill: { target: 'origin', above: 'rgba(210,153,34,0.15)', below: 'rgba(248,81,73,0.15)' },
      yAxisID: 'y', tension: 0.3 },
    { ...taiexOverlay }
  ]},
  options: baseOpts
});

// 外資選擇權 — line + TAIEX overlay
new Chart(document.getElementById('optionsChart'), {
  type: 'line',
  data: { labels, datasets: [
    { data: optNetOI, borderColor: '#3fb950', borderWidth: 2, pointRadius: 0,
      fill: false, yAxisID: 'y', tension: 0.3 },
    { ...taiexOverlay }
  ]},
  options: baseOpts
});

// 融資券 — bar + TAIEX overlay
new Chart(document.getElementById('marginChart'), {
  data: { labels, datasets: [
    { type: 'bar', data: marginRate.map(v => v * 100),
      backgroundColor: marginRate.map(v => v >= 0 ? 'rgba(210,153,34,0.7)' : 'rgba(63,185,80,0.5)'), yAxisID: 'y' },
    { ...taiexOverlay }
  ]},
  options: baseOpts
});
/*]]>*/
</script>

</body>
</html>
```

- [ ] **Step 2: Run all tests**

```bash
./mvnw -pl eagleeye-web test -q
```

Expected: all tests pass (template changes don't affect unit/MVC tests).

- [ ] **Step 3: Do a full build to check for any issues**

```bash
./mvnw -pl eagleeye-web -am clean package -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Smoke-test in the browser**

First backfill some data if the local H2 DB is empty:

```bash
# In a separate terminal: start the collector with backfill to populate local DB
java -jar eagleeye-collector/target/eagleeye-collector-exec.jar \
  --eagleeye.backfill.from=2024-12-01 \
  --eagleeye.backfill.to=2025-03-01
```

Then start the web server:

```bash
java -jar eagleeye-web/target/eagleeye-web-exec.jar
```

Open [http://localhost:8080/dashboard](http://localhost:8080/dashboard) — verify:
- Dark theme loads
- 4 charts render with TAIEX overlay lines
- Alert bar shows at least one signal
- 20/40/60天 toggle buttons switch chart ranges

- [ ] **Step 5: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/dashboard.html
git commit -m "feat(web): add Thymeleaf dashboard template with Chart.js dual-axis charts"
```

---

## Chunk 5: Final Integration

---

### Task 8: Full build and clean-up

- [ ] **Step 1: Run the full project build**

```bash
./mvnw clean package -q
```

Expected: `BUILD SUCCESS` — all 4 modules compile and test.

- [ ] **Step 2: Final commit**

```bash
git add .
git status  # verify nothing unintended
git commit -m "feat(web): eagleeye-web module complete — 外資 vs TAIEX dashboard"
```
