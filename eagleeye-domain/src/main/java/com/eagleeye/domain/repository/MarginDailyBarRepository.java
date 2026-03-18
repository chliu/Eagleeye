package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.MarginDailyBar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarginDailyBarRepository extends JpaRepository<MarginDailyBar, Long> {

    Optional<MarginDailyBar> findByTradeDate(LocalDate tradeDate);

    List<MarginDailyBar> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
}
