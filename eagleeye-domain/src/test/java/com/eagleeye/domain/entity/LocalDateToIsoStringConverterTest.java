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
