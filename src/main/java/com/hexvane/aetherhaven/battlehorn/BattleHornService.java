package com.hexvane.aetherhaven.battlehorn;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.patrol.GuardFollowPlayerSystem;
import com.hexvane.aetherhaven.rts.RtsGuardDirectory;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class BattleHornService {
    private BattleHornService() {}

    /** Summons all loaded hired guards in the player's town to follow them. */
    public static void callGuards(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull AetherhavenPlugin plugin
    ) {
        Store<EntityStore> store = commandBuffer.getStore();
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return;
        }
        UUID playerUuid = pu.getUuid();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, playerRef, tm);
        if (town == null || !town.hasMemberOrOwner(playerUuid)) {
            return;
        }

        List<Ref<EntityStore>> guards = RtsGuardDirectory.livingGuardRefs(town, store);
        for (Ref<EntityStore> guardRef : guards) {
            GuardFollowPlayerSystem.startFollow(guardRef, commandBuffer, store, playerUuid);
        }
    }

    @Nullable
    public static TownRecord resolvePlayerTown(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return null;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, playerRef, tm);
        if (town == null || !town.hasMemberOrOwner(pu.getUuid())) {
            return null;
        }
        return town;
    }
}
