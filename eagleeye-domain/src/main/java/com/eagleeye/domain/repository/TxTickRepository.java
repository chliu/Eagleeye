package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.TxTick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface TxTickRepository extends JpaRepository<TxTick, Long> {

    long countByTradeDate(LocalDate tradeDate);

    List<TxTick> findTop5ByTradeDateOrderByTimeAsc(LocalDate tradeDate);

    @Modifying
    @Query("DELETE FROM TxTick t WHERE t.tradeDate = :tradeDate")
    void deleteByTradeDate(LocalDate tradeDate);

    // Load all ticks for a date (auction flag filtered in Java)
    List<TxTick> findByTradeDateOrderByTimeAsc(LocalDate tradeDate);

    // Distinct trade dates for the date picker, newest first
    @Query("SELECT DISTINCT t.tradeDate FROM TxTick t ORDER BY t.tradeDate DESC")
    List<LocalDate> findDistinctTradeDates();
}
