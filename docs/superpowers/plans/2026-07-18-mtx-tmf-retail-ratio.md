# MTX/TMF 散戶多空比 Dashboard Panels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two dashboard panels (小台指/MTX and 微台指/TMF 散戶多空比 vs 加權指數), which requires a new TAIFEX collector for total market open interest (not currently collected — the existing data only has per-trader-type OI).

**Architecture:** A new `FuturesMarketOi` entity/repo stores total OI per contract/date. A new `TaifexMarketReportParser` + `TaifexClient` POST method + `FuturesMarketOiService` + `TaifexMarketOiCollector` follow the exact same layering as the existing `MarginTransactionService`/`MarginCollector` pair. `DashboardService` combines this total OI with the existing (already-collected) three-trader-type OI to compute the ratio; `dashboard.html` renders it with the same bar+line pattern as the 融資變化 panel.

**Tech Stack:** Spring Boot, Spring Data JPA, Jsoup (HTML parsing), JUnit 5 + Mockito + AssertJ, Thymeleaf + Chart.js.

**Reference spec:** `docs/superpowers/specs/2026-07-17-mtx-tmf-retail-ratio-design.md`

---

### Task 1: `FuturesMarketOi` entity + repository

**Files:**
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/FuturesMarketOi.java`
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/repository/FuturesMarketOiRepository.java`

This is a plain data-holder entity (no trader-type breakdown), following the exact shape of `MarginTransaction`/`MarginTransactionRepository`. No dedicated test — it's exercised indirectly by the service test in Task 4, matching how other simple entities in this codebase are handled.

- [ ] **Step 1: Create the entity**

```java
package com.eagleeye.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(
    name = "futures_market_oi",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_futures_market_oi",
        columnNames = {"trade_date", "contract"}
    )
)
public class FuturesMarketOi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "contract", nullable = false, length = 10)
    private String contract;

    @Column(name = "total_oi")
    private Long totalOi;

    protected FuturesMarketOi() {}

    public FuturesMarketOi(LocalDate tradeDate, String contract) {
        this.tradeDate = tradeDate;
        this.contract = contract;
    }

    public Long getId() { return id; }
    public LocalDate getTradeDate() { return tradeDate; }
    public String getContract() { return contract; }
    public Long getTotalOi() { return totalOi; }

    public void setTotalOi(Long totalOi) { this.totalOi = totalOi; }
}
```

- [ ] **Step 2: Create the repository**

```java
package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.FuturesMarketOi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FuturesMarketOiRepository extends JpaRepository<FuturesMarketOi, Long> {

    Optional<FuturesMarketOi> findByTradeDateAndContract(LocalDate tradeDate, String contract);

    List<FuturesMarketOi> findByContractAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, LocalDate from, LocalDate to);
}
```

- [ ] **Step 3: Compile to verify**

Run: `mvn compile -pl eagleeye-domain -am -q`
Expected: BUILD SUCCESS, no output.

- [ ] **Step 4: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/entity/FuturesMarketOi.java eagleeye-domain/src/main/java/com/eagleeye/domain/repository/FuturesMarketOiRepository.java
git commit -m "feat(domain): add FuturesMarketOi entity for total market open interest"
```

---

### Task 2: `TaifexClient` — POST-based fetch for the daily market report

**Files:**
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TaifexClient.java`

No dedicated test file exists for `TaifexClient` today (it's a thin `RestClient` wrapper, exercised only indirectly via service-level Mockito mocks — same as `TwseClient`), so this task has no test step. Correctness of the request shape was verified against live TAIFEX data before writing this plan (see spec §3.1): `POST /enl/eng3/futDailyMarketReport` with body `queryType=2&marketCode=0&dateaddcnt=&commodity_id=<contract>&commodity_id2=&queryDate=<yyyy/MM/dd>`.

- [ ] **Step 1: Add the new path constant, imports, and method**

Modify `eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TaifexClient.java`.

Find:
```java
package com.eagleeye.collector.taifex;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class TaifexClient {

    private static final String BASE_URL        = "https://www.taifex.com.tw";
    private static final String FUTURES_PATH    = "/enl/eng3/futContractsDate";
    private static final String FUTURES_AH_PATH = "/enl/eng3/futContractsDateAh";
    private static final String OPTIONS_PATH    = "/enl/eng3/optContractsDate";
    private static final String OPTIONS_CALL_PUT_PATH = "/enl/eng3/callsAndPutsDate";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
```

Replace with:
```java
package com.eagleeye.collector.taifex;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class TaifexClient {

    private static final String BASE_URL        = "https://www.taifex.com.tw";
    private static final String FUTURES_PATH    = "/enl/eng3/futContractsDate";
    private static final String FUTURES_AH_PATH = "/enl/eng3/futContractsDateAh";
    private static final String OPTIONS_PATH    = "/enl/eng3/optContractsDate";
    private static final String OPTIONS_CALL_PUT_PATH = "/enl/eng3/callsAndPutsDate";
    private static final String DAILY_MARKET_REPORT_PATH = "/enl/eng3/futDailyMarketReport";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
```

Find:
```java
    public String fetchOptionsCallPutHtml(LocalDate date) {
        return fetch(OPTIONS_CALL_PUT_PATH, date);
    }

    private String fetch(String path, LocalDate date) {
```

Replace with:
```java
    public String fetchOptionsCallPutHtml(LocalDate date) {
        return fetch(OPTIONS_CALL_PUT_PATH, date);
    }

    /**
     * Fetches TAIFEX's daily market report (期貨每日交易行情) for one contract —
     * total open interest per contract-month, independent of trader type. Unlike
     * the other reports here, this one requires a POST with form-encoded body
     * (verified against production TAIFEX; a GET with equivalent query params
     * returns "No Data").
     */
    public String fetchDailyMarketReportHtml(LocalDate date, String contract) {
        String queryDate = date.format(DATE_FORMAT);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("queryType", "2");
        form.add("marketCode", "0");
        form.add("dateaddcnt", "");
        form.add("commodity_id", contract);
        form.add("commodity_id2", "");
        form.add("queryDate", queryDate);

        return restClient.post()
                .uri(DAILY_MARKET_REPORT_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
    }

    private String fetch(String path, LocalDate date) {
```

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -pl eagleeye-collector -am -q`
Expected: BUILD SUCCESS, no output.

- [ ] **Step 3: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TaifexClient.java
git commit -m "feat(collector): add POST fetch for TAIFEX daily market report"
```

---

### Task 3: `TaifexMarketReportParser`

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TaifexMarketReportParser.java`
- Test: `eagleeye-collector/src/test/java/com/eagleeye/collector/taifex/TaifexMarketReportParserTest.java`

Parses the "Subtotal:" row's Open Interest cell (verified index 12 of 17 `<td>` cells against live TAIFEX HTML — see spec §3.2/§3.3).

- [ ] **Step 1: Write the failing test**

```java
package com.eagleeye.collector.taifex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TaifexMarketReportParserTest {

    private TaifexMarketReportParser parser;
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 7, 16);

    @BeforeEach
    void setUp() {
        parser = new TaifexMarketReportParser();
    }

    @Test
    void parseTotalOi_extractsSubtotalOpenInterest() {
        String html = buildReport(
                dataRow("MTX", "202607W4", "44"),
                dataRow("MTX", "202607W5", "2"),
                dataRow("MTX", "202608", "30580"),
                subtotalRow("35655")
        );

        Long totalOi = parser.parseTotalOi(html, TEST_DATE, "MTX");

        assertThat(totalOi).isEqualTo(35655L);
    }

    @Test
    void parseTotalOi_stripsThousandsSeparators() {
        String html = buildReport(
                dataRow("TMF", "202608", "59759"),
                subtotalRow("1,234,567")
        );

        Long totalOi = parser.parseTotalOi(html, TEST_DATE, "TMF");

        assertThat(totalOi).isEqualTo(1_234_567L);
    }

    @Test
    void parseTotalOi_returnsNull_whenNoSubtotalRow() {
        String html = buildReport(dataRow("MTX", "202608", "30580"));

        assertThat(parser.parseTotalOi(html, TEST_DATE, "MTX")).isNull();
    }

    @Test
    void parseTotalOi_returnsNull_whenNoTableFound() {
        assertThat(parser.parseTotalOi("<html><body>No Data</body></html>", TEST_DATE, "MTX")).isNull();
    }

    @Test
    void isNoDataPage_true_whenEnglishMarker() {
        assertThat(parser.isNoDataPage("<html><body>No Data</body></html>")).isTrue();
    }

    @Test
    void isNoDataPage_true_whenChineseMarker() {
        assertThat(parser.isNoDataPage("<html><body>查無資料</body></html>")).isTrue();
    }

    @Test
    void isNoDataPage_false_forNormalHtml() {
        assertThat(parser.isNoDataPage("<html><body><table class='table_f'></table></body></html>")).isFalse();
    }

    // -----------------------------------------------------------------------
    // HTML builders — mirror the real report's 17-cell row shape (verified live);
    // only Contract, Contract Month, and Open Interest (index 12) vary per test.
    // -----------------------------------------------------------------------

    private String buildReport(String... rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><table class='table_f table-fixed w-1000'><tbody>");
        for (String row : rows) sb.append(row);
        sb.append("</tbody></table></body></html>");
        return sb.toString();
    }

    private String dataRow(String contract, String month, String openInterest) {
        StringBuilder sb = new StringBuilder("<tr>");
        sb.append("<td>").append(contract).append("</td>");
        sb.append("<td>").append(month).append("</td>");
        for (int i = 0; i < 9; i++) sb.append("<td>0</td>"); // Open/High/Low/Last/Change/%/VolAH/VolReg/VolTotal
        sb.append("<td>0</td>");                              // Settlement Price
        sb.append("<td>").append(openInterest).append("</td>"); // Open Interest (index 12)
        for (int i = 0; i < 4; i++) sb.append("<td>0</td>"); // BestBid/BestAsk/HistHigh/HistLow
        sb.append("</tr>");
        return sb.toString();
    }

    private String subtotalRow(String openInterestSubtotal) {
        StringBuilder sb = new StringBuilder("<tr>");
        for (int i = 0; i < 7; i++) sb.append("<td>&nbsp;</td>");
        sb.append("<td>Subtotal:</td>");
        sb.append("<td>0</td><td>0</td><td>0</td>"); // volume subtotals
        sb.append("<td>&nbsp;</td>");                 // settlement price (blank)
        sb.append("<td>").append(openInterestSubtotal).append("</td>"); // OI subtotal (index 12)
        for (int i = 0; i < 4; i++) sb.append("<td>&nbsp;</td>");
        sb.append("</tr>");
        return sb.toString();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl eagleeye-collector -am -Dtest=TaifexMarketReportParserTest -q`
Expected: FAIL — compile error, `TaifexMarketReportParser` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.eagleeye.collector.taifex;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Parses TAIFEX's daily market report (期貨每日交易行情) — total open interest
 * per contract across all trader types, summed across contract months via the
 * report's own "Subtotal:" row. Distinct report/row shape from {@link TaifexParser},
 * which handles the three-major-institutional-investors tables.
 */
@Component
public class TaifexMarketReportParser {

    private static final Logger log = LoggerFactory.getLogger(TaifexMarketReportParser.class);

    // 17-cell row: ... [11] Settlement Price [12] Open Interest [13] Best Bid ...
    private static final int OPEN_INTEREST_COLUMN = 12;

    public boolean isNoDataPage(String html) {
        return html != null && (html.contains("No Data") || html.contains("查無資料"));
    }

    /**
     * Returns the total open interest across all contract months for {@code contract}
     * on {@code tradeDate} — the report's "Subtotal:" row, Open Interest column — or
     * {@code null} if the table or that row can't be found.
     */
    public Long parseTotalOi(String html, LocalDate tradeDate, String contract) {
        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("table.table_f");
        if (table == null) {
            log.warn("Could not find data table in TAIFEX daily market report HTML for {}/{}", contract, tradeDate);
            return null;
        }

        for (Element row : table.select("tr")) {
            Elements cells = row.select("td");
            boolean isSubtotal = cells.stream().anyMatch(c -> c.text().trim().equals("Subtotal:"));
            if (!isSubtotal) continue;

            if (cells.size() <= OPEN_INTEREST_COLUMN) {
                log.warn("Subtotal row has too few cells ({}) for {}/{}", cells.size(), contract, tradeDate);
                return null;
            }
            String text = cells.get(OPEN_INTEREST_COLUMN).text().trim().replace(",", "");
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                log.warn("Could not parse Open Interest subtotal '{}' for {}/{}", text, contract, tradeDate);
                return null;
            }
        }

        log.warn("No Subtotal row found in TAIFEX daily market report for {}/{}", contract, tradeDate);
        return null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl eagleeye-collector -am -Dtest=TaifexMarketReportParserTest -q`
Expected: PASS — 7 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/taifex/TaifexMarketReportParser.java eagleeye-collector/src/test/java/com/eagleeye/collector/taifex/TaifexMarketReportParserTest.java
git commit -m "feat(collector): add TaifexMarketReportParser for total market OI"
```

---

### Task 4: `FuturesMarketOiService`

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/service/FuturesMarketOiService.java`
- Test: `eagleeye-collector/src/test/java/com/eagleeye/collector/service/FuturesMarketOiServiceTest.java`

Loops over `MTX` and `TMF`, upserting each independently, reusing the existing `DateCollectionResult` sealed type (same shape as `MarginTransactionService`).

- [ ] **Step 1: Write the failing test**

```java
package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TaifexClient;
import com.eagleeye.collector.taifex.TaifexMarketReportParser;
import com.eagleeye.domain.entity.FuturesMarketOi;
import com.eagleeye.domain.repository.FuturesMarketOiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuturesMarketOiServiceTest {

    @Mock private TaifexClient client;
    @Mock private TaifexMarketReportParser parser;
    @Mock private FuturesMarketOiRepository repository;

    private FuturesMarketOiService service;

    private static final LocalDate DATE = LocalDate.of(2026, 7, 16);

    @BeforeEach
    void setUp() {
        service = new FuturesMarketOiService(client, parser, repository);
    }

    @Test
    void collectDate_bothContractsPresent_savesEachAndReturnsCollected() {
        when(client.fetchDailyMarketReportHtml(DATE, "MTX")).thenReturn("<html>mtx</html>");
        when(client.fetchDailyMarketReportHtml(DATE, "TMF")).thenReturn("<html>tmf</html>");
        when(parser.isNoDataPage(any())).thenReturn(false);
        when(parser.parseTotalOi("<html>mtx</html>", DATE, "MTX")).thenReturn(35655L);
        when(parser.parseTotalOi("<html>tmf</html>", DATE, "TMF")).thenReturn(74882L);
        when(repository.findByTradeDateAndContract(eq(DATE), any())).thenReturn(Optional.empty());

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
        assertThat(result.tradeDate()).isEqualTo(DATE);

        ArgumentCaptor<FuturesMarketOi> captor = ArgumentCaptor.forClass(FuturesMarketOi.class);
        verify(repository, times(2)).save(captor.capture());
        List<FuturesMarketOi> saved = captor.getAllValues();
        assertThat(saved).extracting(FuturesMarketOi::getContract).containsExactlyInAnyOrder("MTX", "TMF");
        FuturesMarketOi mtx = saved.stream().filter(o -> o.getContract().equals("MTX")).findFirst().orElseThrow();
        assertThat(mtx.getTotalOi()).isEqualTo(35655L);
    }

    @Test
    void collectDate_bothContractsNoData_returnsNoData() {
        when(client.fetchDailyMarketReportHtml(eq(DATE), any())).thenReturn("<html>No Data</html>");
        when(parser.isNoDataPage(any())).thenReturn(true);

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.NoData.class);
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_oneContractNoData_stillCollectsTheOther() {
        when(client.fetchDailyMarketReportHtml(DATE, "MTX")).thenReturn("<html>mtx</html>");
        when(client.fetchDailyMarketReportHtml(DATE, "TMF")).thenReturn("<html>No Data</html>");
        when(parser.isNoDataPage("<html>mtx</html>")).thenReturn(false);
        when(parser.isNoDataPage("<html>No Data</html>")).thenReturn(true);
        when(parser.parseTotalOi("<html>mtx</html>", DATE, "MTX")).thenReturn(35655L);
        when(repository.findByTradeDateAndContract(DATE, "MTX")).thenReturn(Optional.empty());

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Collected.class);
        verify(repository, times(1)).save(any());
    }

    @Test
    void collectDate_clientThrows_returnsError() {
        when(client.fetchDailyMarketReportHtml(eq(DATE), any())).thenThrow(new RuntimeException("timeout"));

        DateCollectionResult result = service.collectDate(DATE);

        assertThat(result).isInstanceOf(DateCollectionResult.Error.class);
        DateCollectionResult.Error error = (DateCollectionResult.Error) result;
        assertThat(error.message()).contains("timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_existingRecord_upserts() {
        FuturesMarketOi existing = new FuturesMarketOi(DATE, "MTX");
        when(client.fetchDailyMarketReportHtml(DATE, "MTX")).thenReturn("<html>mtx</html>");
        when(client.fetchDailyMarketReportHtml(DATE, "TMF")).thenReturn("<html>tmf</html>");
        when(parser.isNoDataPage(any())).thenReturn(false);
        when(parser.parseTotalOi("<html>mtx</html>", DATE, "MTX")).thenReturn(40000L);
        when(parser.parseTotalOi("<html>tmf</html>", DATE, "TMF")).thenReturn(75000L);
        when(repository.findByTradeDateAndContract(DATE, "MTX")).thenReturn(Optional.of(existing));
        when(repository.findByTradeDateAndContract(DATE, "TMF")).thenReturn(Optional.empty());

        service.collectDate(DATE);

        ArgumentCaptor<FuturesMarketOi> captor = ArgumentCaptor.forClass(FuturesMarketOi.class);
        verify(repository, times(2)).save(captor.capture());
        FuturesMarketOi mtx = captor.getAllValues().stream()
                .filter(o -> o.getContract().equals("MTX")).findFirst().orElseThrow();
        assertThat(mtx.getTotalOi()).isEqualTo(40000L);
        assertThat(mtx).isSameAs(existing);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl eagleeye-collector -am -Dtest=FuturesMarketOiServiceTest -q`
Expected: FAIL — compile error, `FuturesMarketOiService` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.eagleeye.collector.service;

import com.eagleeye.collector.taifex.TaifexClient;
import com.eagleeye.collector.taifex.TaifexMarketReportParser;
import com.eagleeye.domain.entity.FuturesMarketOi;
import com.eagleeye.domain.repository.FuturesMarketOiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class FuturesMarketOiService {

    private static final Logger log = LoggerFactory.getLogger(FuturesMarketOiService.class);
    private static final List<String> CONTRACTS = List.of("MTX", "TMF");

    private final TaifexClient client;
    private final TaifexMarketReportParser parser;
    private final FuturesMarketOiRepository repository;

    public FuturesMarketOiService(TaifexClient client, TaifexMarketReportParser parser,
                                   FuturesMarketOiRepository repository) {
        this.client = client;
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public DateCollectionResult collectDate(LocalDate date) {
        try {
            int collected = 0;
            for (String contract : CONTRACTS) {
                String html = client.fetchDailyMarketReportHtml(date, contract);
                if (parser.isNoDataPage(html)) {
                    log.info("No daily market report data for {}/{}", contract, date);
                    continue;
                }
                Long totalOi = parser.parseTotalOi(html, date, contract);
                if (totalOi == null) continue;
                upsert(date, contract, totalOi);
                collected++;
            }
            if (collected == 0) {
                log.info("No market OI data for {}", date);
                return new DateCollectionResult.NoData(date);
            }
            log.info("Collected market OI for {} ({} of {} contracts)", date, collected, CONTRACTS.size());
            return new DateCollectionResult.Collected(date);
        } catch (Exception e) {
            log.error("Market OI collection failed for {}: {}", date, e.getMessage(), e);
            return new DateCollectionResult.Error(date, e.getMessage());
        }
    }

    private void upsert(LocalDate date, String contract, Long totalOi) {
        var existing = repository.findByTradeDateAndContract(date, contract);
        FuturesMarketOi entity = existing.orElseGet(() -> new FuturesMarketOi(date, contract));
        entity.setTotalOi(totalOi);
        repository.save(entity);
        log.info("{} market OI for {}/{}: {}", existing.isPresent() ? "Updated" : "Inserted", contract, date, totalOi);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl eagleeye-collector -am -Dtest=FuturesMarketOiServiceTest -q`
Expected: PASS — 5 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/service/FuturesMarketOiService.java eagleeye-collector/src/test/java/com/eagleeye/collector/service/FuturesMarketOiServiceTest.java
git commit -m "feat(collector): add FuturesMarketOiService to collect MTX/TMF total OI"
```

---

### Task 5: `TaifexMarketOiCollector` + `ScheduledCollectorTest`

**Files:**
- Create: `eagleeye-collector/src/main/java/com/eagleeye/collector/collector/TaifexMarketOiCollector.java`
- Modify: `eagleeye-collector/src/test/java/com/eagleeye/collector/collector/ScheduledCollectorTest.java`

Pure adapter, mirrors `MarginCollector` exactly. Dispatch key: `"MKTOI"`.

- [ ] **Step 1: Write the failing test**

Modify `eagleeye-collector/src/test/java/com/eagleeye/collector/collector/ScheduledCollectorTest.java`.

Find:
```java
    @Mock MarketIndexService       marketIndexService;
    @Mock InstitutionalFlowService institutionalFlowService;
    @Mock CollectionService        collectionService;
    @Mock MarginTransactionService marginTransactionService;
```

Replace with:
```java
    @Mock MarketIndexService       marketIndexService;
    @Mock InstitutionalFlowService institutionalFlowService;
    @Mock CollectionService        collectionService;
    @Mock MarginTransactionService marginTransactionService;
    @Mock FuturesMarketOiService   futuresMarketOiService;
```

Find:
```java
    @Test
    void margin_error_returnsError() {
        when(marginTransactionService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Error(DATE, "HTTP 503"));

        CollectorOutcome result = new MarginCollector(marginTransactionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("HTTP 503");
    }
}
```

Replace with:
```java
    @Test
    void margin_error_returnsError() {
        when(marginTransactionService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Error(DATE, "HTTP 503"));

        CollectorOutcome result = new MarginCollector(marginTransactionService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("HTTP 503");
    }

    // ── TaifexMarketOiCollector ───────────────────────────────────────────────

    @Test
    void marketOi_collected_returnsOk() {
        when(futuresMarketOiService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Collected(DATE));

        CollectorOutcome result = new TaifexMarketOiCollector(futuresMarketOiService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.detail()).isEqualTo("ok");
    }

    @Test
    void marketOi_noData_returnsNoData() {
        when(futuresMarketOiService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.NoData(DATE));

        assertThat(new TaifexMarketOiCollector(futuresMarketOiService).collect(DATE).status())
                .isEqualTo(CollectionStatus.NO_DATA);
    }

    @Test
    void marketOi_error_returnsError() {
        when(futuresMarketOiService.collectDate(DATE))
                .thenReturn(new DateCollectionResult.Error(DATE, "parse failed"));

        CollectorOutcome result = new TaifexMarketOiCollector(futuresMarketOiService).collect(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.detail()).contains("parse failed");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl eagleeye-collector -am -Dtest=ScheduledCollectorTest -q`
Expected: FAIL — compile error, `TaifexMarketOiCollector` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.eagleeye.collector.collector;

import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.FuturesMarketOiService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TaifexMarketOiCollector implements ScheduledCollector {

    private final FuturesMarketOiService service;

    public TaifexMarketOiCollector(FuturesMarketOiService service) {
        this.service = service;
    }

    @Override public String name() { return "MKTOI"; }

    @Override
    public CollectorOutcome collect(LocalDate date) {
        var result = service.collectDate(date);
        return switch (result) {
            case DateCollectionResult.Collected c -> CollectorOutcome.collected("ok");
            case DateCollectionResult.NoData n    -> CollectorOutcome.noData();
            case DateCollectionResult.Error e     -> CollectorOutcome.error(e.message());
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl eagleeye-collector -am -Dtest=ScheduledCollectorTest -q`
Expected: PASS — all tests including the 3 new `marketOi_*` cases.

- [ ] **Step 5: Commit**

```bash
git add eagleeye-collector/src/main/java/com/eagleeye/collector/collector/TaifexMarketOiCollector.java eagleeye-collector/src/test/java/com/eagleeye/collector/collector/ScheduledCollectorTest.java
git commit -m "feat(collector): add TaifexMarketOiCollector (dispatch key MKTOI)"
```

---

### Task 6: Deployment — plist, install.sh, backfill

**Files:**
- Create: `deploy/com.eagleeye.collector.mktoi.plist`
- Modify: `deploy/install.sh`
- Modify: `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CombinedBackfillRunner.java`

New launchd job at 15:35 Asia/Taipei (5 minutes after the existing 15:30 TAIFEX job). Also wires the new service into the combined backfill sequence so historical data populates instead of the panels starting empty.

- [ ] **Step 1: Create the plist**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<!--
    MKTOI collector — TAIFEX daily market report, total open interest (未沖銷契約量)
    for MTX/TMF, published on the same ~15:00 schedule as the institutional report.
    launchd StartCalendarInterval uses local time (Asia/Taipei), NOT UTC.
-->
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.eagleeye.collector.mktoi</string>

    <key>ProgramArguments</key>
    <array>
        <string>/usr/local/opt/openjdk@25/bin/java</string>
        <string>--enable-native-access=ALL-UNNAMED</string>
        <!-- Class Data Sharing: speeds startup ~18% for this short-lived process. -->
        <string>-XX:+AutoCreateSharedArchive</string>
        <string>-XX:SharedArchiveFile=/opt/eagleeye/collector/collector.jsa</string>
        <string>-Xlog:cds=off</string>
        <string>-jar</string>
        <string>/opt/eagleeye/collector/eagleeye-collector.jar</string>
        <string>--spring.profiles.active=prod</string>
        <string>--logging.level.root=WARN</string>
        <string>--logging.level.com.eagleeye=INFO</string>
        <string>--collector=MKTOI</string>
    </array>

    <key>StartCalendarInterval</key>
    <array>
        <dict><key>Weekday</key><integer>1</integer><key>Hour</key><integer>15</integer><key>Minute</key><integer>35</integer></dict>
        <dict><key>Weekday</key><integer>2</integer><key>Hour</key><integer>15</integer><key>Minute</key><integer>35</integer></dict>
        <dict><key>Weekday</key><integer>3</integer><key>Hour</key><integer>15</integer><key>Minute</key><integer>35</integer></dict>
        <dict><key>Weekday</key><integer>4</integer><key>Hour</key><integer>15</integer><key>Minute</key><integer>35</integer></dict>
        <dict><key>Weekday</key><integer>5</integer><key>Hour</key><integer>15</integer><key>Minute</key><integer>35</integer></dict>
    </array>

    <key>StandardOutPath</key>
    <string>/opt/eagleeye/logs/collector-mktoi.log</string>
    <key>StandardErrorPath</key>
    <string>/opt/eagleeye/logs/collector-mktoi-error.log</string>

    <key>RunAtLoad</key>
    <false/>

    <key>EnvironmentVariables</key>
    <dict>
        <key>JAVA_HOME</key>
        <string>/usr/local/opt/openjdk@25</string>
    </dict>
</dict>
</plist>
```

- [ ] **Step 2: Add `mktoi` to `install.sh`'s collector array and schedule summary**

Modify `deploy/install.sh`.

Find:
```bash
# Collectors, each its own launchd job (name-addressed via --collector=NAME).
COLLECTORS=(futah taiex iflow taifex margin txtick)
```

Replace with:
```bash
# Collectors, each its own launchd job (name-addressed via --collector=NAME).
COLLECTORS=(futah taiex iflow taifex mktoi margin txtick)
```

Find:
```bash
echo "    15:30  TAIFEX  TAIFEX OI           (未平倉口數及契約金額)"
echo "    21:35  MARGIN  margin transactions (融資融券)"
```

Replace with:
```bash
echo "    15:30  TAIFEX  TAIFEX OI           (未平倉口數及契約金額)"
echo "    15:35  MKTOI   total market OI     (期貨每日交易行情, MTX/TMF)"
echo "    21:35  MARGIN  margin transactions (融資融券)"
```

- [ ] **Step 3: Wire `FuturesMarketOiService` into `CombinedBackfillRunner`**

Modify `eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CombinedBackfillRunner.java`.

Find:
```java
import com.eagleeye.collector.service.FuturesOptionsCollectionResult;
import com.eagleeye.collector.service.CollectionService;
import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
```

Replace with:
```java
import com.eagleeye.collector.service.FuturesOptionsCollectionResult;
import com.eagleeye.collector.service.CollectionService;
import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.FuturesMarketOiService;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
```

Find:
```java
    private final MarketIndexService marketIndexService;
    private final CollectionService collectionService;
    private final MarginTransactionService marginTransactionService;
    private final InstitutionalFlowService institutionalFlowService;
    private final ApplicationContext applicationContext;
    private final long requestDelayMs;

    @Autowired
    public CombinedBackfillRunner(MarketIndexService marketIndexService,
                                  CollectionService collectionService,
                                  ApplicationContext applicationContext,
                                  MarginTransactionService marginTransactionService,
                                  InstitutionalFlowService institutionalFlowService) {
        this(marketIndexService, collectionService, applicationContext,
                marginTransactionService, institutionalFlowService, 500);
    }

    CombinedBackfillRunner(MarketIndexService marketIndexService,
                           CollectionService collectionService,
                           ApplicationContext applicationContext,
                           MarginTransactionService marginTransactionService,
                           InstitutionalFlowService institutionalFlowService,
                           long requestDelayMs) {
        this.marketIndexService = marketIndexService;
        this.collectionService = collectionService;
        this.marginTransactionService = marginTransactionService;
        this.institutionalFlowService = institutionalFlowService;
        this.applicationContext = applicationContext;
        this.requestDelayMs = requestDelayMs;
    }
```

Replace with:
```java
    private final MarketIndexService marketIndexService;
    private final CollectionService collectionService;
    private final MarginTransactionService marginTransactionService;
    private final InstitutionalFlowService institutionalFlowService;
    private final FuturesMarketOiService futuresMarketOiService;
    private final ApplicationContext applicationContext;
    private final long requestDelayMs;

    @Autowired
    public CombinedBackfillRunner(MarketIndexService marketIndexService,
                                  CollectionService collectionService,
                                  ApplicationContext applicationContext,
                                  MarginTransactionService marginTransactionService,
                                  InstitutionalFlowService institutionalFlowService,
                                  FuturesMarketOiService futuresMarketOiService) {
        this(marketIndexService, collectionService, applicationContext,
                marginTransactionService, institutionalFlowService, futuresMarketOiService, 500);
    }

    CombinedBackfillRunner(MarketIndexService marketIndexService,
                           CollectionService collectionService,
                           ApplicationContext applicationContext,
                           MarginTransactionService marginTransactionService,
                           InstitutionalFlowService institutionalFlowService,
                           FuturesMarketOiService futuresMarketOiService,
                           long requestDelayMs) {
        this.marketIndexService = marketIndexService;
        this.collectionService = collectionService;
        this.marginTransactionService = marginTransactionService;
        this.institutionalFlowService = institutionalFlowService;
        this.futuresMarketOiService = futuresMarketOiService;
        this.applicationContext = applicationContext;
        this.requestDelayMs = requestDelayMs;
    }
```

Find:
```java
                    DateCollectionResult flowResult = institutionalFlowService.collectDate(day);
                    printDateResult("IFLOW ", day, flowResult);
                    Thread.sleep(requestDelayMs);
                }
                day = day.plusDays(1);
```

Replace with:
```java
                    DateCollectionResult flowResult = institutionalFlowService.collectDate(day);
                    printDateResult("IFLOW ", day, flowResult);
                    Thread.sleep(requestDelayMs);

                    DateCollectionResult mktoiResult = futuresMarketOiService.collectDate(day);
                    printDateResult("MKTOI ", day, mktoiResult);
                    Thread.sleep(requestDelayMs);
                }
                day = day.plusDays(1);
```

- [ ] **Step 4: Update `CombinedBackfillRunnerTest`**

`eagleeye-collector/src/test/java/com/eagleeye/collector/runner/CombinedBackfillRunnerTest.java` constructs `CombinedBackfillRunner` directly and every one of its 9 test methods calls `stubMarketIndex(...); stubTaifex(); stubMargin(); stubFlow();` before invoking `runner.executeBackfill(...)`. Without a stub, the new `futuresMarketOiService.collectDate(day)` call added in Task 6 Step 3 returns `null` from the mock, and `printDateResult`'s `switch` on that `null` throws `NullPointerException`. Add a mock, wire it into the constructor, and stub it everywhere `stubFlow()` is stubbed.

Find:
```java
import com.eagleeye.collector.service.FuturesOptionsCollectionResult;
import com.eagleeye.collector.service.CollectionService;
import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
```

Replace with:
```java
import com.eagleeye.collector.service.FuturesOptionsCollectionResult;
import com.eagleeye.collector.service.CollectionService;
import com.eagleeye.collector.service.DateCollectionResult;
import com.eagleeye.collector.service.FuturesMarketOiService;
import com.eagleeye.collector.service.InstitutionalFlowService;
import com.eagleeye.collector.service.MarginTransactionService;
import com.eagleeye.collector.service.MarketIndexCollectionResult;
import com.eagleeye.collector.service.MarketIndexService;
```

Find:
```java
    @Mock private MarketIndexService marketIndexService;
    @Mock private CollectionService collectionService;
    @Mock private MarginTransactionService marginTransactionService;
    @Mock private InstitutionalFlowService institutionalFlowService;

    private CombinedBackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new CombinedBackfillRunner(marketIndexService, collectionService, null,
                marginTransactionService, institutionalFlowService, 0);
    }
```

Replace with:
```java
    @Mock private MarketIndexService marketIndexService;
    @Mock private CollectionService collectionService;
    @Mock private MarginTransactionService marginTransactionService;
    @Mock private InstitutionalFlowService institutionalFlowService;
    @Mock private FuturesMarketOiService futuresMarketOiService;

    private CombinedBackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new CombinedBackfillRunner(marketIndexService, collectionService, null,
                marginTransactionService, institutionalFlowService, futuresMarketOiService, 0);
    }
```

Find (this exact two-line sequence is identical across all 9 test methods — use a single replace-all):
```java
        stubMargin();
        stubFlow();
```

Replace all occurrences with:
```java
        stubMargin();
        stubFlow();
        stubMktoi();
```

Find:
```java
    private void stubFlow() {
        when(institutionalFlowService.collectDate(any(LocalDate.class)))
                .thenReturn(new DateCollectionResult.Collected(LocalDate.now()));
    }
}
```

Replace with:
```java
    private void stubFlow() {
        when(institutionalFlowService.collectDate(any(LocalDate.class)))
                .thenReturn(new DateCollectionResult.Collected(LocalDate.now()));
    }

    private void stubMktoi() {
        when(futuresMarketOiService.collectDate(any(LocalDate.class)))
                .thenReturn(new DateCollectionResult.Collected(LocalDate.now()));
    }
}
```

- [ ] **Step 5: Compile and run collector tests**

Run: `mvn test -pl eagleeye-collector -am -q`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add deploy/com.eagleeye.collector.mktoi.plist deploy/install.sh eagleeye-collector/src/main/java/com/eagleeye/collector/runner/CombinedBackfillRunner.java
git add -u eagleeye-collector/src/test
git commit -m "feat(deploy): wire MKTOI collector into launchd, install.sh, and backfill"
```

---

### Task 7: `DashboardViewModel` — add ratio fields

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardViewModel.java`
- Modify: `eagleeye-web/src/test/java/com/eagleeye/web/DashboardControllerTest.java`

- [ ] **Step 1: Add the two new fields**

Find:
```java
public record DashboardViewModel(
        List<String> isoDates,
        List<String> dateLabels,
        List<Double> taiexClose,
        List<Long>   spotNetFlow,
        List<Long>   marginChange,
        List<Long>   futuresLongOI,
        List<Long>   futuresShortOI,
        List<Long>   optionsCallOI,
        List<Long>   optionsPutOI,
        List<Long>   optionsCallNetValue,
        List<Long>   optionsPutNetValue,
        List<Long>   futuresAhLong,
        List<Long>   futuresAhShort,
        List<Long>   futuresAhNet,
        int          days
) {}
```

Replace with:
```java
public record DashboardViewModel(
        List<String> isoDates,
        List<String> dateLabels,
        List<Double> taiexClose,
        List<Long>   spotNetFlow,
        List<Long>   marginChange,
        List<Long>   futuresLongOI,
        List<Long>   futuresShortOI,
        List<Long>   optionsCallOI,
        List<Long>   optionsPutOI,
        List<Long>   optionsCallNetValue,
        List<Long>   optionsPutNetValue,
        List<Long>   futuresAhLong,
        List<Long>   futuresAhShort,
        List<Long>   futuresAhNet,
        List<Double> mtxRatio,
        List<Double> tmfRatio,
        int          days
) {}
```

- [ ] **Step 2: Fix `DashboardControllerTest`'s manual `DashboardViewModel` construction**

`DashboardControllerTest.emptyVm(int days)` builds a `DashboardViewModel` positionally with one `List.of()` per list field. It must gain 2 more to match the new record shape.

Find:
```java
    DashboardViewModel emptyVm(int days) {
        return new DashboardViewModel(
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), days);
    }
```

Replace with:
```java
    DashboardViewModel emptyVm(int days) {
        return new DashboardViewModel(
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), days);
    }
```

- [ ] **Step 3: Compile to verify**

Run: `mvn compile -pl eagleeye-web -am -q`
Expected: FAIL — `DashboardService` no longer matches the record's constructor (fixed in Task 8; this confirms the field was added and is now load-bearing).

- [ ] **Step 4: Commit only after Task 8 is complete** — do not commit yet (this module won't compile standalone until `DashboardService` is updated in Task 8). Continue directly to Task 8; both are committed together there.

---

### Task 8: `DashboardService` — compute the ratio

**Files:**
- Modify: `eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java`
- Modify: `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java`

Per date, for each of MTX/TMF: sum OI long/short across **all** trader types present (dealer+trust+FINI, missing types contribute 0) using the existing trader-type-agnostic repository method, then combine with `FuturesMarketOi.totalOi` to compute the ratio (spec §4). `mtxRatio`/`tmfRatio` are `null` for a date when `totalOi` is missing.

- [ ] **Step 1: Write the failing tests**

Modify `eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java`.

Find:
```java
    @Mock TaiexIndexRepository taiexRepo;
    @Mock InstitutionalFlowRepository flowRepo;
    @Mock FuturesPositionRepository futuresRepo;
    @Mock FuturesAhPositionRepository futuresAhRepo;
    @Mock OptionsPositionRepository optionsRepo;
    @Mock OptionsCallPutPositionRepository callPutRepo;
    @Mock MarginTransactionRepository marginRepo;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(taiexRepo, flowRepo, futuresRepo, futuresAhRepo, optionsRepo, callPutRepo, marginRepo);
    }
```

Replace with:
```java
    @Mock TaiexIndexRepository taiexRepo;
    @Mock InstitutionalFlowRepository flowRepo;
    @Mock FuturesPositionRepository futuresRepo;
    @Mock FuturesAhPositionRepository futuresAhRepo;
    @Mock OptionsPositionRepository optionsRepo;
    @Mock OptionsCallPutPositionRepository callPutRepo;
    @Mock MarginTransactionRepository marginRepo;
    @Mock FuturesMarketOiRepository futuresMarketOiRepo;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(taiexRepo, flowRepo, futuresRepo, futuresAhRepo, optionsRepo, callPutRepo, marginRepo, futuresMarketOiRepo);
    }
```

Find:
```java
    FuturesPosition futures(LocalDate date, String contract, long oiLong, long oiShort) {
        FuturesPosition fp = new FuturesPosition(date, contract, TraderType.FINI);
        fp.setOiLongVolume(oiLong);
        fp.setOiShortVolume(oiShort);
        fp.setOiNetVolume(oiLong - oiShort);
        return fp;
    }
```

Replace with:
```java
    FuturesPosition futures(LocalDate date, String contract, long oiLong, long oiShort) {
        FuturesPosition fp = new FuturesPosition(date, contract, TraderType.FINI);
        fp.setOiLongVolume(oiLong);
        fp.setOiShortVolume(oiShort);
        fp.setOiNetVolume(oiLong - oiShort);
        return fp;
    }

    FuturesPosition futures(LocalDate date, String contract, TraderType traderType, long oiLong, long oiShort) {
        FuturesPosition fp = new FuturesPosition(date, contract, traderType);
        fp.setOiLongVolume(oiLong);
        fp.setOiShortVolume(oiShort);
        fp.setOiNetVolume(oiLong - oiShort);
        return fp;
    }

    FuturesMarketOi futuresMarketOi(LocalDate date, String contract, long totalOi) {
        FuturesMarketOi oi = new FuturesMarketOi(date, contract);
        oi.setTotalOi(totalOi);
        return oi;
    }
```

Find:
```java
    @Test
    void buildViewModel_computesOptionsOI() {
```

Replace with:
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

    @Test
    void buildViewModel_computesOptionsOI() {
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl eagleeye-web -am -Dtest=DashboardServiceTest -q`
Expected: FAIL — compile error (`DashboardService`'s constructor doesn't yet accept a `FuturesMarketOiRepository` argument, and `DashboardViewModel` doesn't yet have `mtxRatio()`/`tmfRatio()` accessors). Note: `DashboardServiceTest.java`'s existing `import com.eagleeye.domain.entity.*;` and `import com.eagleeye.domain.repository.*;` wildcard imports already cover `FuturesMarketOi`/`FuturesMarketOiRepository` from Task 1 — no import changes needed in this file.

- [ ] **Step 3: Modify `DashboardService`**

Find:
```java
import com.eagleeye.domain.entity.FuturesAhPosition;
import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TaiexIndex;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.FuturesAhPositionRepository;
import com.eagleeye.domain.repository.FuturesPositionRepository;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import com.eagleeye.domain.repository.MarginTransactionRepository;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
import com.eagleeye.domain.repository.OptionsPositionRepository;
import com.eagleeye.domain.repository.TaiexIndexRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
```

Replace with:
```java
import com.eagleeye.domain.entity.FuturesAhPosition;
import com.eagleeye.domain.entity.FuturesMarketOi;
import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.entity.MarginTransaction;
import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TaiexIndex;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.FuturesAhPositionRepository;
import com.eagleeye.domain.repository.FuturesMarketOiRepository;
import com.eagleeye.domain.repository.FuturesPositionRepository;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import com.eagleeye.domain.repository.MarginTransactionRepository;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
import com.eagleeye.domain.repository.OptionsPositionRepository;
import com.eagleeye.domain.repository.TaiexIndexRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
```

Find:
```java
    private final TaiexIndexRepository taiexRepo;
    private final InstitutionalFlowRepository flowRepo;
    private final FuturesPositionRepository futuresRepo;
    private final FuturesAhPositionRepository futuresAhRepo;
    private final OptionsPositionRepository optionsRepo;
    private final OptionsCallPutPositionRepository callPutRepo;
    private final MarginTransactionRepository marginRepo;

    public DashboardService(TaiexIndexRepository taiexRepo,
                            InstitutionalFlowRepository flowRepo,
                            FuturesPositionRepository futuresRepo,
                            FuturesAhPositionRepository futuresAhRepo,
                            OptionsPositionRepository optionsRepo,
                            OptionsCallPutPositionRepository callPutRepo,
                            MarginTransactionRepository marginRepo) {
        this.taiexRepo = taiexRepo;
        this.flowRepo = flowRepo;
        this.futuresRepo = futuresRepo;
        this.futuresAhRepo = futuresAhRepo;
        this.optionsRepo = optionsRepo;
        this.callPutRepo = callPutRepo;
        this.marginRepo = marginRepo;
    }
```

Replace with:
```java
    private final TaiexIndexRepository taiexRepo;
    private final InstitutionalFlowRepository flowRepo;
    private final FuturesPositionRepository futuresRepo;
    private final FuturesAhPositionRepository futuresAhRepo;
    private final OptionsPositionRepository optionsRepo;
    private final OptionsCallPutPositionRepository callPutRepo;
    private final MarginTransactionRepository marginRepo;
    private final FuturesMarketOiRepository futuresMarketOiRepo;

    public DashboardService(TaiexIndexRepository taiexRepo,
                            InstitutionalFlowRepository flowRepo,
                            FuturesPositionRepository futuresRepo,
                            FuturesAhPositionRepository futuresAhRepo,
                            OptionsPositionRepository optionsRepo,
                            OptionsCallPutPositionRepository callPutRepo,
                            MarginTransactionRepository marginRepo,
                            FuturesMarketOiRepository futuresMarketOiRepo) {
        this.taiexRepo = taiexRepo;
        this.flowRepo = flowRepo;
        this.futuresRepo = futuresRepo;
        this.futuresAhRepo = futuresAhRepo;
        this.optionsRepo = optionsRepo;
        this.callPutRepo = callPutRepo;
        this.marginRepo = marginRepo;
        this.futuresMarketOiRepo = futuresMarketOiRepo;
    }
```

Find:
```java
        List<MarginTransaction> marginList  = marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);

        Map<LocalDate, TaiexIndex>        taiexMap = indexByDate(taiexList,   TaiexIndex::getTradeDate);
```

Replace with:
```java
        List<MarginTransaction> marginList  = marginRepo.findByTradeDateBetweenOrderByTradeDateAsc(from, to);
        List<FuturesPosition>   futuresMtxAllList = futuresRepo
            .findByContractAndTradeDateBetweenOrderByTradeDateAsc("MTX", from, to);
        List<FuturesPosition>   futuresTmfAllList = futuresRepo
            .findByContractAndTradeDateBetweenOrderByTradeDateAsc("TMF", from, to);
        List<FuturesMarketOi>   mtxOiList = futuresMarketOiRepo
            .findByContractAndTradeDateBetweenOrderByTradeDateAsc("MTX", from, to);
        List<FuturesMarketOi>   tmfOiList = futuresMarketOiRepo
            .findByContractAndTradeDateBetweenOrderByTradeDateAsc("TMF", from, to);

        Map<LocalDate, TaiexIndex>        taiexMap = indexByDate(taiexList,   TaiexIndex::getTradeDate);
```

Find:
```java
        Map<LocalDate, MarginTransaction> mgnMap   = indexByDate(marginList,  MarginTransaction::getTradeDate);
```

Replace with:
```java
        Map<LocalDate, MarginTransaction> mgnMap   = indexByDate(marginList,  MarginTransaction::getTradeDate);
        Map<LocalDate, List<FuturesPosition>> mtxByDate = futuresMtxAllList.stream()
            .collect(Collectors.groupingBy(FuturesPosition::getTradeDate));
        Map<LocalDate, List<FuturesPosition>> tmfByDate = futuresTmfAllList.stream()
            .collect(Collectors.groupingBy(FuturesPosition::getTradeDate));
        Map<LocalDate, FuturesMarketOi> mtxOiMap = indexByDate(mtxOiList, FuturesMarketOi::getTradeDate);
        Map<LocalDate, FuturesMarketOi> tmfOiMap = indexByDate(tmfOiList, FuturesMarketOi::getTradeDate);
```

Find:
```java
        List<Long>   futuresAhLong  = new ArrayList<>();
        List<Long>   futuresAhShort = new ArrayList<>();
        List<Long>   futuresAhNet   = new ArrayList<>();
```

Replace with:
```java
        List<Long>   futuresAhLong  = new ArrayList<>();
        List<Long>   futuresAhShort = new ArrayList<>();
        List<Long>   futuresAhNet   = new ArrayList<>();
        List<Double> mtxRatio       = new ArrayList<>();
        List<Double> tmfRatio       = new ArrayList<>();
```

Find:
```java
            futuresAhLong.add(ah != null ? ah.getTradingLongVolume()  : null);
            futuresAhShort.add(ah != null ? ah.getTradingShortVolume() : null);
            futuresAhNet.add(ah != null ? ah.getTradingNetVolume()   : null);
        }
```

Replace with:
```java
            futuresAhLong.add(ah != null ? ah.getTradingLongVolume()  : null);
            futuresAhShort.add(ah != null ? ah.getTradingShortVolume() : null);
            futuresAhNet.add(ah != null ? ah.getTradingNetVolume()   : null);

            mtxRatio.add(retailRatio(mtxByDate.get(date), mtxOiMap.get(date)));
            tmfRatio.add(retailRatio(tmfByDate.get(date), tmfOiMap.get(date)));
        }
```

Find:
```java
        return new DashboardViewModel(
            isoDates, dateLabels, taiexClose, spotNetFlow,
            marginChange,
            futuresLongOI, futuresShortOI,
            optionsCallOI, optionsPutOI,
            optionsCallNetValue, optionsPutNetValue,
            futuresAhLong, futuresAhShort, futuresAhNet,
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
            mtxRatio, tmfRatio,
            days);
    }

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

    private static long sumOi(List<FuturesPosition> positions, Function<FuturesPosition, Long> field) {
        if (positions == null) return 0L;
        return positions.stream()
            .mapToLong(fp -> {
                Long v = field.apply(fp);
                return v != null ? v : 0L;
            })
            .sum();
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl eagleeye-web -am -Dtest=DashboardServiceTest -q`
Expected: PASS — all existing tests still pass (unstubbed `futuresMarketOiRepo`/new `futuresRepo` calls default to empty lists via Mockito, so `mtxRatio`/`tmfRatio` are all-null for every pre-existing test — matches the precedent set by the prior TX-equivalent feature), plus the 4 new tests.

- [ ] **Step 5: Compile the whole reactor to catch any other callers**

Run: `mvn compile -pl eagleeye-web -am -q`
Expected: BUILD SUCCESS. (`DashboardController` only calls `service.buildViewModel(days)` — no direct `DashboardViewModel`/`DashboardService` constructor calls elsewhere in `eagleeye-web` need updating.)

- [ ] **Step 6: Commit (both Task 7 and Task 8 together, since they don't compile independently)**

```bash
git add eagleeye-web/src/main/java/com/eagleeye/web/DashboardViewModel.java eagleeye-web/src/main/java/com/eagleeye/web/DashboardService.java eagleeye-web/src/test/java/com/eagleeye/web/DashboardServiceTest.java eagleeye-web/src/test/java/com/eagleeye/web/DashboardControllerTest.java
git commit -m "feat(dashboard): compute MTX/TMF 散戶多空比 in DashboardService"
```

---

### Task 9: `dashboard.html` — two new panels

**Files:**
- Modify: `eagleeye-web/src/main/resources/templates/dashboard.html`

Two new `.chart-card` blocks in their own `.chart-grid` row, using the exact bar-colored-by-sign + TAIEX-line pattern already used by the 融資變化 panel (no new CSS).

- [ ] **Step 1: Add the new chart-card markup**

Find:
```html
    <div class="chart-card">
      <div class="chart-title">外資選擇權買賣差額變化</div>
      <canvas id="optionsChart"></canvas>
    </div>

  </div>

  <div class="chart-grid" style="margin-top:10px;">
    <div class="chart-card">
      <div class="chart-title">外資期貨日增減（多單 vs 空單）</div>
      <div id="futuresDeltaTable"></div>
    </div>
```

Replace with:
```html
    <div class="chart-card">
      <div class="chart-title">外資選擇權買賣差額變化</div>
      <canvas id="optionsChart"></canvas>
    </div>

  </div>

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

  <div class="chart-grid" style="margin-top:10px;">
    <div class="chart-card">
      <div class="chart-title">外資期貨日增減（多單 vs 空單）</div>
      <div id="futuresDeltaTable"></div>
    </div>
```

- [ ] **Step 2: Add the new JS data arrays**

Find:
```javascript
const ahLong        = /*[[${vm.futuresAhLong}]]*/ [];
const ahShort       = /*[[${vm.futuresAhShort}]]*/ [];
const ahNet         = /*[[${vm.futuresAhNet}]]*/ [];
```

Replace with:
```javascript
const ahLong        = /*[[${vm.futuresAhLong}]]*/ [];
const ahShort       = /*[[${vm.futuresAhShort}]]*/ [];
const ahNet         = /*[[${vm.futuresAhNet}]]*/ [];
const mtxRatio      = /*[[${vm.mtxRatio}]]*/ [];
const tmfRatio      = /*[[${vm.tmfRatio}]]*/ [];
```

- [ ] **Step 3: Add the percentage formatter and the two new charts**

Find:
```javascript
new Chart(document.getElementById('optionsChart'), {
  data: {
    labels,
    datasets: [
      { type: 'bar', label: '買權差額', data: optCallNetVal, backgroundColor: RED,   yAxisID: 'y', order: 1 },
      { type: 'bar', label: '賣權差額', data: optPutNetVal,  backgroundColor: GREEN, yAxisID: 'y', order: 1 },
      taiexDs()
    ]
  },
  options: optValOpts
});

// 台指期結算日 = 每月第三個週三 (day 15–21, weekday=3)
```

Replace with:
```javascript
new Chart(document.getElementById('optionsChart'), {
  data: {
    labels,
    datasets: [
      { type: 'bar', label: '買權差額', data: optCallNetVal, backgroundColor: RED,   yAxisID: 'y', order: 1 },
      { type: 'bar', label: '賣權差額', data: optPutNetVal,  backgroundColor: GREEN, yAxisID: 'y', order: 1 },
      taiexDs()
    ]
  },
  options: optValOpts
});

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

// 台指期結算日 = 每月第三個週三 (day 15–21, weekday=3)
```

- [ ] **Step 4: Compile to verify the module still builds**

Run: `mvn compile -pl eagleeye-web -am -q`
Expected: BUILD SUCCESS (Thymeleaf templates aren't compiled, but this confirms nothing else broke).

- [ ] **Step 5: Commit**

```bash
git add eagleeye-web/src/main/resources/templates/dashboard.html
git commit -m "feat(dashboard): add MTX/TMF 散戶多空比 chart panels"
```

---

### Task 10: Full build + manual verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `mvn test -q`
Expected: BUILD SUCCESS, all modules pass.

- [ ] **Step 2: Start the web app**

Run: `mvn spring-boot:run -pl eagleeye-web -am`
Wait for `Started EagleeyeWebApplication` in the log.

- [ ] **Step 3: Load the dashboard and confirm the two new panels**

Open `http://localhost:8080/dashboard` in a browser (or use the `run`/`claude-in-chrome` tooling available in this session). Confirm:
- Two new panels titled "小台指多空比 vs 收盤價" and "微台指多空比 vs 收盤價" render below the existing 4-chart row.
- Bars are colored red/green matching sign, a 加權指數 line renders on the right axis, and hovering a bar shows a `%`-formatted tooltip value.
- If the local dev DB has no `futures_market_oi` rows yet (expected — the new collector hasn't run against this DB), the two new charts render empty/flat (all-null series) without any error in the browser console or server log. This is correct behavior, not a bug — it will populate once the MKTOI collector runs or a backfill is executed against this DB.

- [ ] **Step 4: Stop the app**

Kill the `spring-boot:run` process (Ctrl+C, or `run_in_background` termination if launched that way).

No commit for this task — it's verification only.
