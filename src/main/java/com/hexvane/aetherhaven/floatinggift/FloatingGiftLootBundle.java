package com.hexvane.aetherhaven.floatinggift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/** Parsed floating gift loot bundle with per-type tables and shared filler config. */
public final class FloatingGiftLootBundle {
    private static final int DEFAULT_FILLER_ROLLS_MIN = 2;
    private static final int DEFAULT_FILLER_ROLLS_MAX = 4;
    private static final int DEFAULT_RED_FURNITURE_ROLLS = 2;
    private static final int DEFAULT_RED_PROP_ROLLS = 1;
    private static final int DEFAULT_REGULAR_PLOT_BLUEPRINT_WEIGHT = 5;

    private final FloatingGiftLootTable regularTable;
    private final FloatingGiftLootTable greenTable;
    private final FloatingGiftLootTable redTable;
    private final FloatingGiftLootTable fillerTable;
    private final int regularPlotBlueprintWeight;
    private final int fillerRollsMin;
    private final int fillerRollsMax;
    private final int redFurnitureRolls;
    private final int redPropRolls;

    private FloatingGiftLootBundle(
        @Nonnull FloatingGiftLootTable regularTable,
        @Nonnull FloatingGiftLootTable greenTable,
        @Nonnull FloatingGiftLootTable redTable,
        @Nonnull FloatingGiftLootTable fillerTable,
        int regularPlotBlueprintWeight,
        int fillerRollsMin,
        int fillerRollsMax,
        int redFurnitureRolls,
        int redPropRolls
    ) {
        this.regularTable = regularTable;
        this.greenTable = greenTable;
        this.redTable = redTable;
        this.fillerTable = fillerTable;
        this.regularPlotBlueprintWeight = Math.max(0, regularPlotBlueprintWeight);
        this.fillerRollsMin = fillerRollsMin;
        this.fillerRollsMax = fillerRollsMax;
        this.redFurnitureRolls = redFurnitureRolls;
        this.redPropRolls = redPropRolls;
    }

    @Nonnull
    public static FloatingGiftLootBundle empty() {
        return new FloatingGiftLootBundle(
            FloatingGiftLootTable.empty(),
            FloatingGiftLootTable.empty(),
            FloatingGiftLootTable.empty(),
            FloatingGiftLootTable.empty(),
            DEFAULT_REGULAR_PLOT_BLUEPRINT_WEIGHT,
            DEFAULT_FILLER_ROLLS_MIN,
            DEFAULT_FILLER_ROLLS_MAX,
            DEFAULT_RED_FURNITURE_ROLLS,
            DEFAULT_RED_PROP_ROLLS
        );
    }

    @Nonnull
    public static FloatingGiftLootBundle parseJson(@Nonnull String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return empty();
        }
        JsonObject obj = root.getAsJsonObject();

        if (obj.has("entries") && !obj.has("regular")) {
            FloatingGiftLootTable legacy = FloatingGiftLootTable.parseJson(json);
            return new FloatingGiftLootBundle(
                legacy,
                FloatingGiftLootTable.empty(),
                FloatingGiftLootTable.empty(),
                FloatingGiftLootTable.empty(),
                DEFAULT_REGULAR_PLOT_BLUEPRINT_WEIGHT,
                DEFAULT_FILLER_ROLLS_MIN,
                DEFAULT_FILLER_ROLLS_MAX,
                DEFAULT_RED_FURNITURE_ROLLS,
                DEFAULT_RED_PROP_ROLLS
            );
        }

        JsonObject regularSection = obj.getAsJsonObject("regular");
        int plotBlueprintWeight = DEFAULT_REGULAR_PLOT_BLUEPRINT_WEIGHT;
        if (regularSection != null && regularSection.has("plotBlueprintWeight")) {
            plotBlueprintWeight = Math.max(0, regularSection.get("plotBlueprintWeight").getAsInt());
        }

        FloatingGiftLootTable regular = parseSectionTable(obj, "regular");
        FloatingGiftLootTable green = parseSectionTable(obj, "green");
        FloatingGiftLootTable red = parseSectionTable(obj, "red");
        FloatingGiftLootTable filler = parseSectionTable(obj, "filler");

        int rollsMin = DEFAULT_FILLER_ROLLS_MIN;
        int rollsMax = DEFAULT_FILLER_ROLLS_MAX;
        JsonObject fillerSection = obj.getAsJsonObject("filler");
        if (fillerSection != null) {
            if (fillerSection.has("rollsMin")) {
                rollsMin = fillerSection.get("rollsMin").getAsInt();
            }
            if (fillerSection.has("rollsMax")) {
                rollsMax = fillerSection.get("rollsMax").getAsInt();
            }
        }
        if (rollsMin > rollsMax) {
            int tmp = rollsMin;
            rollsMin = rollsMax;
            rollsMax = tmp;
        }
        rollsMin = Math.max(0, rollsMin);
        rollsMax = Math.max(rollsMin, rollsMax);

        int furnitureRolls = DEFAULT_RED_FURNITURE_ROLLS;
        int propRolls = DEFAULT_RED_PROP_ROLLS;
        JsonObject redSection = obj.getAsJsonObject("red");
        if (redSection != null) {
            if (redSection.has("furnitureRolls")) {
                furnitureRolls = Math.max(0, redSection.get("furnitureRolls").getAsInt());
            }
            if (redSection.has("propRolls")) {
                propRolls = Math.max(0, redSection.get("propRolls").getAsInt());
            }
        }

        return new FloatingGiftLootBundle(
            regular,
            green,
            red,
            filler,
            plotBlueprintWeight,
            rollsMin,
            rollsMax,
            furnitureRolls,
            propRolls
        );
    }

    @Nonnull
    public FloatingGiftLootBundle withRegularTable(@Nonnull FloatingGiftLootTable regularTable) {
        return new FloatingGiftLootBundle(
            regularTable,
            greenTable,
            redTable,
            fillerTable,
            regularPlotBlueprintWeight,
            fillerRollsMin,
            fillerRollsMax,
            redFurnitureRolls,
            redPropRolls
        );
    }

    @Nonnull
    private static FloatingGiftLootTable parseSectionTable(@Nonnull JsonObject root, @Nonnull String key) {
        JsonObject section = root.getAsJsonObject(key);
        if (section == null) {
            return FloatingGiftLootTable.empty();
        }
        JsonArray entries = section.getAsJsonArray("entries");
        if (entries == null) {
            return FloatingGiftLootTable.empty();
        }
        JsonObject wrapper = new JsonObject();
        wrapper.add("entries", entries);
        return FloatingGiftLootTable.parseJson(wrapper.toString());
    }

    @Nonnull
    public static FloatingGiftLootBundle loadFromFile(@Nonnull Path path, @Nonnull String fallbackJson) throws IOException {
        if (!Files.isRegularFile(path)) {
            return parseJson(fallbackJson);
        }
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return parseJson(sb.toString());
        }
    }

    @Nonnull
    public FloatingGiftLootTable tableFor(@Nonnull FloatingGiftType type) {
        return switch (type) {
            case REGULAR -> regularTable;
            case GREEN -> greenTable;
            case RED -> redTable;
        };
    }

    @Nonnull
    public FloatingGiftLootTable getFillerTable() {
        return fillerTable;
    }

    public int getFillerRollsMin() {
        return fillerRollsMin;
    }

    public int getFillerRollsMax() {
        return fillerRollsMax;
    }

    public int getRedFurnitureRolls() {
        return redFurnitureRolls;
    }

    public int getRedPropRolls() {
        return redPropRolls;
    }

    public int getRegularPlotBlueprintWeight() {
        return regularPlotBlueprintWeight;
    }
}
