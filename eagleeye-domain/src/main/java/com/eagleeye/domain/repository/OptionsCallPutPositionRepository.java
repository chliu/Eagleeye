package com.eagleeye.domain.repository;

import com.eagleeye.domain.entity.OptionsCallPutPosition;
import com.eagleeye.domain.entity.RightType;
import com.eagleeye.domain.entity.TraderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OptionsCallPutPositionRepository
        extends JpaRepository<OptionsCallPutPosition, Long> {

    Optional<OptionsCallPutPosition> findByTradeDateAndContractAndTraderTypeAndRightType(
            LocalDate tradeDate, String contract, TraderType traderType, RightType rightType);

    List<OptionsCallPutPosition>
    findByContractAndTraderTypeAndRightTypeAndTradeDateBetweenOrderByTradeDateAsc(
            String contract, TraderType traderType, RightType rightType,
            LocalDate from, LocalDate to);
}
