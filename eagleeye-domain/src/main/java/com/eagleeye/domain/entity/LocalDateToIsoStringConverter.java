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
