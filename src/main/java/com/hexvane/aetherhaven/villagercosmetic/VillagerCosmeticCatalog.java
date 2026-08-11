package com.hexvane.aetherhaven.villagercosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Loads {@code Server/Aetherhaven/VillagerCosmetics/cosmetics.json}. */
public final class VillagerCosmeticCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE = "Server/Aetherhaven/VillagerCosmetics/cosmetics.json";

    private final Map<String, VillagerCosmeticDefinition> byId;
    private final Map<String, VillagerCosmeticDefinition> byUnlockItemId;
    private final Set<String> headAccessoryModelPaths;
    private final Set<String> faceAccessoryModelPaths;
    private final Set<String> backAccessoryModelPaths;

    private VillagerCosmeticCatalog(
        @Nonnull Map<String, VillagerCosmeticDefinition> byId,
        @Nonnull Map<String, VillagerCosmeticDefinition> byUnlockItemId,
        @Nonnull Set<String> headAccessoryModelPaths,
        @Nonnull Set<String> faceAccessoryModelPaths,
        @Nonnull Set<String> backAccessoryModelPaths
    ) {
        this.byId = byId;
        this.byUnlockItemId = byUnlockItemId;
        this.headAccessoryModelPaths = headAccessoryModelPaths;
        this.faceAccessoryModelPaths = faceAccessoryModelPaths;
        this.backAccessoryModelPaths = backAccessoryModelPaths;
    }

    @Nonnull
    public static VillagerCosmeticCatalog empty() {
        return new VillagerCosmeticCatalog(Map.of(), Map.of(), Set.of(), Set.of(), Set.of());
    }

    @Nonnull
    public static VillagerCosmeticCatalog loadFromClasspath(@Nonnull ClassLoader classLoader) {
        try (InputStream in = classLoader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.atWarning().log("Villager cosmetics catalog missing: %s", RESOURCE);
                return empty();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("cosmetics");
            Map<String, VillagerCosmeticDefinition> byId = new LinkedHashMap<>();
            Map<String, VillagerCosmeticDefinition> byItem = new LinkedHashMap<>();
            Set<String> headModels = new LinkedHashSet<>();
            Set<String> faceModels = new LinkedHashSet<>();
            Set<String> backModels = new LinkedHashSet<>();
            if (arr != null) {
                for (JsonElement el : arr) {
                    if (el == null || !el.isJsonObject()) {
                        continue;
                    }
                    JsonObject o = el.getAsJsonObject();
                    String id = text(o, "id");
                    String slot = text(o, "slot");
                    String nameKey = text(o, "displayNameKey");
                    String model = text(o, "model");
                    String texture = text(o, "texture");
                    String unlockItemId = text(o, "unlockItemId");
                    if (id.isEmpty() || slot.isEmpty() || model.isEmpty() || unlockItemId.isEmpty()) {
                        continue;
                    }
                    VillagerCosmeticDefinition def =
                        new VillagerCosmeticDefinition(id, slot, nameKey, model, texture, unlockItemId);
                    byId.put(id, def);
                    byItem.put(unlockItemId, def);
                    if (VillagerCosmeticDefinition.SLOT_HEAD_ACCESSORY.equalsIgnoreCase(slot)) {
                        headModels.add(model);
                    } else if (VillagerCosmeticDefinition.SLOT_FACE_ACCESSORY.equalsIgnoreCase(slot)) {
                        faceModels.add(model);
                    } else if (VillagerCosmeticDefinition.SLOT_BACK_ACCESSORY.equalsIgnoreCase(slot)) {
                        backModels.add(model);
                    }
                }
            }
            // Folder prefixes count as their slot for strip/replace on base outfits.
            headModels.add("Cosmetics/Head/");
            faceModels.add("Cosmetics/Face_Accessories/");
            backModels.add("Cosmetics/Back/");
            LOGGER.atInfo().log("Loaded %s villager cosmetic(s)", byId.size());
            return new VillagerCosmeticCatalog(
                Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(byItem),
                Collections.unmodifiableSet(headModels),
                Collections.unmodifiableSet(faceModels),
                Collections.unmodifiableSet(backModels)
            );
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load villager cosmetics catalog");
            return empty();
        }
    }

    @Nonnull
    private static String text(@Nonnull JsonObject o, @Nonnull String key) {
        JsonElement el = o.get(key);
        return el == null || el.isJsonNull() ? "" : el.getAsString().trim();
    }

    @Nullable
    public VillagerCosmeticDefinition byId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return byId.get(id.trim());
    }

    @Nullable
    public VillagerCosmeticDefinition byUnlockItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        return byUnlockItemId.get(itemId.trim());
    }

    @Nonnull
    public List<VillagerCosmeticDefinition> all() {
        return List.copyOf(byId.values());
    }

    @Nonnull
    public List<VillagerCosmeticDefinition> unlockedInSlots(
        @Nonnull Set<String> unlockedIds,
        @Nonnull String slot
    ) {
        String slotKey = slot.trim();
        List<VillagerCosmeticDefinition> out = new ArrayList<>();
        for (String id : unlockedIds) {
            VillagerCosmeticDefinition def = byId(id);
            if (def != null && def.slot().equalsIgnoreCase(slotKey)) {
                out.add(def);
            }
        }
        return out;
    }

    @Nonnull
    public Set<String> slotsWithUnlocks(@Nonnull Set<String> unlockedIds) {
        Set<String> slots = new LinkedHashSet<>();
        for (String id : unlockedIds) {
            VillagerCosmeticDefinition def = byId(id);
            if (def != null) {
                slots.add(def.slot());
            }
        }
        return slots;
    }

    public boolean isHeadAccessoryModel(@Nullable String modelPath) {
        return matchesFolderOrKnown(modelPath, "Cosmetics/Head/", headAccessoryModelPaths);
    }

    public boolean isFaceAccessoryModel(@Nullable String modelPath) {
        return matchesFolderOrKnown(modelPath, "Cosmetics/Face_Accessories/", faceAccessoryModelPaths);
    }

    public boolean isBackAccessoryModel(@Nullable String modelPath) {
        return matchesFolderOrKnown(modelPath, "Cosmetics/Back/", backAccessoryModelPaths);
    }

    public boolean belongsToSlot(@Nonnull String slot, @Nullable String modelPath) {
        if (VillagerCosmeticDefinition.SLOT_HEAD_ACCESSORY.equalsIgnoreCase(slot)) {
            return isHeadAccessoryModel(modelPath);
        }
        if (VillagerCosmeticDefinition.SLOT_FACE_ACCESSORY.equalsIgnoreCase(slot)) {
            return isFaceAccessoryModel(modelPath);
        }
        if (VillagerCosmeticDefinition.SLOT_BACK_ACCESSORY.equalsIgnoreCase(slot)) {
            return isBackAccessoryModel(modelPath);
        }
        String slotLower = slot.toLowerCase(Locale.ROOT);
        for (VillagerCosmeticDefinition def : byId.values()) {
            if (def.slot().equalsIgnoreCase(slotLower) && def.model().equalsIgnoreCase(modelPath != null ? modelPath : "")) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFolderOrKnown(
        @Nullable String modelPath,
        @Nonnull String folderPrefix,
        @Nonnull Set<String> knownPaths
    ) {
        if (modelPath == null || modelPath.isBlank()) {
            return false;
        }
        String path = modelPath.trim();
        if (path.regionMatches(true, 0, folderPrefix, 0, folderPrefix.length())) {
            return true;
        }
        for (String known : knownPaths) {
            if (known.endsWith("/")) {
                continue;
            }
            if (known.equalsIgnoreCase(path)) {
                return true;
            }
        }
        return false;
    }
}
