package com.eagleeye.web.vp;

public record VpSummary(
        String date,
        String product,
        int open,
        int close,
        int high,
        int low,
        int totalVolume,
        int vpoc,
        int vpocVolume,
        int vah,
        int val,
        double valueAreaPct,
        int closeVsVpoc
) {}
