package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.TaiexIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaiexIndexRepository extends JpaRepository<TaiexIndex, Long> {

    Optional<TaiexIndex> findByTradeDate(LocalDate tradeDate);

    List<TaiexIndex> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
}
