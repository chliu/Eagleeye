package com.eagleeye.web.vp;

import java.util.List;

public record Scenario(
        int id,
        String title,
        String trigger,
        String bias,
        String entry,
        List<String> targets,
        String stopLoss,
        String rationale
) {}
