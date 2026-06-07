package com.eagleeye.web.vp;

public record VpEntry(
        int price,
        int volume,
        PriceType type,
        boolean inValueArea,
        double cumVolumePct
) {}
