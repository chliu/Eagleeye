package com.eagleeye.collector.twse;

import tools.jackson.databind.JsonNode;

class ParseUtils {

    static long toLong(String value) {
        return Long.parseLong(value.replace(",", ""));
    }

    static String truncate(String s) {
        if (s == null) return "<null>";
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }

    static JsonNode extractTableData(JsonNode root) {
        JsonNode tables = root.path("tables");
        return (tables.isArray() && !tables.isEmpty())
                ? tables.get(0).path("data")
                : root.path("data");
    }
}
