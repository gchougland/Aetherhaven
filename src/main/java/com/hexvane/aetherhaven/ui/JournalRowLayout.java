package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

/** Keeps existing journal widgets alive while their data is refreshed. Owned by one open page. */
final class JournalRowLayout {
    private final String selector;
    private final String template;
    private int size;

    JournalRowLayout(String selector, String template) {
        this.selector = selector;
        this.template = template;
    }

    /** Returns the previous size so callers can initialize children of newly appended rows once. */
    int resize(UICommandBuilder commands, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative row count");
        int previous = size;
        for (int i = size - 1; i >= count; i--) commands.remove(selector + "[" + i + "]");
        for (int i = size; i < count; i++) commands.append(selector, template);
        size = count;
        return previous;
    }
}
