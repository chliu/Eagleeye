package com.eagleeye.web.vp;

import java.util.List;
import java.util.Map;

public record TradingPlan(
        Map<String, PriceLevel> levels,
        List<Scenario> scenarios,
        String keyMessage
) {}
