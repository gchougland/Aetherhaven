package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.construction.prefabmaterials.SuggestedResourceTypeResolver.Target;
import com.hexvane.aetherhaven.prefab.PrefabJsonStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Counts prefab blocks into simplified resource type build costs. */
public final class SuggestedResourceMaterialsGenerator {
    private final PrefabMaterialConversionTable conversions;

    public SuggestedResourceMaterialsGenerator(@Nonnull PrefabMaterialConversionTable conversions) {
        this.conversions = conversions;
    }

    @Nonnull
    public static SuggestedResourceMaterialsGenerator fromClasspath(@Nonnull ClassLoader classLoader) {
        return new SuggestedResourceMaterialsGenerator(PrefabMaterialConversionTable.loadFromClasspath(classLoader));
    }

    /** Apply the plot creator's conversion rules to authored or full-blueprint costs, merging before scaling. */
    @Nonnull
    public List<MaterialRequirement> simplifyRequirements(@Nonnull List<MaterialRequirement> source) {
        Map<String, Integer> items = new HashMap<>();
        Map<String, Integer> resources = new HashMap<>();
        for (MaterialRequirement requirement : source) {
            if (requirement.getCount() <= 0) continue;
            String resource = requirement.getResourceTypeId();
            if (resource != null && !resource.isBlank()) {
                String canonical = SuggestedResourceTypeResolver.canonicalizeResourceTypeId(resource);
                resources.merge(canonical != null ? canonical : resource, requirement.getCount(), Integer::sum);
                continue;
            }
            String item = requirement.getItemId();
            if (item == null || item.isBlank()) continue;
            String normalized = PrefabBlockNormalizer.normalizeBlockToItemId(item);
            if (normalized == null) continue;
            switch (SuggestedResourceTypeResolver.resolve(normalized, conversions)) {
                case Target.Skip ignored -> {}
                case Target.SpecialtyItem specialty -> items.merge(specialty.itemId(), requirement.getCount(), Integer::sum);
                case Target.ResourceType type -> resources.merge(type.resourceTypeId(), requirement.getCount(), Integer::sum);
            }
        }
        return PrefabMaterialsGenerator.toSortedRequirements(items, resources);
    }

    @Nonnull
    public List<MaterialRequirement> generateFromPrefabPath(@Nonnull Path prefabPath) throws IOException {
        Map<String, Integer> items = new HashMap<>();
        Map<String, Integer> resources = new HashMap<>();
        PrefabJsonStream.forEachBlock(prefabPath, (name, filler) -> countBlock(name, filler, items, resources));
        return PrefabMaterialsGenerator.toSortedRequirements(items, resources);
    }

    @Nonnull
    public List<MaterialRequirement> generateFromPrefabJson(@Nonnull String prefabJson) {
        try (Reader reader = new StringReader(prefabJson)) {
            Map<String, Integer> items = new HashMap<>();
            Map<String, Integer> resources = new HashMap<>();
            PrefabJsonStream.forEachBlock(reader, (name, filler) -> countBlock(name, filler, items, resources));
            return PrefabMaterialsGenerator.toSortedRequirements(items, resources);
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
        Target target = SuggestedResourceTypeResolver.resolve(itemId, conversions);
        switch (target) {
            case Target.Skip ignored -> {}
            case Target.SpecialtyItem specialty -> items.merge(specialty.itemId(), 1, Integer::sum);
            case Target.ResourceType resource -> resources.merge(resource.resourceTypeId(), 1, Integer::sum);
        }
    }
}
