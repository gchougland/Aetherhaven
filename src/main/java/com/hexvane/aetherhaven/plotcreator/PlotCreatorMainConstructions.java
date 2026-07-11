package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Canonical construction ids that variant buildings may {@code countsAsConstructionId} point to.
 *
 * <p>Primary source is {@link ConstructionCatalog} (every pack's buildings). Optional
 * {@code plot_creator_main_constructions.json} supplies preferred order and label lang keys for known
 * Aetherhaven bases. Other mods' canonical constructions appear automatically when both mods are loaded.
 */
public final class PlotCreatorMainConstructions {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE = "Server/Aetherhaven/plot_creator_main_constructions.json";

    /**
     * @param id construction id
     * @param labelLang optional message id for the dropdown; when null, {@code fallbackLabel} is shown raw
     * @param fallbackLabel plain display name used when {@code labelLang} is null
     */
    public record Entry(@Nonnull String id, @Nullable String labelLang, @Nonnull String fallbackLabel) {}

    private static volatile List<Entry> cachedLabelOverrides = List.of();

    private PlotCreatorMainConstructions() {}

    /** True when {@code constructionId} is a valid Variant Of target in the live catalog (or JSON fallback). */
    public static boolean isKnownMainConstruction(@Nullable AetherhavenPlugin plugin, @Nonnull String constructionId) {
        String id = constructionId.trim();
        if (id.isEmpty()) {
            return false;
        }
        if (plugin != null) {
            return isEligibleVariantBase(plugin.getConstructionCatalog().get(id));
        }
        for (Entry entry : labelOverrides(PlotCreatorMainConstructions.class.getClassLoader())) {
            if (id.equals(entry.id())) {
                return true;
            }
        }
        return false;
    }

    /** @deprecated Prefer {@link #isKnownMainConstruction(AetherhavenPlugin, String)}. */
    @Deprecated
    public static boolean isKnownMainConstruction(@Nonnull String constructionId) {
        return isKnownMainConstruction(AetherhavenPlugin.get(), constructionId);
    }

    @Nonnull
    public static ObjectArrayList<DropdownEntryInfo> dropdownEntries(@Nullable AetherhavenPlugin plugin) {
        ObjectArrayList<DropdownEntryInfo> out = new ObjectArrayList<>();
        for (Entry entry : variantBaseEntries(plugin)) {
            LocalizableString label =
                entry.labelLang() != null
                    ? LocalizableString.fromMessageId(entry.labelLang())
                    : LocalizableString.fromString(entry.fallbackLabel());
            out.add(new DropdownEntryInfo(label, entry.id()));
        }
        return out;
    }

    /** @deprecated Prefer {@link #dropdownEntries(AetherhavenPlugin)}. */
    @Deprecated
    @Nonnull
    public static ObjectArrayList<DropdownEntryInfo> dropdownEntries(@Nonnull ClassLoader classLoader) {
        return dropdownEntries(AetherhavenPlugin.get());
    }

    @Nonnull
    public static List<Entry> variantBaseEntries(@Nullable AetherhavenPlugin plugin) {
        Map<String, Entry> labelById = new LinkedHashMap<>();
        for (Entry override : labelOverrides(PlotCreatorMainConstructions.class.getClassLoader())) {
            labelById.put(override.id(), override);
        }
        if (plugin == null) {
            return List.copyOf(labelById.values());
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        List<Entry> preferred = new ArrayList<>();
        for (Entry override : labelById.values()) {
            ConstructionDefinition def = catalog.get(override.id());
            if (isEligibleVariantBase(def)) {
                preferred.add(override);
            }
        }
        List<Entry> extras = new ArrayList<>();
        for (ConstructionDefinition def : catalog.list()) {
            if (!isEligibleVariantBase(def)) {
                continue;
            }
            String id = def.getId().trim();
            if (labelById.containsKey(id)) {
                continue;
            }
            String lang = def.getDisplayNameLangKey();
            extras.add(new Entry(id, lang, def.getDisplayName()));
        }
        extras.sort(Comparator.comparing(e -> e.fallbackLabel().toLowerCase(Locale.ROOT)));
        List<Entry> out = new ArrayList<>(preferred.size() + extras.size());
        out.addAll(preferred);
        out.addAll(extras);
        return out;
    }

    /** Canonical bases only: not a variant, not decoration, not a wall segment. */
    public static boolean isEligibleVariantBase(@Nullable ConstructionDefinition def) {
        if (def == null || def.getId() == null || def.getId().isBlank()) {
            return false;
        }
        if (def.isDecorationPlot() || def.isWallSegment()) {
            return false;
        }
        String countsAs = def.getCountsAsConstructionIdRaw();
        return countsAs == null || countsAs.isBlank();
    }

    @Nonnull
    private static List<Entry> labelOverrides(@Nonnull ClassLoader classLoader) {
        if (!cachedLabelOverrides.isEmpty()) {
            return cachedLabelOverrides;
        }
        synchronized (PlotCreatorMainConstructions.class) {
            if (!cachedLabelOverrides.isEmpty()) {
                return cachedLabelOverrides;
            }
            cachedLabelOverrides = loadLabelOverrides(classLoader);
            return cachedLabelOverrides;
        }
    }

    @Nonnull
    private static List<Entry> loadLabelOverrides(@Nonnull ClassLoader classLoader) {
        try (InputStream in = classLoader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.atWarning().log("Missing %s", RESOURCE);
                return List.of();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("mainConstructions");
            if (arr == null) {
                LOGGER.atWarning().log("%s missing mainConstructions array", RESOURCE);
                return List.of();
            }
            ObjectArrayList<Entry> loaded = new ObjectArrayList<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                String id = stringField(obj, "id");
                String labelLang = stringField(obj, "labelLang");
                if (id == null || labelLang == null) {
                    LOGGER.atWarning().log("%s entry missing id or labelLang: %s", RESOURCE, obj);
                    continue;
                }
                loaded.add(new Entry(id, labelLang, id));
            }
            return Collections.unmodifiableList(loaded);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load %s", RESOURCE);
            return List.of();
        }
    }

    @Nullable
    private static String stringField(@Nonnull JsonObject obj, @Nonnull String key) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return null;
        }
        String value = el.getAsString().trim();
        return value.isEmpty() ? null : value;
    }
}
