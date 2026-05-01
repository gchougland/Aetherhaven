package com.hexvane.aetherhaven.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Loads {@code Server/Aetherhaven/Production/workplace_unlocks.json} (all workplaces, including lumbermill trunks). */
public final class WorkplaceUnlockCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE = "Server/Aetherhaven/Production/workplace_unlocks.json";

    public static final class UnlockLine {
        private final String workplace;
        private final String itemId;
        private final int rarityTier;
        private final boolean defaultUnlocked;
        private final int resourceCost;
        private final int ticks;
        private final long maxStorage;

        public UnlockLine(
            @Nonnull String workplace,
            @Nonnull String itemId,
            int rarityTier,
            boolean defaultUnlocked,
            int resourceCost,
            int ticks,
            long maxStorage
        ) {
            this.workplace = workplace.trim();
            this.itemId = itemId.trim();
            this.rarityTier = rarityTier;
            this.defaultUnlocked = defaultUnlocked;
            this.resourceCost = Math.max(1, resourceCost);
            this.ticks = Math.max(1, ticks);
            this.maxStorage = Math.max(1L, Math.min(ProductionCatalog.MAX_STORAGE_PER_OUTPUT, maxStorage));
        }

        @Nonnull
        public String workplace() {
            return workplace;
        }

        @Nonnull
        public String itemId() {
            return itemId;
        }

        public int rarityTier() {
            return rarityTier;
        }

        public boolean defaultUnlocked() {
            return defaultUnlocked;
        }

        public int resourceCost() {
            return resourceCost;
        }

        public int ticks() {
            return ticks;
        }

        public long maxStorage() {
            return maxStorage;
        }
    }

    private final List<UnlockLine> allLines;

    private WorkplaceUnlockCatalog(@Nonnull List<UnlockLine> allLines) {
        this.allLines = List.copyOf(allLines);
    }

    @Nonnull
    public static WorkplaceUnlockCatalog empty() {
        return new WorkplaceUnlockCatalog(List.of());
    }

    @Nonnull
    public static WorkplaceUnlockCatalog loadFromClasspath(@Nonnull ClassLoader classLoader) {
        List<UnlockLine> fromJson = new ArrayList<>();
        try (InputStream in = classLoader.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray arr = root.getAsJsonArray("unlocks");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        if (el == null || !el.isJsonObject()) {
                            continue;
                        }
                        UnlockLine line = parseLine(el.getAsJsonObject());
                        if (line != null) {
                            fromJson.add(line);
                        }
                    }
                }
            } else {
                LOGGER.atWarning().log("Missing resource %s", RESOURCE);
            }
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load %s", RESOURCE);
        }
        return new WorkplaceUnlockCatalog(fromJson);
    }

    @Nullable
    private static UnlockLine parseLine(@Nonnull JsonObject o) {
        String wp = readString(o, "workplace");
        String id = readString(o, "itemId");
        if (wp == null || wp.isBlank() || id == null || id.isBlank()) {
            return null;
        }
        int tier = o.has("rarityTier") && !o.get("rarityTier").isJsonNull() ? o.get("rarityTier").getAsInt() : 0;
        boolean def = o.has("defaultUnlocked") && !o.get("defaultUnlocked").isJsonNull() && o.get("defaultUnlocked").getAsBoolean();
        int resCost = 25;
        if (o.has("resourceCost") && !o.get("resourceCost").isJsonNull()) {
            resCost = Math.max(1, o.get("resourceCost").getAsInt());
        }
        int ticks = 200;
        if (o.has("ticks") && !o.get("ticks").isJsonNull()) {
            ticks = Math.max(1, o.get("ticks").getAsInt());
        }
        long maxSt = AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX;
        if (o.has("maxStorage") && !o.get("maxStorage").isJsonNull()) {
            maxSt = Math.max(1L, o.get("maxStorage").getAsLong());
        }
        return new UnlockLine(wp, id, tier, def, resCost, ticks, maxSt);
    }

    @Nullable
    private static String readString(@Nonnull JsonObject o, @Nonnull String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }

    /** Gold coins charged for this rarity tier (0 = free / default path). Floored to a multiple of 5; minimum paid tier is 100. */
    public static long goldCoinsForRarityTier(int rarityTier) {
        if (rarityTier <= 0) {
            return 0L;
        }
        long raw = 100L + (long) (rarityTier - 1) * 45L;
        return (raw / 5L) * 5L;
    }

    @Nonnull
    public List<UnlockLine> linesForWorkplace(@Nonnull String constructionId) {
        String cid = constructionId.trim();
        List<UnlockLine> out = new ArrayList<>();
        for (UnlockLine l : allLines) {
            if (l.workplace().equals(cid)) {
                out.add(l);
            }
        }
        return Collections.unmodifiableList(out);
    }

    @Nonnull
    public Map<String, UnlockLine> byItemId(@Nonnull String constructionId) {
        Map<String, UnlockLine> m = new LinkedHashMap<>();
        for (UnlockLine l : linesForWorkplace(constructionId)) {
            m.put(l.itemId().toLowerCase(Locale.ROOT), l);
        }
        return m;
    }

    @Nullable
    public UnlockLine findLine(@Nonnull String constructionId, @Nonnull String itemId) {
        String k = itemId.trim().toLowerCase(Locale.ROOT);
        return byItemId(constructionId).get(k);
    }
}
