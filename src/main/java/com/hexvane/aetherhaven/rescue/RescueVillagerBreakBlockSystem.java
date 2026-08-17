package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** When a player breaks a registered trigger block, may spawn a rescue NPC for their town. */
public final class RescueVillagerBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final AetherhavenPlugin plugin;

    public RescueVillagerBreakBlockSystem(@Nonnull AetherhavenPlugin plugin) {
        super(BreakBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        RescueVillagerTrigger trigger = RescueVillagerTriggers.byBlockTypeId(event.getBlockType().getId());
        if (trigger == null) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i pos = event.getTargetBlock();
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUID breakerUuid = pu.getUuid();
        TownRecord town = RescueVillagerSpawnService.resolveTownForFieldRescue(breakerUuid, localTm, trigger);
        if (town == null) {
            return;
        }
        UUID townId = town.getTownId();
        world.execute(() -> {
            Store<EntityStore> liveStore = world.getEntityStore().getStore();
            TownRecord liveTown = AetherhavenWorldRegistries.getTownAcrossWorlds(townId, localTm);
            if (liveTown == null) {
                return;
            }
            List<TownRecord> affiliated =
                AetherhavenWorldRegistries.listTownsForPlayerAcrossWorlds(breakerUuid);
            if (RescueVillagerSpawnService.anyAffiliatedTownAlreadyHasRescue(affiliated, trigger)) {
                return;
            }
            RescueVillagerSpawnService.trySpawnAfterBlockBroken(
                world, liveStore, liveTown, pos, breakerUuid, trigger
            );
        });
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
