package com.eagleeye.web;

import java.util.List;

public record DashboardViewModel(
        List<String> isoDates,
        List<String> dateLabels,
        List<Double> taiexClose,
        List<Long>   spotNetFlow,
        List<Long>   marginChange,
        List<Long>   shortChange,
        List<Long>   futuresLongOI,
        List<Long>   futuresShortOI,
        List<Long>   optionsCallOI,
        List<Long>   optionsPutOI,
        List<Long>   optionsCallNetValue,
        List<Long>   optionsPutNetValue,
        int          days
) {}
