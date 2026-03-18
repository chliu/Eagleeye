package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.TaiexDailyBar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaiexDailyBarRepository extends JpaRepository<TaiexDailyBar, Long> {

    Optional<TaiexDailyBar> findByTradeDate(LocalDate tradeDate);

    List<TaiexDailyBar> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
}
