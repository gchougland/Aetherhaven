package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.placement.PlotFootprintOverlayRefresh;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Every ~0.5s (10 ticks at 20 TPS), shows nearby prop outlines to players holding the packaging wand. Packet-only —
 * never writes to the {@link Store} (see class rule: no {@code Store} writes from tick systems).
 */
public final class PropPackagingWandTickSystem extends TickingSystem<EntityStore> {
    private static final float REFRESH_INTERVAL_SEC = 0.5f;
    private static final double LOOK_RAY_MAX_DISTANCE = 8.0;

    @Nonnull
    private static final Set<UUID> OVERLAY_ACTIVE = ConcurrentHashMap.newKeySet();

    @Nonnull
    private final AetherhavenPlugin plugin;

    private float timer;

    public PropPackagingWandTickSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        timer += dt;
        if (timer < REFRESH_INTERVAL_SEC) {
            return;
        }
        // Keep remainder so hitchy ticks do not stretch the gap past the overlay hold lifetime.
        timer -= REFRESH_INTERVAL_SEC;
        World world = store.getExternalData().getWorld();
        if (!world.isAlive()) {
            return;
        }
        store.forEachChunk(
            Query.and(Player.getComponentType(), PlayerRef.getComponentType(), UUIDComponent.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                tickChunk(world, store, archetypeChunk, commandBuffer);
            }
        );
    }

    private void tickChunk(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        for (int i = 0; i < archetypeChunk.size(); i++) {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
            PlayerRef playerRef = archetypeChunk.getComponent(i, PlayerRef.getComponentType());
            if (uc == null || playerRef == null) {
                continue;
            }
            UUID playerUuid = uc.getUuid();
            ItemStack held = InventoryComponent.getItemInHand(store, ref);
            boolean holdingWand =
                !ItemStack.isEmpty(held) && PropConstants.PACKAGING_WAND_ITEM_ID.equals(held.getItemId());
            if (!holdingWand) {
                if (OVERLAY_ACTIVE.remove(playerUuid)) {
                    PropPackagingOverlay.clearFor(playerRef);
                    PlotFootprintOverlayRefresh.afterClearDebugShapes(ref, commandBuffer);
                }
                continue;
            }
            OVERLAY_ACTIVE.add(playerUuid);
            PropRegistry registry = PropWorldRegistries.getOrCreatePropRegistry(world, plugin);
            if (registry.size() == 0) {
                PropPackagingOverlay.clearFor(playerRef);
                continue;
            }
            PropCatalog catalog = plugin.getPropCatalog();
            Transform look = TargetUtil.getLook(ref, store);
            PropInstance lookedAt =
                PropBoundsUtil.findPropAlongLookRay(registry, world, ref, store, catalog, LOOK_RAY_MAX_DISTANCE);
            PropPackagingOverlay.show(playerRef, registry, catalog, look.getPosition(), lookedAt);
        }
    }
}
