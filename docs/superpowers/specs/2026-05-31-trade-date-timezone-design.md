# Fix `trade_date` timezone off-by-one — store as ISO text

**Date:** 2026-05-31
**Status:** Approved (design)

## Problem

`trade_date` is a `LocalDate` mapped to a `date` column in `AbstractMarketPosition`
(and four standalone entities). In the prod profile the data is stored in **SQLite**
via the xerial `sqlite-jdbc` driver + `org.hibernate.community.dialect.SQLiteDialect`.

The driver stores dates as **INTEGER epoch-millis**, converting `LocalDate` →
`java.sql.Date` through the **JVM default zone (Asia/Taipei)**. So `2026-05-29` is
persisted as Taipei midnight = `2026-05-28T16:00:00Z` = `1748448000000`-style millis.

Raw / external SQL that reads the integer as a UTC epoch therefore renders the date
**one day early**:

```sql
date(trade_date/1000, 'unixepoch')      -- 2026-05-28  (WRONG, UTC)
date(trade_date/1000 + 8*3600, 'unixepoch') -- 2026-05-29  (the +8h workaround)
```

**Scope note — the application is not broken.** All `trade_date` access goes through
JPA with `LocalDate` parameters (no native SQL in the app), so Hibernate writes and
reads with the same Taipei conversion and round-trips correctly. The off-by-one only
affects raw/external SQL and ad-hoc inspection. The fix makes the stored representation
unambiguous so the `+8h` workaround is never needed again.

## Goal

Store `trade_date` as an unambiguous ISO-8601 calendar date string (`'2026-05-29'`),
timezone-free, identically across SQLite / H2 / Postgres. Migrate existing rows so old
and new data are consistent. Remove the need for the `+8h` query workaround.

## Approach — JPA `AttributeConverter` to ISO text

A calendar date has no timezone, so store it as text via a JPA converter rather than
relying on driver/timezone behavior.

```java
@Converter(autoApply = false)
public class LocalDateToIsoStringConverter implements AttributeConverter<LocalDate, String> {
    @Override
    public String convertToDatabaseColumn(LocalDate d) {
        return d == null ? null : d.toString(); // ISO-8601, e.g. "2026-05-29"
    }
    @Override
    public LocalDate convertToEntityAttribute(String s) {
        return s == null ? null : LocalDate.parse(s);
    }
}
```

Apply `@Convert(converter = LocalDateToIsoStringConverter.class)` to every `tradeDate`
field:

| Location | Tables covered |
|----------|----------------|
| `AbstractMarketPosition.tradeDate` | `futures_position`, `options_position`, `options_call_put_position` |
| `FuturesAhPosition.tradeDate` | `futures_ah_position` |
| `InstitutionalFlow.tradeDate` | `institutional_flow` |
| `MarginTransaction.tradeDate` | `margin_transaction` |
| `TaiexIndex.tradeDate` | `taiex_index` |

### Why this over the xerial `date_class=text` driver flag

`date_class=text&date_string_format=yyyy-MM-dd` was considered and rejected: the driver
still serializes the `java.sql.Date` through a `Calendar`/timezone, which can reproduce
the same off-by-one, and it is SQLite-only. `LocalDate.toString()` is timezone-free and
portable across all three databases.

### Query compatibility

- `'yyyy-MM-dd'` lexical order == chronological order, so `OrderByTradeDateDesc` and
  `findByTradeDateBetween` derived queries keep working (Hibernate runs the converter on
  bound parameters too).
- Unique constraints `(trade_date, contract, trader_type[, right_type])` unaffected.
- No DDL change: SQLite's existing `date`-affinity column stores the non-numeric ISO
  text as-is, and `ddl-auto: update` leaves the existing column alone. Fresh H2/Postgres
  schemas get a VARCHAR column.

## Existing-data migration (prod SQLite)

One idempotent `UPDATE` per table, recovering the true Taipei date and rewriting it as
ISO text. Idempotent via the `typeof = 'integer'` guard.

```sql
UPDATE <table>
SET trade_date = date(trade_date/1000 + 8*3600, 'unixepoch')
WHERE typeof(trade_date) = 'integer';
```

Run against all seven tables: `options_position`, `options_call_put_position`,
`futures_position`, `futures_ah_position`, `institutional_flow`, `margin_transaction`,
`taiex_index`.

Delivered as a checked-in SQL script run once via `sqlite3` against
`~/.eagleeye/data/eagleeye.db`. The project has no Flyway/Liquibase and uses
`ddl-auto: update`; no migration framework will be introduced.

Dev H2 (`eagleeye.mv.db`) is throwaway under `ddl-auto: update` → delete and let it
regenerate rather than migrate.

## Testing & verification

- **Unit:** `LocalDateToIsoStringConverter` round-trips `LocalDate ↔ "yyyy-MM-dd"`,
  including `null`.
- **Integration** (existing IT harness): after persisting an entity with a known
  `LocalDate`, a native `SELECT trade_date` returns the exact ISO string with **no
  offset**, and `findByTradeDate(...)` still round-trips.
- **Post-migration checks:** `SELECT DISTINCT trade_date` shows `'2026-05-29'`-style
  strings; row counts unchanged before/after; re-verify the 5/29 FINI numbers
  (CALL `oi_net_value` = 569,039, PUT = 101,887) with a plain
  `WHERE trade_date = '2026-05-29'` (no `+8h`).
- Retire/update the `db-trade-date-taipei-epoch` memory note once shipped.

## Out of scope

- No change to collector "what is today" logic (still Asia/Taipei).
- No new migration framework.
- No refactoring beyond applying the converter and the one-time data migration.
