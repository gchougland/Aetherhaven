package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.prefab.PrefabJsonStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
        Map<String, Integer> items = new HashMap<>();
        Map<String, Integer> resources = new HashMap<>();
        PrefabJsonStream.forEachBlock(prefabPath, (name, filler) -> countBlock(name, filler, items, resources));
        return PrefabMaterialItemIds.mergeNormalized(toSortedRequirements(items, resources));
    }

    @Nonnull
    public List<MaterialRequirement> generateFromPrefabJson(@Nonnull String prefabJson) {
        try (Reader reader = new StringReader(prefabJson)) {
            Map<String, Integer> items = new HashMap<>();
            Map<String, Integer> resources = new HashMap<>();
            PrefabJsonStream.forEachBlock(reader, (name, filler) -> countBlock(name, filler, items, resources));
            return PrefabMaterialItemIds.mergeNormalized(toSortedRequirements(items, resources));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse prefab JSON", e);
        }
    }

    private void countBlock(
        @Nullable String rawName,
        @Nullable Integer filler,
        @Nonnull Map<String, Integer> items,
        @Nonnull Map<String, Integer> resources
    ) {
        if (filler != null && filler != 0) {
            return;
        }
        if (rawName == null) {
            return;
        }
        String itemId = PrefabBlockNormalizer.normalizeBlockToItemId(rawName);
        if (itemId == null) {
            return;
        }
        ConversionRule rule = conversions.lookup(itemId);
        if (rule != null) {
            if (rule.skip) {
                return;
            }
            for (OutputSpec spec : rule.outputs) {
                addOutput(items, resources, spec, 1);
            }
            return;
        }
        items.merge(itemId, 1, Integer::sum);
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
