# trade_date ISO-text Timezone Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist `trade_date` as an ISO-8601 text string via a JPA `AttributeConverter` so the SQLite epoch-millis off-by-one disappears, and migrate existing rows to match.

**Architecture:** A single `AttributeConverter<LocalDate, String>` maps `LocalDate ↔ "yyyy-MM-dd"`. It is applied with `@Convert` to every `tradeDate` field (one mapped-superclass field + four standalone entities). Existing prod SQLite rows (INTEGER epoch-millis at Taipei midnight) are rewritten to ISO text by a one-time SQL script. The application already round-trips correctly through JPA; this change only makes the stored representation unambiguous and removes the `+8h` raw-SQL workaround.

**Tech Stack:** Java 25, Spring Boot, Hibernate ORM 6 (community SQLite dialect), JUnit 5, AssertJ, SQLite (prod) / H2 (dev) / Postgres (optional).

**Spec:** `docs/superpowers/specs/2026-05-31-trade-date-timezone-design.md`

---

## File Structure

- **Create** `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/LocalDateToIsoStringConverter.java` — the converter.
- **Create** `eagleeye-domain/src/test/java/com/eagleeye/domain/entity/LocalDateToIsoStringConverterTest.java` — converter unit test.
- **Modify** `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/AbstractMarketPosition.java` — `@Convert` on `tradeDate` (covers `futures_position`, `options_position`, `options_call_put_position`).
- **Modify** `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/FuturesAhPosition.java` — `@Convert` on `tradeDate`.
- **Modify** `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/InstitutionalFlow.java` — `@Convert` on `tradeDate`.
- **Modify** `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/MarginTransaction.java` — `@Convert` on `tradeDate`.
- **Modify** `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/TaiexIndex.java` — `@Convert` on `tradeDate`.
- **Create** `eagleeye-collector/src/test/java/com/eagleeye/collector/TradeDateTimezoneIT.java` — SQLite integration test proving no offset.
- **Create** `scripts/migrations/2026-05-31-trade-date-to-iso.sql` — one-time data migration.

---

## Task 1: LocalDate ↔ ISO-text converter

**Files:**
- Create: `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/LocalDateToIsoStringConverter.java`
- Test: `eagleeye-domain/src/test/java/com/eagleeye/domain/entity/LocalDateToIsoStringConverterTest.java`

- [ ] **Step 1: Write the failing test**

Create `eagleeye-domain/src/test/java/com/eagleeye/domain/entity/LocalDateToIsoStringConverterTest.java`:

```java
package com.eagleeye.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDateToIsoStringConverterTest {

    private final LocalDateToIsoStringConverter converter = new LocalDateToIsoStringConverter();

    @Test
    void convertToDatabaseColumn_formatsIso() {
        assertThat(converter.convertToDatabaseColumn(LocalDate.of(2026, 5, 29)))
                .isEqualTo("2026-05-29");
    }

    @Test
    void convertToEntityAttribute_parsesIso() {
        assertThat(converter.convertToEntityAttribute("2026-05-29"))
                .isEqualTo(LocalDate.of(2026, 5, 29));
    }

    @Test
    void handlesNullBothDirections() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl eagleeye-domain test -Dtest=LocalDateToIsoStringConverterTest`
Expected: compilation failure — `LocalDateToIsoStringConverter` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/LocalDateToIsoStringConverter.java`:

```java
package com.eagleeye.domain.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

/**
 * Persists {@link LocalDate} as an ISO-8601 text string ("yyyy-MM-dd").
 *
 * <p>A trade date is a calendar date with no time-of-day, so storing it as text
 * keeps it timezone-free and identical across SQLite, H2, and Postgres. This avoids
 * the xerial SQLite driver storing dates as epoch-millis through the JVM default zone
 * (Asia/Taipei), which rendered raw-SQL dates one day early.
 */
@Converter(autoApply = false)
public class LocalDateToIsoStringConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate date) {
        return date == null ? null : date.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : LocalDate.parse(dbValue);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl eagleeye-domain test -Dtest=LocalDateToIsoStringConverterTest`
Expected: PASS (3 tests green).

- [ ] **Step 5: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/entity/LocalDateToIsoStringConverter.java \
        eagleeye-domain/src/test/java/com/eagleeye/domain/entity/LocalDateToIsoStringConverterTest.java
git commit -m "feat(domain): add LocalDate to ISO-text JPA converter"
```

---

## Task 2: Apply converter to all tradeDate fields (proven on SQLite)

**Files:**
- Test: `eagleeye-collector/src/test/java/com/eagleeye/collector/TradeDateTimezoneIT.java`
- Modify: `eagleeye-domain/.../AbstractMarketPosition.java:27-28`
- Modify: `eagleeye-domain/.../FuturesAhPosition.java:20-21`
- Modify: `eagleeye-domain/.../InstitutionalFlow.java:20-21`
- Modify: `eagleeye-domain/.../MarginTransaction.java:20-21`
- Modify: `eagleeye-domain/.../TaiexIndex.java:20-21`

- [ ] **Step 1: Write the failing integration test**

Create `eagleeye-collector/src/test/java/com/eagleeye/collector/TradeDateTimezoneIT.java`. It runs against an **in-memory SQLite** DB (single Hikari connection so the `:memory:` DB survives across the connection) with the community SQLite dialect — the exact prod stack:

```java
package com.eagleeye.collector;

import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TraderType;
import com.eagleeye.domain.repository.OptionsCallPutPositionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eagleeye.collector.enabled=false"
})
@Transactional
class TradeDateTimezoneIT {

    @Autowired private OptionsCallPutPositionRepository repo;
    @Autowired private EntityManager em;

    private static final LocalDate DATE = LocalDate.of(2026, 5, 28);

    @Test
    @DisplayName("SQLite stores trade_date as ISO text with no timezone offset")
    void tradeDate_storedAsIsoText_noOffset() {
        repo.saveAndFlush(new OptionsCallPutPosition(DATE, "TXO", TraderType.FINI, RightType.CALL));
        em.clear();

        Object raw = em.createNativeQuery(
                "SELECT trade_date FROM options_call_put_position WHERE contract = 'TXO'")
                .getSingleResult();

        // Before the fix this is an epoch-millis Long (one day early in UTC); after it is ISO text.
        assertThat(raw).hasToString("2026-05-28");

        OptionsCallPutPosition found = repo
                .findByTradeDateAndContractAndTraderTypeAndRightType(
                        DATE, "TXO", TraderType.FINI, RightType.CALL)
                .orElseThrow();
        assertThat(found.getTradeDate()).isEqualTo(DATE);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl eagleeye-collector -am test -Dtest=TradeDateTimezoneIT`
Expected: FAIL on `assertThat(raw).hasToString("2026-05-28")` — the native value is a Long epoch-millis (e.g. `1748390400000`), not the string `2026-05-28`.

- [ ] **Step 3: Apply `@Convert` on the mapped-superclass field**

In `eagleeye-domain/src/main/java/com/eagleeye/domain/entity/AbstractMarketPosition.java`, add the import and annotate the field.

Add to the imports (after `import jakarta.persistence.Column;`):

```java
import jakarta.persistence.Convert;
```

Change:

```java
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

to:

```java
    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

- [ ] **Step 4: Apply `@Convert` on `FuturesAhPosition`**

In `FuturesAhPosition.java` (imports already use `import jakarta.persistence.*;`), change:

```java
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

to:

```java
    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

- [ ] **Step 5: Apply `@Convert` on `InstitutionalFlow`**

In `InstitutionalFlow.java` (imports use `import jakarta.persistence.*;`), change:

```java
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

to:

```java
    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

- [ ] **Step 6: Apply `@Convert` on `MarginTransaction`**

In `MarginTransaction.java` (imports use `import jakarta.persistence.*;`), change:

```java
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

to:

```java
    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

- [ ] **Step 7: Apply `@Convert` on `TaiexIndex`**

In `TaiexIndex.java` (imports use `import jakarta.persistence.*;`), change:

```java
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

to:

```java
    @Convert(converter = LocalDateToIsoStringConverter.class)
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
```

- [ ] **Step 8: Run the integration test to verify it passes**

Run: `mvn -q -pl eagleeye-collector -am test -Dtest=TradeDateTimezoneIT`
Expected: PASS — native value is now `2026-05-28`, and `findByTradeDate` round-trips.

- [ ] **Step 9: Run the full collector + domain test suites (no regressions)**

Run: `mvn -q -pl eagleeye-domain,eagleeye-collector -am test`
Expected: PASS — existing H2 ITs (round-trip, ordering, unique constraint) still green with the converter applied.

- [ ] **Step 10: Commit**

```bash
git add eagleeye-domain/src/main/java/com/eagleeye/domain/entity/AbstractMarketPosition.java \
        eagleeye-domain/src/main/java/com/eagleeye/domain/entity/FuturesAhPosition.java \
        eagleeye-domain/src/main/java/com/eagleeye/domain/entity/InstitutionalFlow.java \
        eagleeye-domain/src/main/java/com/eagleeye/domain/entity/MarginTransaction.java \
        eagleeye-domain/src/main/java/com/eagleeye/domain/entity/TaiexIndex.java \
        eagleeye-collector/src/test/java/com/eagleeye/collector/TradeDateTimezoneIT.java
git commit -m "fix(domain): store trade_date as ISO text to remove SQLite tz off-by-one"
```

---

## Task 3: Migrate existing prod SQLite rows

**Files:**
- Create: `scripts/migrations/2026-05-31-trade-date-to-iso.sql`

- [ ] **Step 1: Create the migration script**

Create `scripts/migrations/2026-05-31-trade-date-to-iso.sql`:

```sql
-- One-time migration: convert trade_date from INTEGER epoch-millis (Asia/Taipei
-- midnight) to ISO-8601 text "yyyy-MM-dd". Idempotent: only touches INTEGER rows.
-- The +8h offset recovers the true Taipei trading day before formatting.
-- Run once against ~/.eagleeye/data/eagleeye.db (back it up first).

BEGIN TRANSACTION;

UPDATE options_position
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE options_call_put_position
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE futures_position
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE futures_ah_position
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE institutional_flow
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE margin_transaction
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

UPDATE taiex_index
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';

COMMIT;
```

- [ ] **Step 2: Commit the script**

```bash
git add scripts/migrations/2026-05-31-trade-date-to-iso.sql
git commit -m "chore(db): migration script converting trade_date to ISO text"
```

- [ ] **Step 3: Back up the prod DB**

Run:
```bash
cp ~/.eagleeye/data/eagleeye.db ~/.eagleeye/data/eagleeye.db.bak-$(date +%Y%m%d)
```
Expected: a `.bak-YYYYMMDD` copy exists. (Stop any running collector/shell/web process first so the DB is not mid-write.)

- [ ] **Step 4: Capture pre-migration row counts**

Run:
```bash
sqlite3 ~/.eagleeye/data/eagleeye.db "SELECT 'options_call_put_position', COUNT(*) FROM options_call_put_position
UNION ALL SELECT 'options_position', COUNT(*) FROM options_position
UNION ALL SELECT 'futures_position', COUNT(*) FROM futures_position
UNION ALL SELECT 'futures_ah_position', COUNT(*) FROM futures_ah_position
UNION ALL SELECT 'institutional_flow', COUNT(*) FROM institutional_flow
UNION ALL SELECT 'margin_transaction', COUNT(*) FROM margin_transaction
UNION ALL SELECT 'taiex_index', COUNT(*) FROM taiex_index;"
```
Expected: a count per table — record these numbers.

- [ ] **Step 5: Run the migration**

Run:
```bash
sqlite3 ~/.eagleeye/data/eagleeye.db < scripts/migrations/2026-05-31-trade-date-to-iso.sql
```
Expected: no output, exit code 0.

- [ ] **Step 6: Verify storage type, row counts, and the 5/29 numbers**

Run:
```bash
sqlite3 -header -column ~/.eagleeye/data/eagleeye.db "
SELECT typeof(trade_date) AS type, COUNT(*) FROM options_call_put_position GROUP BY 1;
SELECT trade_date, right_type, oi_net_value
FROM options_call_put_position
WHERE trade_date = '2026-05-29' AND trader_type = 'FINI' AND contract = 'TXO'
ORDER BY right_type;"
```
Expected:
- `type` column is `text` for all rows (no `integer` remaining).
- CALL `oi_net_value` = `569039`, PUT `oi_net_value` = `101887`, queried with a **plain** `trade_date = '2026-05-29'` (no `+8h`). Re-run Step 4's count query and confirm counts are unchanged.

---

## Task 4: Dev cleanup and memory update

**Files:** none (operational + memory).

- [ ] **Step 1: Regenerate the dev H2 database**

The dev/default profile uses H2 at `~/.eagleeye/data/eagleeye.mv.db`. Under `ddl-auto: update` its `trade_date` column type would now mismatch (DATE vs VARCHAR). It is throwaway dev data — delete it so it regenerates on next dev run:

Run:
```bash
rm -f ~/.eagleeye/data/eagleeye.mv.db ~/.eagleeye/data/eagleeye.trace.db
```
Expected: files removed (no error if absent).

- [ ] **Step 2: Update the timezone memory note**

The persisted memory `db-trade-date-taipei-epoch` documents the `+8h` workaround, which is now obsolete for newly stored/migrated data. Update it to state the fix shipped (trade_date is ISO text as of 2026-05-31) and that the `+8h` offset is only needed for un-migrated backups. (Done by the assistant via the memory mechanism, not a code change.)

- [ ] **Step 3: Final verification — full build**

Run: `mvn -q test`
Expected: PASS across all modules.
