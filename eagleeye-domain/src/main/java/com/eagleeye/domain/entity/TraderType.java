package com.eagleeye.domain.entity;

public enum TraderType {
    DEALER,
    INVESTMENT_TRUST,
    FINI;

    public static TraderType fromLabel(String label) {
        return switch (label.trim().toLowerCase()) {
            case "dealer", "dealers" -> DEALER;
            case "investment trust" -> INVESTMENT_TRUST;
            case "fini" -> FINI;
            default -> throw new IllegalArgumentException("Unknown trader type: " + label);
        };
    }
}
