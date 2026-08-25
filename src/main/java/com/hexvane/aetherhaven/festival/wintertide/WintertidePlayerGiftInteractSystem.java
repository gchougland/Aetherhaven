package com.hexvane.aetherhaven.festival.wintertide;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Puts a Use prompt on town members who are waiting for a Wintertide gift from another player. */
public final class WintertidePlayerGiftInteractSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), UUIDComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        UUIDComponent uc = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (ref == null || !ref.isValid() || uc == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null) {
            return;
        }
        // Visitors have no home town, so fall back to the festival they are standing in.
        TownRecord town = WintertideGiftService.resolveTown(ref, store, null);
        if (town == null || !WintertideGiftService.isWintertideActive(town)) {
            WintertidePlayerGiftInteractSync.clear(ref, commandBuffer);
            return;
        }
        WintertideSession session = WintertideSessionIndex.get(town.getTownId());
        if (session == null || !session.isLivePlayerGiftTarget(uc.getUuid())) {
            WintertidePlayerGiftInteractSync.clear(ref, commandBuffer);
            return;
        }
        WintertidePlayerGiftInteractSync.sync(ref, commandBuffer);
    }
}
