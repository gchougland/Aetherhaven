package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/**
 * Periodically syncs plot placement building previews to players who walk into range while the placer UI is dismissed.
 */
public final class PlotPlacementSpectatorSyncSystem extends TickingSystem<EntityStore> {
    private static final float INTERVAL_SEC = 2.0f;

    private float timer;

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        timer += dt;
        if (timer < INTERVAL_SEC) {
            return;
        }
        timer = 0.0f;
        World world = store.getExternalData().getWorld();
        if (!world.isAlive()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PlotPlacementSessions.forEachActive(
            (placerUuid, session) -> {
                if (!world.getName().equals(session.getWorld().getName())) {
                    return;
                }
                ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
                if (def == null) {
                    PlotPlacementPreviewSync.hideSpectators(world, placerUuid, session);
                    return;
                }
                Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(session.getAnchor(), session.getPrefabYaw());
                PlotPlacementPreviewSync.syncSpectators(world, placerUuid, session, def, prefabOrigin, false);
            }
        );
    }
}
