package com.eagleeye.web.vp;

public record SessionsResponse(
        SessionVpocData open,
        SessionVpocData morning,
        SessionVpocData afternoon
) {}
