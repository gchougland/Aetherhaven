package com.hexvane.aetherhaven.testtown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a test town build did: counts by kind and everything it skipped, with the reason. Thread safe, because the
 * build hops between the world thread and its helpers.
 */
public final class TestTownReport {
    public static final String BUILDINGS = "buildings";
    public static final String SITES = "sites";
    public static final String VILLAGERS = "villagers";
    public static final String WORKPLACES = "workplaces";
    public static final String TOWNSFOLK = "townsfolk";
    public static final String ASSIGNED = "assigned";
    public static final String COURIERS = "couriers";
    public static final String GUARDS = "guards";
    public static final String CLERKS = "clerks";
    public static final String PRODUCTION = "production";
    public static final String ORDERS = "orders";
    public static final String STOCK = "stock";
    public static final String PATHS = "paths";

    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final List<String> skipped = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private boolean existing, failed;

    public TestTownReport() {
        for (String key : List.of(BUILDINGS, SITES, VILLAGERS, WORKPLACES, TOWNSFOLK, ASSIGNED, COURIERS, GUARDS, CLERKS,
            PRODUCTION, ORDERS, STOCK, PATHS)) counts.put(key, 0);
    }

    public synchronized void add(String kind) { add(kind, 1); }

    public synchronized void add(String kind, int amount) { counts.merge(kind, amount, Integer::sum); }

    public synchronized void set(String kind, int amount) { counts.put(kind, amount); }

    public synchronized int count(String kind) { return counts.getOrDefault(kind, 0); }

    /** Records something the build could not do. It carries on afterwards. */
    public synchronized void skip(String what, String why) {
        skipped.add(what + ": " + (why == null || why.isBlank() ? "no reason given" : why));
    }

    public synchronized void note(String text) { notes.add(text); }

    public synchronized void markExisting() { existing = true; }

    public synchronized boolean existing() { return existing; }

    /** The town itself could not be founded, so nothing else was built. */
    public synchronized void markFailed() { failed = true; }

    public synchronized boolean failed() { return failed; }

    public synchronized List<String> skipped() { return List.copyOf(skipped); }

    public synchronized List<String> notes() { return List.copyOf(notes); }

    /** One line with every count, the line the console proof waits for. */
    public synchronized String summary(String worldName) {
        return (failed ? "Test town NOT built" : existing ? "Test town found" : "Test town built") + " in world " + worldName + ": "
            + some(BUILDINGS, "building", "buildings") + ", " + some(SITES, "building site", "building sites") + ", "
            + some(VILLAGERS, "core villager", "core villagers") + ", " + some(WORKPLACES, "workplace", "workplaces") + ", "
            + counts.get(TOWNSFOLK) + " townsfolk hired, " + counts.get(ASSIGNED) + " with a workplace job, "
            + some(COURIERS, "courier", "couriers") + ", " + some(GUARDS, "guard", "guards") + ", "
            + some(CLERKS, "shop clerk", "shop clerks") + ", " + some(PRODUCTION, "production building set up", "production buildings set up")
            + ", " + some(ORDERS, "order", "orders") + ", " + some(STOCK, "kind of stock", "kinds of stock") + ", "
            + some(PATHS, "path", "paths") + ", " + skipped.size() + " skipped.";
    }

    private String some(String kind, String one, String many) {
        int count = counts.get(kind);
        return count + " " + (count == 1 ? one : many);
    }

    /** One line per skipped thing and per note, then the summary, so the last line printed is the one to wait for. */
    public synchronized List<String> lines(String worldName) {
        List<String> lines = new ArrayList<>();
        for (String skip : skipped) lines.add("Test town skipped " + skip);
        for (String note : notes) lines.add("Test town note: " + note);
        lines.add(summary(worldName));
        return lines;
    }
}
