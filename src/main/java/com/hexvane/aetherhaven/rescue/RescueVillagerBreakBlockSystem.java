package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
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
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town =
            TownPlayerResolution.resolveAffiliatedTownAtBlock(tm, world.getName(), pu.getUuid(), pos.x, pos.z);
        if (town == null) {
            return;
        }
        if (town.hasQuestCompleted(trigger.rescueQuestId())) {
            return;
        }
        UUID townId = town.getTownId();
        UUID breakerUuid = pu.getUuid();
        world.execute(() -> {
            Store<EntityStore> liveStore = world.getEntityStore().getStore();
            TownRecord liveTown = tm.getTown(townId);
            if (liveTown == null || liveTown.hasQuestCompleted(trigger.rescueQuestId())) {
                return;
            }
            RescueVillagerSpawnService.trySpawnAfterBlockBroken(world, liveStore, liveTown, pos, breakerUuid, trigger);
        });
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
