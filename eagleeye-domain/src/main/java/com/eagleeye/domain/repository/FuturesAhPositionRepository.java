package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.FuturesAhPosition;
import com.eagleeye.domain.entity.TraderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FuturesAhPositionRepository extends JpaRepository<FuturesAhPosition, Long> {

    List<FuturesAhPosition> findByTradeDateOrderByContractAscTraderTypeAsc(LocalDate tradeDate);

    List<FuturesAhPosition> findByContractAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, LocalDate from, LocalDate to);

    List<FuturesAhPosition> findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, TraderType traderType, LocalDate from, LocalDate to);

    Optional<FuturesAhPosition> findByTradeDateAndContractAndTraderType(
            LocalDate tradeDate, String contract, TraderType traderType);
}
