package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.FuturesPosition;
import com.eagleeye.domain.entity.TraderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FuturesPositionRepository extends JpaRepository<FuturesPosition, Long> {

    List<FuturesPosition> findByTradeDateOrderByContractAscTraderTypeAsc(LocalDate tradeDate);

    List<FuturesPosition> findByContractAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, LocalDate from, LocalDate to);

    List<FuturesPosition> findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, TraderType traderType, LocalDate from, LocalDate to);

    Optional<FuturesPosition> findByTradeDateAndContractAndTraderType(
            LocalDate tradeDate, String contract, TraderType traderType);
}
