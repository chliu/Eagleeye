package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.FuturesMarketOi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FuturesMarketOiRepository extends JpaRepository<FuturesMarketOi, Long> {

    Optional<FuturesMarketOi> findByTradeDateAndContract(LocalDate tradeDate, String contract);

    List<FuturesMarketOi> findByContractAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, LocalDate from, LocalDate to);
}
