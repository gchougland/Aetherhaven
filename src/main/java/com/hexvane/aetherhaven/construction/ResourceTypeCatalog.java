package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.ui.UiMaterialLabels;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Loaded Hytale resource types for plot creator material pickers. */
public final class ResourceTypeCatalog {
    private static final List<String> PRIORITY_IDS = List.of(
        "Wood_All",
        "Rock",
        "Soils",
        "Rubble",
        "Wood_Trunk"
    );

    private ResourceTypeCatalog() {}

    @Nonnull
    public static List<Entry> listForPicker() {
        Map<String, ResourceType> assets = ResourceType.getAssetMap().getAssetMap();
        Set<String> seen = new LinkedHashSet<>();
        List<Entry> out = new ArrayList<>();

        for (String priorityId : PRIORITY_IDS) {
            ResourceType asset = assets.get(priorityId);
            if (asset == null || asset.getId() == null || asset.getId().isBlank()) {
                continue;
            }
            String id = asset.getId().trim();
            if (!UiMaterialLabels.hasResourceTypeLangLabel(id)) {
                continue;
            }
            if (seen.add(id)) {
                out.add(toEntry(id, asset));
            }
        }

        List<Entry> rest = new ArrayList<>();
        for (Map.Entry<String, ResourceType> pair : assets.entrySet()) {
            ResourceType asset = pair.getValue();
            if (asset == null || asset.getId() == null || asset.getId().isBlank()) {
                continue;
            }
            String id = asset.getId().trim();
            if (!seen.add(id)) {
                continue;
            }
            if (!UiMaterialLabels.hasResourceTypeLangLabel(id)) {
                continue;
            }
            rest.add(toEntry(id, asset));
        }
        rest.sort(Comparator.comparing(e -> e.displayName().toLowerCase(Locale.ROOT)));
        out.addAll(rest);
        return out;
    }

    @Nonnull
    private static Entry toEntry(@Nonnull String id, @Nonnull ResourceType asset) {
        String icon = asset.getIcon();
        String iconPath = icon != null && !icon.isBlank() ? icon.trim() : null;
        String displayName = UiMaterialLabels.displayLabelFor(MaterialRequirement.ofResourceType(id, 1));
        return new Entry(id, displayName, iconPath);
    }

    public record Entry(@Nonnull String id, @Nonnull String displayName, @Nullable String iconPath) {}
}
