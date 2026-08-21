package com.hexvane.aetherhaven.blockpalette;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Mutable catalog of block palettes and optional cross-mod {@link BlockPaletteRemapGroup}s.
 *
 * <p>Loads every JSON under {@code Server/Aetherhaven/BlockPalettes/} from all asset packs (or the
 * classpath in tests). Other mods can ship JSON there or call {@link #register} /
 * {@link #registerRemapGroup} at startup.
 */
public final class BlockPaletteCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private final ConcurrentHashMap<String, BlockPaletteDefinition> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockPaletteRemapGroup> remapGroupsById = new ConcurrentHashMap<>();
    /** Palette ids per category in registration order (UI order). */
    private final ConcurrentHashMap<String, List<String>> categoryPaletteIds = new ConcurrentHashMap<>();
    private final LinkedHashSet<String> categoryOrder = new LinkedHashSet<>();

    private BlockPaletteCatalog() {}

    @Nonnull
    public static BlockPaletteCatalog empty() {
        return new BlockPaletteCatalog();
    }

    @Nonnull
    public static BlockPaletteCatalog loadFromClasspath(@Nonnull ClassLoader cl) {
        return loadFromAssetPacksOrClasspath(cl);
    }

    @Nonnull
    public static BlockPaletteCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        BlockPaletteCatalog catalog = new BlockPaletteCatalog();
        List<PackJsonFile> packFiles =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(BlockPalettePaths.PACK_RELATIVE);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    catalog.mergeJson(in, f.packName() + ":" + f.absolutePath());
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log("Failed to load block palette file %s", f.absolutePath());
                }
            }
        } else {
            for (String path : ClasspathResourceScanner.listJsonFiles(classLoader, BlockPalettePaths.packPrefix())) {
                try (InputStream in = classLoader.getResourceAsStream(path)) {
                    if (in == null) {
                        continue;
                    }
                    catalog.mergeJson(in, path);
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log("Failed to load block palette file %s", path);
                }
            }
        }
        if (!catalog.byId.isEmpty()) {
            LOGGER.atInfo().log(
                "Loaded %s block palette(s) in %s categor(ies), %s remap group(s)",
                catalog.byId.size(),
                catalog.categoryOrder.size(),
                catalog.remapGroupsById.size()
            );
        }
        return catalog;
    }

    private void mergeJson(@Nonnull InputStream in, @Nonnull String label) {
        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            root = GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to parse block palette JSON %s", label);
            return;
        }
        if (root == null) {
            return;
        }
        mergeRemapGroups(root.getAsJsonArray("remapGroups"), label);
        mergeCategories(root.getAsJsonArray("categories"), label);
    }

    private void mergeRemapGroups(@Nullable JsonArray groups, @Nonnull String label) {
        if (groups == null) {
            return;
        }
        for (JsonElement el : groups) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject g = el.getAsJsonObject();
            String groupId = readString(g, "id");
            String category = readString(g, "category");
            if (groupId.isEmpty() || category.isEmpty()) {
                LOGGER.atWarning().log("Skipping remap group with missing id/category in %s", label);
                continue;
            }
            JsonArray variantsArr = g.getAsJsonArray("variants");
            if (variantsArr == null || variantsArr.isEmpty()) {
                LOGGER.atWarning().log("Skipping remap group %s with no variants (%s)", groupId, label);
                continue;
            }
            List<BlockPaletteRemapGroup.Variant> variants = new ArrayList<>();
            for (JsonElement vEl : variantsArr) {
                if (vEl == null || !vEl.isJsonObject()) {
                    continue;
                }
                JsonObject v = vEl.getAsJsonObject();
                String paletteId = readString(v, "id");
                if (paletteId.isEmpty()) {
                    paletteId = readString(v, "paletteId");
                }
                String displayName = readString(v, "displayName");
                String iconBlockId = readString(v, "iconBlockId");
                String blockPrefix = readString(v, "blockPrefix");
                String familyKey = readString(v, "familyKey");
                if (paletteId.isEmpty() || iconBlockId.isEmpty() || blockPrefix.isEmpty()) {
                    continue;
                }
                if (displayName.isEmpty()) {
                    displayName = paletteId;
                }
                if (familyKey.isEmpty()) {
                    familyKey = groupId + ":" + paletteId;
                }
                variants.add(
                    new BlockPaletteRemapGroup.Variant(paletteId, displayName, familyKey, iconBlockId, blockPrefix)
                );
            }
            if (variants.isEmpty()) {
                continue;
            }
            registerRemapGroup(new BlockPaletteRemapGroup(groupId, category, variants));
        }
    }

    private void mergeCategories(@Nullable JsonArray categories, @Nonnull String label) {
        if (categories == null) {
            return;
        }
        for (JsonElement catEl : categories) {
            if (catEl == null || !catEl.isJsonObject()) {
                continue;
            }
            JsonObject cat = catEl.getAsJsonObject();
            String categoryId = readString(cat, "id");
            if (categoryId.isEmpty()) {
                continue;
            }
            categoryOrder.add(categoryId);
            JsonArray palettes = cat.getAsJsonArray("palettes");
            if (palettes == null) {
                continue;
            }
            for (JsonElement pEl : palettes) {
                if (pEl == null || !pEl.isJsonObject()) {
                    continue;
                }
                JsonObject p = pEl.getAsJsonObject();
                String id = readString(p, "id");
                String displayName = readString(p, "displayName");
                String familyKey = readString(p, "familyKey");
                String iconBlockId = readString(p, "iconBlockId");
                String remapGroupId = readString(p, "remapGroupId");
                if (id.isEmpty() || iconBlockId.isEmpty()) {
                    continue;
                }
                if (familyKey.isEmpty()) {
                    familyKey = id;
                }
                if (displayName.isEmpty()) {
                    displayName = id;
                }
                register(
                    new BlockPaletteDefinition(
                        id,
                        categoryId,
                        displayName,
                        familyKey,
                        iconBlockId,
                        remapGroupId.isEmpty() ? null : remapGroupId
                    )
                );
            }
        }
    }

    @Nonnull
    private static String readString(@Nonnull JsonObject o, @Nonnull String key) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return "";
        }
        String s = el.getAsString();
        return s != null ? s.trim() : "";
    }

    /**
     * Registers (or replaces) a palette. Safe for other mods to call during their setup after Aetherhaven loads.
     */
    public void register(@Nonnull BlockPaletteDefinition def) {
        if (def.getId().isEmpty() || def.getCategory().isEmpty()) {
            return;
        }
        boolean wasNew = byId.put(def.getId(), def) == null;
        synchronized (categoryOrder) {
            categoryOrder.add(def.getCategory());
        }
        if (wasNew) {
            categoryPaletteIds
                .computeIfAbsent(def.getCategory(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(def.getId());
        }
    }

    /**
     * Registers a cross-mod remap group and unlockable palettes for each variant. Later packs / calls override
     * the same group id.
     */
    public void registerRemapGroup(@Nonnull BlockPaletteRemapGroup group) {
        if (group.getId().isEmpty() || group.getCategory().isEmpty() || group.getVariants().isEmpty()) {
            return;
        }
        remapGroupsById.put(group.getId(), group);
        synchronized (categoryOrder) {
            categoryOrder.add(group.getCategory());
        }
        for (BlockPaletteRemapGroup.Variant v : group.getVariants()) {
            register(v.toDefinition(group.getCategory(), group.getId()));
        }
    }

    @Nullable
    public BlockPaletteDefinition get(@Nonnull String id) {
        return byId.get(id.trim());
    }

    @Nullable
    public BlockPaletteRemapGroup getRemapGroup(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return remapGroupsById.get(id.trim());
    }

    @Nonnull
    public List<BlockPaletteRemapGroup> remapGroups() {
        return List.copyOf(remapGroupsById.values());
    }

    @Nonnull
    public List<String> ids() {
        return List.copyOf(byId.keySet());
    }

    @Nonnull
    public List<String> categoryOrder() {
        synchronized (categoryOrder) {
            return List.copyOf(categoryOrder);
        }
    }

    @Nonnull
    public List<BlockPaletteDefinition> forCategory(@Nonnull String category) {
        String cat = category.trim();
        List<String> ids = categoryPaletteIds.get(cat);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<BlockPaletteDefinition> list = new ArrayList<>();
        synchronized (ids) {
            for (String id : ids) {
                BlockPaletteDefinition def = byId.get(id);
                if (def != null) {
                    list.add(def);
                }
            }
        }
        return List.copyOf(list);
    }

    @Nonnull
    public Map<String, BlockPaletteDefinition> allById() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    /** True when this block type id is covered by a registered remap group. */
    public boolean isRemapGroupBlock(@Nonnull String blockTypeId) {
        return findRemapMatch(blockTypeId) != null;
    }

    @Nullable
    public RemapHit findRemapMatch(@Nonnull String blockTypeId) {
        BlockPaletteRemapGroup.Match best = null;
        BlockPaletteRemapGroup bestGroup = null;
        int bestLen = -1;
        for (BlockPaletteRemapGroup group : remapGroupsById.values()) {
            BlockPaletteRemapGroup.Match m = group.matchBlockTypeId(blockTypeId);
            if (m == null) {
                continue;
            }
            int len = m.variant().blockPrefix().length();
            if (len > bestLen) {
                bestLen = len;
                best = m;
                bestGroup = group;
            }
        }
        if (best == null || bestGroup == null) {
            return null;
        }
        return new RemapHit(bestGroup, best);
    }

    public record RemapHit(@Nonnull BlockPaletteRemapGroup group, @Nonnull BlockPaletteRemapGroup.Match match) {}
}
