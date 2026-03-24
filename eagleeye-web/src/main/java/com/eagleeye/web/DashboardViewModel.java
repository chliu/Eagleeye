package com.eagleeye.web;

import java.util.List;

public record DashboardViewModel(
        List<String>    dateLabels,       // "M/d" x-axis labels for all charts
        List<Double>    taiexClose,       // entity close / 100.0 (display-ready)
        List<Long>      spotNetFlow,      // InstitutionalFlow.foreignNet per day (NTD)
        List<Long>      spotCumulative,   // running sum of spotNetFlow
        List<Double>    futuresLSRatio,   // (oiLongVolume - oiShortVolume) / (oiLongVolume + oiShortVolume)
        List<Long>      optionsNetOI,     // OptionsPosition.oiNetVolume (lots)
        List<Double>    marginChangeRate, // (marginBalance - marginPrevBalance) / marginPrevBalance
        List<AlertItem> alerts,
        int             days              // 20, 40, or 60
) {
    public record AlertItem(String signal, Severity severity, String message) {}

    public enum Severity { RED, YELLOW, GREEN }
}
