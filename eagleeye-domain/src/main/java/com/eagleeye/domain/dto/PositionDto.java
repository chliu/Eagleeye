package com.eagleeye.domain.dto;

import com.eagleeye.domain.entity.TraderType;

import java.time.LocalDate;

public record PositionDto(
        LocalDate tradeDate,
        String contract,
        TraderType traderType,
        long tradingLongVolume,
        long tradingLongValue,
        long tradingShortVolume,
        long tradingShortValue,
        long tradingNetVolume,
        long tradingNetValue,
        long oiLongVolume,
        long oiLongValue,
        long oiShortVolume,
        long oiShortValue,
        long oiNetVolume,
        long oiNetValue
) {}
