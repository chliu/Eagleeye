package com.eagleeye.web;

import java.util.List;

public record DashboardViewModel(
        List<String> isoDates,
        List<String> dateLabels,
        List<Double> taiexClose,
        List<Long>   spotNetFlow,
        List<Long>   marginChange,
        List<Long>   futuresLongOI,
        List<Long>   futuresShortOI,
        List<Long>   optionsCallOI,
        List<Long>   optionsPutOI,
        List<Long>   optionsCallNetValue,
        List<Long>   optionsPutNetValue,
        List<Long>   futuresAhLong,
        List<Long>   futuresAhShort,
        List<Long>   futuresAhNet,
        List<Long>   mtxNetPosition,
        List<Long>   tmfNetPosition,
        int          days
) {}
