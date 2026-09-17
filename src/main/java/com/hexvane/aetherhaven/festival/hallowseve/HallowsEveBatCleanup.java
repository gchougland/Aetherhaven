package com.hexvane.aetherhaven.festival.hallowseve;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reconciles temporary festival flocks, including bats left by older versions. */
final class HallowsEveBatCleanup {
    private HallowsEveBatCleanup() {}

    static boolean isFestivalBat(HallowsEveBatComponent marker, NPCEntity npc) {
        return marker != null || (npc != null && HallowsEveIds.BAT_NPC_ROLE.equals(npc.getRoleName()));
    }

    // Called outside Store.tick. Snapshot first so archetype compaction cannot skip a removal.
    static Map<UUID, Integer> reconcile(Store<EntityStore> store, Set<UUID> activeTowns,
        ComponentType<EntityStore, NPCEntity> npcType) {
        Map<UUID, Integer> counts = new HashMap<>();
        List<Ref<EntityStore>> remove = new ArrayList<>();
        var markerType = HallowsEveBatComponent.getComponentType();
        var temporaryType = store.getRegistry().getNonSerializedComponentType();
        store.forEachChunk(Query.or(markerType, npcType), (chunk, commands) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var marker = chunk.getComponent(i, markerType);
                var npc = chunk.getComponent(i, npcType);
                if (!isFestivalBat(marker, npc)) continue;
                UUID townId = marker != null ? marker.getTownId() : null;
                int count = counts.getOrDefault(townId, 0);
                if (townId == null || !activeTowns.contains(townId)
                    || chunk.getComponent(i, temporaryType) == null || count >= HallowsEveIds.BAT_COUNT) {
                    remove.add(chunk.getReferenceTo(i));
                } else {
                    counts.put(townId, count + 1);
                }
            }
        });
        remove.forEach(ref -> {
            if (ref.isValid()) store.removeEntity(ref, RemoveReason.REMOVE);
        });
        return counts;
    }

    static void removeTown(Store<EntityStore> store, UUID townId) {
        List<Ref<EntityStore>> remove = new ArrayList<>();
        var type = HallowsEveBatComponent.getComponentType();
        store.forEachChunk(type, (chunk, commands) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var marker = chunk.getComponent(i, type);
                if (marker != null && townId.equals(marker.getTownId())) remove.add(chunk.getReferenceTo(i));
            }
        });
        remove.forEach(ref -> {
            if (ref.isValid()) store.removeEntity(ref, RemoveReason.REMOVE);
        });
    }
}
