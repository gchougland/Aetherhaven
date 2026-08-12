package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Commits a validated prop placement: consumes the held prop item, pastes the prefab, and records the instance. */
public final class PropPlacementCommit {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PropPlacementCommit() {}

    /**
     * @return {@code true} on success. Fails (without side effects) if the prop is unknown, its prefab cannot be
     *     resolved, the destination is blocked, or the player no longer holds a matching prop item.
     */
    public static boolean commit(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ItemContainer playerInventory,
        @Nonnull String propId,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        PropCatalog catalog = plugin.getPropCatalog();
        PropDefinition def = catalog.get(propId);
        if (def == null) {
            return false;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        if (buffer == null) {
            LOGGER.atWarning().log("Prop %s has no resolvable prefab (%s)", def.getId(), def.getPrefabPath());
            return false;
        }
        if (!PropPrefabOps.canPlaceSolids(world, anchor, yaw, buffer)) {
            return false;
        }
        if (!consumeMatchingPropItem(playerInventory, def.getId())) {
            return false;
        }
        UUID instanceId = UUID.randomUUID();
        PropPrefabOps.pasteSolidsOnly(world, anchor, yaw, buffer);
        List<UUID> linked =
            PropEntityOps.pasteEntities(world, anchor, yaw, def.getPrefabPath(), buffer, instanceId);
        PropRegistry registry = PropWorldRegistries.getOrCreatePropRegistry(world, plugin);
        registry.add(new PropInstance(instanceId, def.getId(), anchor, yaw, linked));
        return true;
    }

    /**
     * Removes one {@link PropItemMetadata#PROP_ITEM_ID} stack tagged for {@code propId} from the player's inventory.
     * Mirrors {@code PlotTokenInventory}'s "match live stack, remove one" pattern since metadata-bearing stacks
     * cannot be matched with a plain {@link ItemContainer#removeItemStack}.
     */
    private static boolean consumeMatchingPropItem(@Nonnull ItemContainer inv, @Nonnull String propId) {
        String shopItemId = PropShopItemIds.forPropId(propId);
        for (short i = 0; i < inv.getCapacity(); i++) {
            ItemStack stack = inv.getItemStack(i);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            boolean match;
            if (PropItemMetadata.PROP_ITEM_ID.equals(stack.getItemId())) {
                match = PropItemMetadata.matchesProp(stack, propId);
            } else if (shopItemId.equals(stack.getItemId())) {
                match = true;
            } else {
                String fromItem = PropShopItemIds.propIdFromItemId(stack.getItemId());
                match = propId.equals(fromItem);
            }
            if (!match) {
                continue;
            }
            if (stack.getQuantity() <= 1) {
                if (inv.removeItemStack(stack).succeeded()) {
                    return true;
                }
            } else if (inv.removeItemStackFromSlot(i, stack, 1).succeeded()) {
                return true;
            }
        }
        return false;
    }
}
