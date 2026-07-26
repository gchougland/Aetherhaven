package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.construction.prefabmaterials.SuggestedResourceTypeResolver.Target;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

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
            JsonObject block = el.getAsJsonObject();
            if (block.has("filler") && !block.get("filler").isJsonNull()) {
                int filler = block.get("filler").getAsInt();
                if (filler != 0) {
                    continue;
                }
            }
            if (!block.has("name") || !block.get("name").isJsonPrimitive()) {
                continue;
            }
            String raw = block.get("name").getAsString();
            String itemId = PrefabBlockNormalizer.normalizeBlockToItemId(raw);
            if (itemId == null) {
                continue;
            }
            Target target = SuggestedResourceTypeResolver.resolve(itemId, conversions);
            switch (target) {
                case Target.Skip ignored -> {}
                case Target.SpecialtyItem specialty -> items.merge(specialty.itemId(), 1, Integer::sum);
                case Target.ResourceType resource -> resources.merge(resource.resourceTypeId(), 1, Integer::sum);
            }
        }
        return PrefabMaterialsGenerator.toSortedRequirements(items, resources);
    }
}
