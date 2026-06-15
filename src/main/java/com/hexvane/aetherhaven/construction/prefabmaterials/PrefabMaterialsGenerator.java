package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Counts prefab blocks and applies conversion rules (mirrors {@code scripts/generate_prefab_materials.py}). */
public final class PrefabMaterialsGenerator {

    private final PrefabMaterialConversionTable conversions;

    public PrefabMaterialsGenerator(@Nonnull PrefabMaterialConversionTable conversions) {
        this.conversions = conversions;
    }

    @Nonnull
    public static PrefabMaterialsGenerator fromClasspath(@Nonnull ClassLoader classLoader) {
        return new PrefabMaterialsGenerator(PrefabMaterialConversionTable.loadFromClasspath(classLoader));
    }

    @Nonnull
    public List<MaterialRequirement> generateFromPrefabPath(@Nonnull Path prefabPath) throws IOException {
        String json = Files.readString(prefabPath, StandardCharsets.UTF_8);
        return generateFromPrefabJson(json);
    }

    @Nonnull
    public List<MaterialRequirement> generateFromPrefabJson(@Nonnull String prefabJson) {
        JsonObject data = JsonParser.parseString(prefabJson).getAsJsonObject();
        JsonArray blocks = data.getAsJsonArray("blocks");
        if (blocks == null) {
            throw new IllegalArgumentException("No blocks array in prefab");
        }
        Map<String, Integer> items = new HashMap<>();
        Map<String, Integer> resources = new HashMap<>();
        for (JsonElement el : blocks) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject b = el.getAsJsonObject();
            if (b.has("filler") && !b.get("filler").isJsonNull()) {
                int filler = b.get("filler").getAsInt();
                if (filler != 0) {
                    continue;
                }
            }
            if (!b.has("name") || !b.get("name").isJsonPrimitive()) {
                continue;
            }
            String raw = b.get("name").getAsString();
            String itemId = PrefabBlockNormalizer.normalizeBlockToItemId(raw);
            if (itemId == null) {
                continue;
            }
            ConversionRule rule = conversions.lookup(itemId);
            if (rule != null) {
                if (rule.skip) {
                    continue;
                }
                for (OutputSpec spec : rule.outputs) {
                    addOutput(items, resources, spec, 1);
                }
                continue;
            }
            items.merge(itemId, 1, Integer::sum);
        }
        return toSortedRequirements(items, resources);
    }

    private static void addOutput(
        @Nonnull Map<String, Integer> items,
        @Nonnull Map<String, Integer> resources,
        @Nonnull OutputSpec spec,
        int blockInstances
    ) {
        int total = spec.amount * blockInstances;
        if (total <= 0) {
            return;
        }
        if (spec.kind == OutputKind.RESOURCE) {
            resources.merge(spec.id, total, Integer::sum);
        } else {
            items.merge(spec.id, total, Integer::sum);
        }
    }

    @Nonnull
    static List<MaterialRequirement> toSortedRequirements(
        @Nonnull Map<String, Integer> items,
        @Nonnull Map<String, Integer> resources
    ) {
        record Entry(String kind, String id, int count) {}

        List<Entry> entries = new ArrayList<>();
        for (var e : items.entrySet()) {
            entries.add(new Entry("item", e.getKey(), e.getValue()));
        }
        for (var e : resources.entrySet()) {
            entries.add(new Entry("resource", e.getKey(), e.getValue()));
        }
        entries.sort(
            Comparator.<Entry>comparingInt(e -> -e.count)
                .thenComparing(e -> e.kind)
                .thenComparing(e -> e.id)
        );
        List<MaterialRequirement> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if ("resource".equals(e.kind)) {
                out.add(MaterialRequirement.ofResourceType(e.id, e.count));
            } else {
                out.add(MaterialRequirement.ofItem(e.id, e.count));
            }
        }
        return List.copyOf(out);
    }

    @Nonnull
    public PrefabMaterialConversionTable getConversions() {
        return conversions;
    }
}
