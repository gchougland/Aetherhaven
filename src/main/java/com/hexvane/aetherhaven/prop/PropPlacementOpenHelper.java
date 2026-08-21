package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.ui.PropPlacementPage;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Validates the held prop item and opens (or resumes) a {@link PropPlacementPage} session. */
public final class PropPlacementOpenHelper {
    private PropPlacementOpenHelper() {}

    @Nullable
    public static CustomUIPage tryOpen(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor,
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionContext context
    ) {
        Store<EntityStore> store = ref.getStore();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        PropPlacementSession existing = PropPlacementSessions.get(uc.getUuid());
        if (existing != null && existing.getWorld().getName().equals(world.getName())) {
            return new PropPlacementPage(playerRef, existing);
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return null;
        }
        ItemStack held = context.getHeldItem();
        String propId = ItemStack.isEmpty(held) ? null : PropItemMetadata.readPropId(held);
        if (propId == null && !ItemStack.isEmpty(held)) {
            String itemId = held.getItemId();
            if (itemId != null && PropVirtualItemRegistry.isVirtualId(itemId)) {
                propId = PropVirtualItemRegistry.getPropIdFromVirtualId(itemId);
            } else {
                propId = PropShopItemIds.propIdFromItemId(itemId);
            }
        }
        if (propId == null || propId.isBlank()) {
            playerRef.sendMessage(Message.translation("aetherhaven_props.aetherhaven.prop.placement.error.notHoldingProp"));
            return null;
        }
        if (plugin.getPropCatalog().get(propId) == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_props.aetherhaven.prop.placement.error.unknownProp"));
            return null;
        }
        Vector3i anchor = pickAnchor(store, ref, context);
        PropPlacementSession session = new PropPlacementSession(uc.getUuid(), world, propId, anchor, 0);
        PropPlacementSessions.put(uc.getUuid(), session);
        return new PropPlacementPage(playerRef, session);
    }

    @Nonnull
    private static Vector3i pickAnchor(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull InteractionContext context
    ) {
        BlockPosition tb = context.getTargetBlock();
        if (tb != null) {
            // Target is the block under the crosshair; sit the prop on top of it (same idea as plot placement).
            return new Vector3i(tb.x, tb.y + 1, tb.z);
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d p = tc.getPosition();
            return new Vector3i((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
        }
        return new Vector3i(0, 0, 0);
    }
}
