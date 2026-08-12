package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.shopspot.ShopSpotItemDelivery;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Packaging wand action: turns the looked-at prop back into a held item, packaging any drift back to intended state. */
public final class PropPackageCommit {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double DEFAULT_MAX_DISTANCE = 8.0;

    private PropPackageCommit() {}

    /** Finds the prop along the player's look ray and packages it. Returns {@code false} when nothing was found. */
    public static boolean tryPackageLookedAtProp(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PropRegistry registry = PropWorldRegistries.getOrCreatePropRegistry(world, plugin);
        if (registry.size() == 0) {
            return false;
        }
        PropCatalog catalog = plugin.getPropCatalog();
        PropInstance instance =
            PropBoundsUtil.findPropAlongLookRay(registry, world, playerRef, store, catalog, DEFAULT_MAX_DISTANCE);
        if (instance == null) {
            return false;
        }
        PlayerRef lookPlayer = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (lookPlayer != null) {
            PropPackagingOverlay.clearFor(lookPlayer);
        }
        return packageInstance(world, plugin, instance, playerRef, store);
    }

    /**
     * Removes {@code instance} from the world and registry, returning its item to {@code playerRefOrNull} (or
     * dropping it at the prop's anchor when {@code null}, e.g. plot teardown packaging with no acting player).
     */
    public static boolean packageInstance(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PropInstance instance,
        @Nullable Ref<EntityStore> playerRefOrNull,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        PropCatalog catalog = plugin.getPropCatalog();
        PropDefinition def = catalog.get(instance.getPropId());
        if (def == null) {
            LOGGER.atWarning().log("Packaging prop %s with unknown definition; dropping from registry only", instance.getInstanceId());
            removeFromRegistry(world, plugin, instance);
            return true;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        if (buffer == null) {
            LOGGER.atWarning().log("Packaging prop %s: prefab %s not found; dropping from registry only", instance.getInstanceId(), def.getPrefabPath());
            removeFromRegistry(world, plugin, instance);
            return true;
        }
        Vector3i anchor = instance.getAnchor();
        if (!PropPrefabOps.isIntact(world, anchor, instance.getYaw(), buffer)) {
            LOGGER.atInfo().log("Packaging prop %s: some blocks were already altered; returning item anyway", instance.getInstanceId());
        }
        // Snapshot before mutating; entity/store writes must not happen mid-interaction tick.
        final PropDefinition defFinal = def;
        final IPrefabBuffer bufferFinal = buffer;
        final Vector3i anchorFinal = new Vector3i(anchor);
        final UUID instanceId = instance.getInstanceId();
        final Ref<EntityStore> playerRefSnapshot = playerRefOrNull;
        final var yaw = instance.getYaw();
        final List<UUID> linkedIds = new ArrayList<>(instance.getLinkedEntityIds());
        world.execute(
            () -> {
                PropEntityOps.removeLinkedEntities(world, instanceId, linkedIds, anchorFinal, yaw, bufferFinal);
                PropPrefabOps.removeSolidsOnly(world, anchorFinal, yaw, bufferFinal);
                removeFromRegistry(world, plugin, instance);
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                deliverItem(world, playerRefSnapshot, entityStore, defFinal, anchorFinal);
            }
        );
        return true;
    }

    private static void removeFromRegistry(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull PropInstance instance) {
        PropRegistry registry = PropWorldRegistries.getOrCreatePropRegistry(world, plugin);
        registry.remove(instance.getInstanceId());
    }

    private static void deliverItem(
        @Nonnull World world,
        @Nullable Ref<EntityStore> playerRefOrNull,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull PropDefinition def,
        @Nonnull Vector3i anchor
    ) {
        ItemStack stack;
        String shopItemId = PropShopItemIds.forPropId(def.getId());
        if (ItemModule.exists(shopItemId)) {
            stack = new ItemStack(shopItemId, 1);
        } else {
            stack = PropItemMetadata.withProp(new ItemStack(PropItemMetadata.PROP_ITEM_ID, 1), def.getId(), def.getDisplayName());
        }
        if (playerRefOrNull != null && accessor instanceof Store<EntityStore> store) {
            Player player = store.getComponent(playerRefOrNull, Player.getComponentType());
            if (player != null) {
                ShopSpotItemDelivery.grantAtShop(player, playerRefOrNull, accessor, null, stack, anchor);
                return;
            }
        }
        dropAtAnchor(accessor, stack, anchor);
    }

    private static void dropAtAnchor(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull ItemStack stack,
        @Nonnull Vector3i anchor
    ) {
        if (!(accessor instanceof Store<EntityStore> store)) {
            return;
        }
        Vector3d dropPos = new Vector3d(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5);
        com.hypixel.hytale.component.Holder<EntityStore>[] holders =
            com.hypixel.hytale.server.core.modules.entity.item.ItemComponent.generateItemDrops(
                store,
                java.util.List.of(stack),
                dropPos,
                com.hypixel.hytale.math.vector.Rotation3f.ZERO
            );
        for (com.hypixel.hytale.component.Holder<EntityStore> holder : holders) {
            store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
        }
    }
}
