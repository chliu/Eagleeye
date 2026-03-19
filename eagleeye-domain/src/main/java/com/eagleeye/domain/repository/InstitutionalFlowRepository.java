package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.InstitutionalFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstitutionalFlowRepository extends JpaRepository<InstitutionalFlow, Long> {
    Optional<InstitutionalFlow> findByTradeDate(LocalDate tradeDate);
    List<InstitutionalFlow> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
}
