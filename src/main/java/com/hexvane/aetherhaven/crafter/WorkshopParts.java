package com.hexvane.aetherhaven.crafter;

import java.util.*;

/**
 * The four generic materials every prop is made from, and the rule that prices a prop in them.
 * All four are ordinary game resources, so they keep their own models, icons and names.
 * A prop's total comes from its gold value; the split follows what the prop is visibly built of.
 */
public final class WorkshopParts {
    private WorkshopParts() {}

    public enum Kind {
        WOOD("Ingredient_Stick", "Sticks"),
        STONE("Rubble_Stone", "Stone Rubble"),
        METAL("Ingredient_Scraps", "Scraps"),
        CLOTH("Ingredient_Fabric_Scrap_Linen", "Linen Scraps");

        private final String item, label;
        Kind(String item, String label) { this.item = item; this.label = label; }
        public String item() { return item; }
        public String label() { return label; }
        public static Kind ofItem(String item) {
            for (Kind kind : values()) if (kind.item.equals(item)) return kind;
            return null;
        }
    }

    public static final int STEP = 5;
    private static final List<String> METAL_WORDS = List.of("metal", "iron", "copper", "bronze", "steel", "gold", "silver",
        "cobalt", "thorium", "adamantite", "mithril", "lantern", "lamp", "chain", "anvil", "cauldron", "bar_", "ingot");
    private static final List<String> CLOTH_WORDS = List.of("cloth", "fabric", "wool", "linen", "cotton", "silk", "carpet",
        "rug", "banner", "flag", "curtain", "pillow", "tapestry", "leather", "hide", "rope", "bolt_", "balloon", "tent");
    private static final List<String> STONE_WORDS = List.of("rock", "stone", "brick", "marble", "quartz", "basalt", "slate",
        "shale", "sandstone", "calcite", "chalk", "cobble", "crystal", "gem", "pot", "clay", "bone", "skull");
    private static final List<String> WOOD_WORDS = List.of("wood", "plank", "trunk", "log", "branch", "stick", "barrel",
        "crate", "shelf", "table", "chair", "bench", "fence", "beam", "plant", "leaf", "leaves", "flower", "hay", "crop");

    /** Reads a block or item id by its name alone. Returns null for things that are not a material, such as air. */
    public static Kind classify(String id) {
        if (id == null || id.isBlank() || "Empty".equals(id)) return null;
        String name = id.toLowerCase(Locale.ROOT);
        // Metal and cloth first: a "Furniture_Village_Lantern" is metalwork even though most furniture is wood.
        for (String word : METAL_WORDS) if (name.contains(word)) return Kind.METAL;
        for (String word : CLOTH_WORDS) if (name.contains(word)) return Kind.CLOTH;
        for (String word : STONE_WORDS) if (name.contains(word)) return Kind.STONE;
        for (String word : WOOD_WORDS) if (name.contains(word)) return Kind.WOOD;
        return name.startsWith("furniture_") || name.startsWith("deco_") ? Kind.WOOD : null;
    }

    /** How strongly each material shows in a prop, from the ids of its blocks and displayed items. */
    public static Map<Kind, Integer> weights(Collection<String> ids) {
        var result = new EnumMap<Kind, Integer>(Kind.class);
        if (ids != null) for (String id : ids) {
            Kind kind = classify(id);
            if (kind != null) result.merge(kind, 1, Integer::sum);
        }
        return result;
    }

    /** Total parts for a prop of this gold value: half its value, in steps of five, never less than five. */
    public static int total(long goldPrice) {
        double half = Math.max(0, Math.min(goldPrice, 10_000)) / 2.0;
        return (int) Math.max(STEP, Math.round(half / STEP) * STEP);
    }

    /**
     * Splits the total between the materials a prop shows, in steps of five. Faint materials are dropped
     * so a shelf with one metal nail stays a wooden job. A prop with nothing recognisable is made of wood.
     */
    public static Map<Kind, Integer> cost(long goldPrice, Map<Kind, Integer> weights) {
        int total = total(goldPrice);
        var seen = new EnumMap<Kind, Integer>(Kind.class);
        if (weights != null) for (var entry : weights.entrySet())
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) seen.put(entry.getKey(), entry.getValue());
        if (seen.isEmpty()) seen.put(Kind.WOOD, 1);
        int sum = seen.values().stream().mapToInt(Integer::intValue).sum();
        Kind main = Kind.WOOD; int most = -1;
        for (var entry : seen.entrySet()) if (entry.getValue() > most) { main = entry.getKey(); most = entry.getValue(); }
        var result = new EnumMap<Kind, Integer>(Kind.class);
        int spent = 0;
        for (var entry : seen.entrySet()) {
            if (entry.getKey() == main) continue;
            int share = (int) (Math.round(total * (entry.getValue() / (double) sum) / STEP) * STEP);
            share = Math.min(share, total - STEP - spent);
            if (share >= STEP) { result.put(entry.getKey(), share); spent += share; }
        }
        result.put(main, total - spent);
        return result;
    }

    /** Parts returned for taking a prop apart: a share of its cost, in whole parts, at least one of each. */
    public static Map<Kind, Integer> salvage(Map<Kind, Integer> cost, int percent) {
        var result = new EnumMap<Kind, Integer>(Kind.class);
        int share = Math.max(0, Math.min(100, percent));
        for (var entry : cost.entrySet()) result.put(entry.getKey(), Math.max(1, entry.getValue() * share / 100));
        return result;
    }

    /** Bench time for one prop: three seconds for each gold of value, never less than thirty. */
    public static double workSeconds(long goldPrice) {
        return Math.max(30, Math.min(goldPrice, 10_000) * 3.0);
    }
}
