package com.hexvane.aetherhaven.poi.tool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiMoveValidation;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared helpers for POI tool block interactions. */
public final class PoiToolInteractions {
    /** Max squared distance (block units) from clicked block to POI cell for selection. */
    private static final long SELECT_MAX_DIST_SQ = 9L;

    private PoiToolInteractions() {}

    public static boolean hasPoiToolPermission(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        Player player = accessor.getComponent(playerRef, Player.getComponentType());
        return player != null && player.hasPermission(AetherhavenConstants.PERMISSION_POI_TOOL);
    }

    public static boolean isPoiToolItem(@Nullable ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && AetherhavenConstants.POI_TOOL_ITEM_ID.equals(stack.getItemId());
    }

    public static void ensureState(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PoiToolPlayerComponent existing = commandBuffer.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        if (existing == null) {
            commandBuffer.addComponent(playerRef, PoiToolPlayerComponent.getComponentType(), new PoiToolPlayerComponent());
        }
    }

    @Nullable
    public static PoiEntry findNearestPoi(
        @Nonnull PoiRegistry registry,
        @Nonnull Vector3i targetBlock
    ) {
        int tx = targetBlock.x;
        int ty = targetBlock.y;
        int tz = targetBlock.z;
        long best = Long.MAX_VALUE;
        PoiEntry bestEntry = null;
        for (PoiEntry e : registry.allEntries()) {
            long dx = (long) e.getX() - tx;
            long dy = (long) e.getY() - ty;
            long dz = (long) e.getZ() - tz;
            long d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= SELECT_MAX_DIST_SQ && d2 < best) {
                best = d2;
                bestEntry = e;
            }
        }
        return bestEntry;
    }

    public static void handleSelect(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock,
        @Nonnull InteractionContext context
    ) {
        if (!hasPoiToolPermission(playerRef, commandBuffer)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ensureState(playerRef, commandBuffer);
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PoiEntry nearest = findNearestPoi(reg, targetBlock);
        PoiToolPlayerComponent state = commandBuffer.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        if (state == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (nearest == null) {
            state.setSelectedPoiId(null);
            send(playerRef, commandBuffer, Message.raw("No POI within range of that block."));
            return;
        }
        state.setSelectedPoiId(nearest.getId());
        send(
            playerRef,
            commandBuffer,
            Message.raw(
                "Selected POI "
                    + nearest.getId()
                    + " at "
                    + nearest.getX()
                    + ", "
                    + nearest.getY()
                    + ", "
                    + nearest.getZ()
            )
        );
    }

    public static void handleMove(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock,
        @Nonnull InteractionContext context
    ) {
        if (!hasPoiToolPermission(playerRef, commandBuffer)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ensureState(playerRef, commandBuffer);
        PoiToolPlayerComponent state = commandBuffer.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        if (state == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUID id = state.getSelectedPoiId();
        if (id == null) {
            send(playerRef, commandBuffer, Message.raw("No POI selected. Primary-click a POI first."));
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PoiEntry current = reg.get(id);
        if (current == null) {
            state.setSelectedPoiId(null);
            send(playerRef, commandBuffer, Message.raw("Selected POI no longer exists."));
            context.getState().state = InteractionState.Failed;
            return;
        }
        int nx = targetBlock.x;
        int ny = targetBlock.y;
        int nz = targetBlock.z;
        if (!PoiMoveValidation.matchesExpectedBlock(world, nx, ny, nz, current.getBlockTypeId())) {
            send(
                playerRef,
                commandBuffer,
                Message.raw(
                    "Target block must match expected type "
                        + current.getBlockTypeId()
                        + " for this POI."
                )
            );
            context.getState().state = InteractionState.Failed;
            return;
        }
        PoiEntry moved = current.copyWithPosition(nx, ny, nz);
        reg.replace(moved);
        send(playerRef, commandBuffer, Message.raw("Moved POI to " + nx + ", " + ny + ", " + nz + "."));
    }

    /**
     * Sets the autonomy leash / Seek goal for the selected POI (any block). Use on the POI anchor block again to
     * clear and fall back to the furniture cell center.
     */
    public static void handleSetInteractionTarget(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock,
        @Nonnull InteractionContext context
    ) {
        if (!hasPoiToolPermission(playerRef, commandBuffer)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ensureState(playerRef, commandBuffer);
        PoiToolPlayerComponent state = commandBuffer.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        if (state == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUID id = state.getSelectedPoiId();
        if (id == null) {
            send(playerRef, commandBuffer, Message.raw("No POI selected. Primary-click a POI block first."));
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PoiEntry current = reg.get(id);
        if (current == null) {
            state.setSelectedPoiId(null);
            send(playerRef, commandBuffer, Message.raw("Selected POI no longer exists."));
            context.getState().state = InteractionState.Failed;
            return;
        }
        int nx = targetBlock.x;
        int ny = targetBlock.y;
        int nz = targetBlock.z;
        if (nx == current.getX() && ny == current.getY() && nz == current.getZ()) {
            PoiEntry cleared = current.copyWithInteractionTarget(null, null, null);
            reg.replace(cleared);
            send(playerRef, commandBuffer, Message.raw("Cleared interaction target; NPCs use the POI block center."));
            return;
        }
        int standY = VillagerBlockUtil.findStandY(world, nx, nz, ny + 2);
        double wx = nx + 0.5;
        double wz = nz + 0.5;
        double wy = standY != Integer.MIN_VALUE ? standY + 0.02 : ny + 0.5;
        PoiEntry updated = current.copyWithInteractionTarget(wx, wy, wz);
        reg.replace(updated);
        send(
            playerRef,
            commandBuffer,
            Message.raw("Set interaction target to " + String.format("%.2f", wx) + ", " + String.format("%.2f", wy) + ", " + String.format("%.2f", wz) + ".")
        );
    }

    private static void send(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Message message
    ) {
        PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(message);
        }
    }
}
