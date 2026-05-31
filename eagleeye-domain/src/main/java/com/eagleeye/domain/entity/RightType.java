package com.eagleeye.domain.entity;

public enum RightType {
    CALL,
    PUT;

    public static RightType fromLabel(String label) {
        return switch (label.trim().toLowerCase()) {
            case "call", "買權" -> CALL;
            case "put", "賣權" -> PUT;
            default -> throw new IllegalArgumentException("Unknown right type: " + label);
        };
    }
}
