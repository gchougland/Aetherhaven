package com.hexvane.aetherhaven.ui;

import java.util.Locale;

/** Shared name matching for home and workplace assignments. */
final class ResidentSearch {
    private ResidentSearch() {}
    static String query(String value) {
        return value == null ? "" : value.substring(0, Math.min(100, value.length())).strip().toLowerCase(Locale.ROOT);
    }
    static boolean matches(String name, String query) {
        String normalized = query(query);
        if (normalized.isEmpty()) return true;
        if (name == null) return false;
        String display = name.toLowerCase(Locale.ROOT);
        for (String word : normalized.split("\\s+")) if (!display.contains(word)) return false;
        return true;
    }
}
