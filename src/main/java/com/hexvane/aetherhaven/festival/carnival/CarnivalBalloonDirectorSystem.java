package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Advances balloon spawn cadence once per carnival town (driven off the wheel face entity so it runs even with no
 * balloons yet).
 */
public final class CarnivalBalloonDirectorSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(CarnivalWheelFaceComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        CarnivalWheelFaceComponent face =
            archetypeChunk.getComponent(index, CarnivalWheelFaceComponent.getComponentType());
        if (face == null) {
            return;
        }
        UUID townId = face.getTownId();
        if (townId == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town == null || !CarnivalIds.FESTIVAL_ID.equals(town.getActiveFestivalId())) {
            return;
        }
        CarnivalBalloonSession session = CarnivalBalloonSessionIndex.get(townId);
        if (session == null || session.getPhase() != CarnivalBalloonSession.Phase.PLAYING) {
            return;
        }
        session.addSpawnCooldown(dt);
        if (session.getSpawned() >= CarnivalIds.BALLOON_TOTAL || session.getSpawnCooldown() > 0f) {
            return;
        }
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        FestivalDefinition festival = plugin.getFestivalCatalog().get(town.getActiveFestivalId());
        if (square == null || festival == null) {
            return;
        }
        CarnivalBalloonSpawnService.scheduleSpawn(world, townId, square, festival);
        session.setSpawnCooldown(CarnivalIds.BALLOON_SPAWN_INTERVAL);
    }
}
