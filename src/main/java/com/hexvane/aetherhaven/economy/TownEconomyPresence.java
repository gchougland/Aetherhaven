package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Shared tax and wage presence rules. Inspect only the owning world's existing entity store. */
public final class TownEconomyPresence {
    private TownEconomyPresence() {}

    public static Set<UUID> onlinePlayers(Store<EntityStore> store) {
        Set<UUID> result = new HashSet<>();
        store.forEachChunk(Query.and(Player.getComponentType(), UUIDComponent.getComponentType()), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var identity = chunk.getComponent(i, UUIDComponent.getComponentType());
                if (identity != null) result.add(identity.getUuid());
            }
        });
        return result;
    }

    public static boolean hasMember(TownRecord town, Set<UUID> playersInTownWorld) {
        return playersInTownWorld.stream().anyMatch(town::hasMemberOrOwner);
    }

    /** Hot attendance path. A transferring player counts only after attachment to this exact store. */
    public static boolean hasMemberHere(Store<EntityStore> store, TownRecord town) {
        return hasMemberInStore(store,town,store.getExternalData().getWorld().getPlayerRefs());
    }

    /** Shared filter for a native player-reference snapshot; it never reads another world's components. */
    public static boolean hasMemberInStore(Store<EntityStore> store, TownRecord town,
            Iterable<com.hypixel.hytale.server.core.universe.PlayerRef> players) {
        if (town == null) return false;
        for (var player : players) {
            if (!town.hasMemberOrOwner(player.getUuid())) continue;
            var ref = player.getReference();
            if (ref != null && ref.isValid() && ref.getStore() == store) return true;
        }
        return false;
    }
}
