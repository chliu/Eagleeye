package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.OptionsPosition;
import com.eagleeye.domain.entity.TraderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OptionsPositionRepository extends JpaRepository<OptionsPosition, Long> {

    List<OptionsPosition> findByTradeDateOrderByContractAscTraderTypeAsc(LocalDate tradeDate);

    List<OptionsPosition> findByContractAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, LocalDate from, LocalDate to);

    List<OptionsPosition> findByContractAndTraderTypeAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, TraderType traderType, LocalDate from, LocalDate to);

    Optional<OptionsPosition> findByTradeDateAndContractAndTraderType(
            LocalDate tradeDate, String contract, TraderType traderType);
}
