package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.plugin.AetherhavenFeatureBootstrap;
import com.hexvane.aetherhaven.workplace.RanchWorkService;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.builtin.tagset.TagSet;
import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.npc.AllNPCsLoadedEvent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.*;

/** Adds breeding birds to the native crate lookup without replacing any loaded group asset. */
public final class RanchCaptureCompatibility {
    public static final String CAPTURE_GROUP = "Capture_Crate";
    public static final String BIRD_GROUP = "Aetherhaven_Ranch_Breeding_Birds";
    private boolean active = true;

    private RanchCaptureCompatibility() {}

    public static void register(JavaPlugin plugin) {
        var integration = new RanchCaptureCompatibility();
        var events = plugin.getEventRegistry();
        // NPCPlugin rebuilds this derived lookup at normal priority for all three events.
        events.register(EventPriority.LAST, AllNPCsLoadedEvent.class, event -> integration.refresh());
        events.register(EventPriority.LAST, LoadedAssetsEvent.class, NPCGroup.class, integration::groupsLoaded);
        events.register(EventPriority.LAST, RemovedAssetsEvent.class, NPCGroup.class, integration::groupsRemoved);
        AetherhavenFeatureBootstrap.registerShutdownHook(() -> {
            integration.active = false;
            integration.refresh();
        });
    }

    private void groupsLoaded(LoadedAssetsEvent<String, NPCGroup, AssetMap<String, NPCGroup>> event) {
        refresh();
    }

    private void groupsRemoved(RemovedAssetsEvent<String, NPCGroup, AssetMap<String, NPCGroup>> event) {
        refresh();
    }

    private void refresh() {
        var npc = NPCPlugin.get();
        if (npc == null || TagSetPlugin.get() == null) return;
        var assets = NPCGroup.getAssetMap();
        var roles = npc.getBuilderManager().getNameToIndexMap();
        if (roles.isEmpty()) return;
        Map<String, ? extends TagSet> groups = assets.getAssetMap();
        if (active) groups = captureView(groups, roles.keySet().stream().filter(RanchWorkService::managedRole).toList());
        var indexes = new Object2IntOpenHashMap<String>();
        groups.keySet().forEach(name -> indexes.put(name, assets.getIndex(name)));
        TagSetPlugin.get(NPCGroup.class).putAssetSets(groups, indexes, roles);
    }

    /** Pure copy of the current asset view. Group exclusions keep native precedence. */
    static Map<String, TagSet> captureView(Map<String, ? extends TagSet> groups, Collection<String> managedRoles) {
        var result = new LinkedHashMap<String, TagSet>(groups);
        var original = groups.get(CAPTURE_GROUP);
        if (original == null) return result;
        String[] includes = groups.containsKey(BIRD_GROUP)
            ? append(original.getIncludedTagSets(), List.of(BIRD_GROUP)) : copy(original.getIncludedTagSets());
        var excludes = new LinkedHashSet<>(managedRoles);
        excludes.add("Aetherhaven_Ranch_*");
        result.put(CAPTURE_GROUP, new GroupView(original.getId(), includes, copy(original.getExcludedTagSets()),
            copy(original.getIncludedTags()), append(original.getExcludedTags(), excludes)));
        return result;
    }

    private static String[] copy(String[] values) { return values == null ? null : values.clone(); }
    private static String[] append(String[] values, Collection<String> additions) {
        var result = new LinkedHashSet<String>();
        if (values != null) Collections.addAll(result, values);
        result.addAll(additions);
        return result.toArray(String[]::new);
    }

    private record GroupView(String id, String[] includedTagSets, String[] excludedTagSets,
                             String[] includedTags, String[] excludedTags) implements TagSet {
        @Override public String getId() { return id; }
        @Override public String[] getIncludedTagSets() { return copy(includedTagSets); }
        @Override public String[] getExcludedTagSets() { return copy(excludedTagSets); }
        @Override public String[] getIncludedTags() { return copy(includedTags); }
        @Override public String[] getExcludedTags() { return copy(excludedTags); }
    }
}
