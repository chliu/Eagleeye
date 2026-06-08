package com.eagleeye.web.vp;

public record LargeTrade(
        String time,
        int price,
        int volume,
        String session,
        int priceVsVpoc,
        TradeZone zone,
        TradeDirection direction
) {}
