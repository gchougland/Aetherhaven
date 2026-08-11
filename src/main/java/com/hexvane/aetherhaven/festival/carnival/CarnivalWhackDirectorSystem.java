package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Advances whack spawn cadence and ends rounds on the 60s timer. Runs as a world tick so it does not depend on
 * goblin entities being present.
 */
public final class CarnivalWhackDirectorSystem extends TickingSystem<EntityStore> {
    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }
        String worldName = world.getName();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (Map.Entry<UUID, CarnivalWhackSession> entry : CarnivalWhackSessionIndex.entries()) {
            UUID townId = entry.getKey();
            CarnivalWhackSession session = entry.getValue();
            if (session == null || session.getPhase() != CarnivalWhackSession.Phase.PLAYING) {
                continue;
            }
            TownRecord town = tm.getTown(townId);
            if (town == null
                || !CarnivalIds.FESTIVAL_ID.equals(town.getActiveFestivalId())
                || !worldName.equals(town.getWorldName())) {
                continue;
            }
            boolean timedOut = session.tickPlaying(dt);
            if (timedOut) {
                CarnivalWhackSpawnService.despawnAllForTown(world, townId);
                session.forceFinish();
                playFinishIfNeeded(store, session, town);
                continue;
            }
            PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
            FestivalDefinition festival = plugin.getFestivalCatalog().get(town.getActiveFestivalId());
            if (square == null || festival == null) {
                continue;
            }
            if (!session.tryReserveSpawn()) {
                continue;
            }
            CarnivalWhackSpawnService.scheduleSpawn(world, townId, square, festival);
        }
    }

    static void playFinishIfNeeded(
        @Nonnull Store<EntityStore> store,
        @Nonnull CarnivalWhackSession session,
        @Nonnull TownRecord town
    ) {
        if (!session.consumeFinishSfxPending()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        CarnivalAudio.playWhackFinish(store, CarnivalAudio.squareCenter(plugin, town));
        UUID playerUuid = session.getPlayerUuid();
        if (playerUuid != null) {
            CarnivalWhackClubUtil.removeAllWhackersForPlayer(store, playerUuid);
            CarnivalAnnounce.announceWhackHitCount(store, playerUuid, session.getHits(), session.getSpawned());
        }
        CarnivalResultSettlement.settleWhackAndPresent(store, town, session);
    }
}
