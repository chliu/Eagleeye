package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.MarginTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarginTransactionRepository extends JpaRepository<MarginTransaction, Long> {

    Optional<MarginTransaction> findByTradeDate(LocalDate tradeDate);

    List<MarginTransaction> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
}
