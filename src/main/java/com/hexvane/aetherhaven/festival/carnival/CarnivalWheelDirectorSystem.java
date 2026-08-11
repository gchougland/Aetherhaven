package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Advances wheel spin timers and keeps the face prop present for the whole carnival (including after join / chunk
 * load, when the in-memory session UUID may be gone).
 */
public final class CarnivalWheelDirectorSystem extends TickingSystem<EntityStore> {
    private static final float ENSURE_INTERVAL_SECONDS = 2.5f;
    private float ensureAccum;

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

        ensureAccum += dt;
        if (ensureAccum >= ENSURE_INTERVAL_SECONDS) {
            ensureAccum = 0f;
            // Soft ensure only — never force-loads chunks when the square is unloaded / empty.
            if (!world.getPlayerRefs().isEmpty()) {
                ensureFacesForCarnivalTowns(world, worldName, plugin, tm);
            }
        }

        for (Map.Entry<UUID, CarnivalWheelSession> entry : CarnivalWheelSessionIndex.entries()) {
            UUID townId = entry.getKey();
            CarnivalWheelSession session = entry.getValue();
            if (session == null || session.getPhase() != CarnivalWheelSession.Phase.SPINNING) {
                continue;
            }
            TownRecord town = tm.getTown(townId);
            if (town == null || !CarnivalIds.FESTIVAL_ID.equals(town.getActiveFestivalId())) {
                session.clearGameplay();
                continue;
            }
            if (!worldName.equals(town.getWorldName())) {
                continue;
            }
            session.addSpinElapsed(dt);
            session.setTickSfxAccum(session.getTickSfxAccum() + dt);
            // Tick sounds follow spin speed: quick clicks early, slower as it coasts.
            float progress = Math.min(1f, session.getSpinElapsed() / Math.max(0.01f, session.getSpinDuration()));
            float tickInterval = CarnivalIds.WHEEL_TICK_SFX_INTERVAL * (0.55f + progress * 1.8f);
            if (session.getTickSfxAccum() >= tickInterval) {
                session.setTickSfxAccum(0f);
                CarnivalAudio.playWheelTick(store, facePosition(store, session));
            }
            if (session.isSpinComplete()) {
                session.finishSpin(session.currentRoll());
                CarnivalAnnounce.announceWheelColor(
                    store,
                    town,
                    session.didWin(),
                    CarnivalAudio.squareCenter(plugin, town)
                );
            }
        }
    }

    private static void ensureFacesForCarnivalTowns(
        @Nonnull World world,
        @Nonnull String worldName,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm
    ) {
        for (TownRecord town : tm.allTowns()) {
            if (town == null
                || !CarnivalIds.FESTIVAL_ID.equals(town.getActiveFestivalId())
                || !worldName.equals(town.getWorldName())) {
                continue;
            }
            PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
            FestivalDefinition festival = plugin.getFestivalCatalog().get(town.getActiveFestivalId());
            if (square == null || festival == null) {
                continue;
            }
            CarnivalWheelPlacementService.ensurePresent(world, town.getTownId(), square, festival);
        }
    }

    @Nullable
    private static Vector3d facePosition(@Nonnull Store<EntityStore> store, @Nonnull CarnivalWheelSession session) {
        UUID faceUuid = session.getFaceEntityUuid();
        if (faceUuid == null) {
            return null;
        }
        Ref<EntityStore> faceRef = store.getExternalData().getRefFromUUID(faceUuid);
        if (faceRef == null || !faceRef.isValid()) {
            return null;
        }
        TransformComponent tc = store.getComponent(faceRef, TransformComponent.getComponentType());
        return tc != null ? tc.getPosition() : null;
    }
}
