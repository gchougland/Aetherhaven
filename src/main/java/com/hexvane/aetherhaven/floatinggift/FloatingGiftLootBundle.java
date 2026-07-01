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

/** Parsed floating gift loot bundle with per-type tables and shared zone filler config. */
public final class FloatingGiftLootBundle {
    public static final String DEFAULT_FILLER_DROPLIST = "Zone1_Encounters_Tier1";
    private static final int DEFAULT_FILLER_ROLLS_MIN = 2;
    private static final int DEFAULT_FILLER_ROLLS_MAX = 4;
    private static final int DEFAULT_RED_FURNITURE_ROLLS = 3;
    private static final int DEFAULT_REGULAR_PLOT_BLUEPRINT_WEIGHT = 5;

    private final FloatingGiftLootTable regularTable;
    private final FloatingGiftLootTable greenTable;
    private final FloatingGiftLootTable redTable;
    private final int regularPlotBlueprintWeight;
    private final String fillerDroplistId;
    private final int fillerRollsMin;
    private final int fillerRollsMax;
    private final int redFurnitureRolls;

    private FloatingGiftLootBundle(
        @Nonnull FloatingGiftLootTable regularTable,
        @Nonnull FloatingGiftLootTable greenTable,
        @Nonnull FloatingGiftLootTable redTable,
        int regularPlotBlueprintWeight,
        @Nonnull String fillerDroplistId,
        int fillerRollsMin,
        int fillerRollsMax,
        int redFurnitureRolls
    ) {
        this.regularTable = regularTable;
        this.greenTable = greenTable;
        this.redTable = redTable;
        this.regularPlotBlueprintWeight = Math.max(0, regularPlotBlueprintWeight);
        this.fillerDroplistId = fillerDroplistId;
        this.fillerRollsMin = fillerRollsMin;
        this.fillerRollsMax = fillerRollsMax;
        this.redFurnitureRolls = redFurnitureRolls;
    }

    @Nonnull
    public static FloatingGiftLootBundle empty() {
        return new FloatingGiftLootBundle(
            FloatingGiftLootTable.empty(),
            FloatingGiftLootTable.empty(),
            FloatingGiftLootTable.empty(),
            DEFAULT_REGULAR_PLOT_BLUEPRINT_WEIGHT,
            DEFAULT_FILLER_DROPLIST,
            DEFAULT_FILLER_ROLLS_MIN,
            DEFAULT_FILLER_ROLLS_MAX,
            DEFAULT_RED_FURNITURE_ROLLS
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
                DEFAULT_REGULAR_PLOT_BLUEPRINT_WEIGHT,
                DEFAULT_FILLER_DROPLIST,
                DEFAULT_FILLER_ROLLS_MIN,
                DEFAULT_FILLER_ROLLS_MAX,
                DEFAULT_RED_FURNITURE_ROLLS
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

        String droplistId = DEFAULT_FILLER_DROPLIST;
        int rollsMin = DEFAULT_FILLER_ROLLS_MIN;
        int rollsMax = DEFAULT_FILLER_ROLLS_MAX;
        JsonObject filler = obj.getAsJsonObject("filler");
        if (filler != null) {
            if (filler.has("droplistId")) {
                droplistId = filler.get("droplistId").getAsString().trim();
            }
            if (filler.has("rollsMin")) {
                rollsMin = filler.get("rollsMin").getAsInt();
            }
            if (filler.has("rollsMax")) {
                rollsMax = filler.get("rollsMax").getAsInt();
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
        JsonObject redSection = obj.getAsJsonObject("red");
        if (redSection != null && redSection.has("furnitureRolls")) {
            furnitureRolls = Math.max(0, redSection.get("furnitureRolls").getAsInt());
        }

        return new FloatingGiftLootBundle(
            regular,
            green,
            red,
            plotBlueprintWeight,
            droplistId,
            rollsMin,
            rollsMax,
            furnitureRolls
        );
    }

    @Nonnull
    public FloatingGiftLootBundle withRegularTable(@Nonnull FloatingGiftLootTable regularTable) {
        return new FloatingGiftLootBundle(
            regularTable,
            greenTable,
            redTable,
            regularPlotBlueprintWeight,
            fillerDroplistId,
            fillerRollsMin,
            fillerRollsMax,
            redFurnitureRolls
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
    public String getFillerDroplistId() {
        return fillerDroplistId.isBlank() ? DEFAULT_FILLER_DROPLIST : fillerDroplistId;
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

    public int getRegularPlotBlueprintWeight() {
        return regularPlotBlueprintWeight;
    }
}
