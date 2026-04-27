package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PathToolInteractions {
    private static final double NODE_PICK_RADIUS = 0.45;
    private static final double PICK_RAY_MAX = 128.0;
    private static final float ROTATE_STEP_DEG = 15f;

    private PathToolInteractions() {}

    public static boolean isPathToolItem(@Nullable ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && AetherhavenConstants.PATH_TOOL_ITEM_ID.equals(stack.getItemId());
    }

    public static boolean hasPathToolPermission(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        Player player = accessor.getComponent(playerRef, Player.getComponentType());
        return player != null && player.hasPermission(AetherhavenConstants.PERMISSION_PATH_TOOL);
    }

    public static void ensureState(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer.getComponent(playerRef, PathToolPlayerComponent.getComponentType()) == null) {
            commandBuffer.addComponent(playerRef, PathToolPlayerComponent.getComponentType(), new PathToolPlayerComponent());
        }
    }

    @Nonnull
    public static Vector3d blockTopCenter(@Nonnull Vector3i b, double yOffsetBlocks) {
        return new Vector3d(
            b.getX() + 0.5,
            b.getY() + 0.5 + yOffsetBlocks,
            b.getZ() + 0.5
        );
    }

    public static void handleAddNode(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock,
        @Nonnull InteractionContext context,
        @Nonnull Store<EntityStore> store
    ) {
        if (!hasPathToolPermission(playerRef, commandBuffer) || !isPathToolItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ensureState(playerRef, commandBuffer);
        PathToolPlayerComponent st = commandBuffer.getComponent(playerRef, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Transform look = TargetUtil.getLook(playerRef, store);
        Vector3d origin = look.getPosition();
        Vector3d dir = look.getDirection();
        @Nullable
        PathToolNode looked = PathToolRayPick.pickNode(
            origin,
            dir,
            PICK_RAY_MAX,
            new ArrayList<>(st.getNodes()),
            NODE_PICK_RADIUS
        );
        if (looked != null) {
            @Nonnull
            List<PathToolNode> next = new ArrayList<>();
            for (PathToolNode n : st.getNodes()) {
                if (!n.getId().equals(looked.getId())) {
                    next.add(n);
                }
            }
            st.setNodesFromList(next);
            if (looked.getId().equals(st.getSelectedNodeId())) {
                st.setSelectedNodeId(next.isEmpty() ? null : next.get(Math.max(0, next.size() - 1)).getId());
            }
            send(playerRef, commandBuffer, Message.translation("server.aetherhaven.pathTool.removedNode").param("n", String.valueOf(next.size())));
            pathToast(playerRef, commandBuffer, "server.aetherhaven.pathTool.toastRemoved");
            return;
        }
        double yo = plugin.getConfig().get().getPathToolNodeBlockYOffset();
        Vector3d pos = blockTopCenter(targetBlock, yo);
        double yaw = 0.0;
        yaw = PathSplineUtil.yawDegFromLookDirection(dir);
        st.getNodes()
            .add(
                new PathToolNode(
                    UUID.randomUUID(),
                    pos,
                    yaw
                )
            );
        send(
            playerRef,
            commandBuffer,
            Message.translation("server.aetherhaven.pathTool.addedNode").param("n", String.valueOf(st.getNodes().size()))
        );
        pathToast(playerRef, commandBuffer, "server.aetherhaven.pathTool.toastAdd");
    }

    public static void handleSelect(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock,
        @Nonnull InteractionContext context,
        @Nonnull Store<EntityStore> store
    ) {
        if (!hasPathToolPermission(playerRef, commandBuffer) || !isPathToolItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ensureState(playerRef, commandBuffer);
        PathToolPlayerComponent st = commandBuffer.getComponent(playerRef, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        double yo = plugin.getConfig().get().getPathToolNodeBlockYOffset();
        Transform look = TargetUtil.getLook(playerRef, store);
        Vector3d origin = look.getPosition();
        Vector3d dir = look.getDirection();
        @Nullable
        PathToolNode looked = PathToolRayPick.pickNode(
            origin,
            dir,
            PICK_RAY_MAX,
            new ArrayList<>(st.getNodes()),
            NODE_PICK_RADIUS
        );
        if (looked != null) {
            if (looked.getId().equals(st.getSelectedNodeId())) {
                // Already on this keyframe: use Q to cycle move/rotate/commit (not primary again).
                return;
            }
            st.setSelectedNodeId(looked.getId());
            send(
                playerRef,
                commandBuffer,
                Message.translation("server.aetherhaven.pathTool.selectedNode")
            );
            pathToast(playerRef, commandBuffer, modeToastId(st.getGizmoMode()));
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.Translate
            && st.getSelectedNodeId() != null
            && st.findNode(st.getSelectedNodeId()) != null) {
            Vector3d npos = blockTopCenter(targetBlock, yo);
            @Nonnull
            List<PathToolNode> list = st.getNodes();
            @Nonnull
            List<PathToolNode> next = new ArrayList<>();
            boolean moved = false;
            for (PathToolNode n : list) {
                if (n.getId().equals(st.getSelectedNodeId())) {
                    next.add(n.withPosition(npos));
                    moved = true;
                } else {
                    next.add(n);
                }
            }
            if (moved) {
                st.setNodesFromList(next);
                send(playerRef, commandBuffer, Message.translation("server.aetherhaven.pathTool.movedNode"));
                pathToast(playerRef, commandBuffer, "server.aetherhaven.pathTool.toastMoved");
            } else {
                context.getState().state = InteractionState.Failed;
            }
            return;
        }
        // No node on crosshair and we did not move: keep the current selection (do not deselect on miss).
        if (!st.getNodes().isEmpty()) {
            send(
                playerRef,
                commandBuffer,
                Message.translation("server.aetherhaven.pathTool.noNodeOnAim")
            );
        }
    }

    @Nonnull
    private static String modeToastId(@Nonnull PathToolGizmoMode m) {
        return switch (m) {
            case Translate -> "server.aetherhaven.pathTool.toastModeTranslate";
            case Rotate -> "server.aetherhaven.pathTool.toastModeRotate";
            case Commit -> "server.aetherhaven.pathTool.toastModeCommit";
        };
    }

    public static void handleCycleGizmoMode(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!hasPathToolPermission(playerRef, commandBuffer) || !isPathToolItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ensureState(playerRef, commandBuffer);
        PathToolPlayerComponent st = commandBuffer.getComponent(playerRef, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        st.cycleGizmoMode();
        send(
            playerRef,
            commandBuffer,
            Message.translation(
                switch (st.getGizmoMode()) {
                    case Translate -> "server.aetherhaven.pathTool.modeCycledToTranslate";
                    case Rotate -> "server.aetherhaven.pathTool.modeCycledToRotate";
                    case Commit -> "server.aetherhaven.pathTool.modeCycledToCommit";
                }
            )
        );
        pathToast(playerRef, commandBuffer, modeToastId(st.getGizmoMode()));
    }

    public static void handleCyclePathWidth(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!hasPathToolPermission(playerRef, commandBuffer) || !isPathToolItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ensureState(playerRef, commandBuffer);
        PathToolPlayerComponent st = commandBuffer.getComponent(playerRef, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        st.cyclePathWidth();
        send(
            playerRef,
            commandBuffer,
            Message
                .translation("server.aetherhaven.pathTool.widthCycled")
                .param("w", String.valueOf(st.getPathWidthBlocks()))
        );
    }

    public static void handleCyclePathStyle(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!hasPathToolPermission(playerRef, commandBuffer) || !isPathToolItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ensureState(playerRef, commandBuffer);
        PathToolPlayerComponent st = commandBuffer.getComponent(playerRef, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        int n = plugin.getConfig().get().getPathToolStyleDefinitions().size();
        if (n <= 0) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        st.clampPathStyleIndex(n);
        st.cyclePathStyle(n);
        send(
            playerRef,
            commandBuffer,
            Message
                .translation("server.aetherhaven.pathTool.styleCycled")
                .param("style", plugin.getConfig().get().getPathToolStyleName(st.getPathStyleIndex()))
        );
        pathToast(playerRef, commandBuffer, "server.aetherhaven.pathTool.toastStyleCycled");
    }

    public static void handleUse(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull InteractionContext context
    ) {
        if (!hasPathToolPermission(playerRef, commandBuffer) || !isPathToolItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ensureState(playerRef, commandBuffer);
        PathToolPlayerComponent st = commandBuffer.getComponent(playerRef, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        st.clampPathStyleIndex(plugin.getConfig().get().getPathToolStyleDefinitions().size());
        if (st.getGizmoMode() == PathToolGizmoMode.Translate) {
            send(playerRef, commandBuffer, Message.translation("server.aetherhaven.pathTool.useInTranslateMode"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.Rotate
            && st.getSelectedNodeId() != null
            && st.findNode(st.getSelectedNodeId()) != null) {
            @Nonnull
            List<PathToolNode> next = new ArrayList<>();
            for (PathToolNode n : st.getNodes()) {
                if (n.getId().equals(st.getSelectedNodeId())) {
                    next.add(
                        n.withYaw(n.getYawDeg() + ROTATE_STEP_DEG)
                    );
                } else {
                    next.add(n);
                }
            }
            st.setNodesFromList(next);
            send(
                playerRef,
                commandBuffer,
                Message.translation("server.aetherhaven.pathTool.rotated").param("deg", String.valueOf(ROTATE_STEP_DEG))
            );
            pathToast(playerRef, commandBuffer, "server.aetherhaven.pathTool.toastRotated");
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.Rotate) {
            send(playerRef, commandBuffer, Message.translation("server.aetherhaven.pathTool.rotateNoSelection"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (st.getNodes().size() < 2) {
            send(
                playerRef,
                commandBuffer,
                Message.translation("server.aetherhaven.pathTool.needTwoNodes")
            );
            context.getState().state = InteractionState.Failed;
            return;
        }
        var samples =
            PathSplineUtil.sample(
                st.getNodes(),
                plugin.getConfig().get().getPathToolSamplesPerBlock()
            );
        List<PathPlannedCell.Planned> plan =
            PathPlannedCell.build(
                world,
                samples,
                st.getPathWidthBlocks(),
                plugin.getConfig().get().getPathToolRayStartAboveY(),
                plugin.getConfig().get().getPathToolMaxRayDown()
            );
        if (plan.isEmpty()) {
            send(playerRef, commandBuffer, Message.translation("server.aetherhaven.pathTool.emptyPlan"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        PathCommitRecord rec = PathCementService.tryCement(
            world,
            plugin.getConfig().get(),
            plan,
            st.getPathStyleIndex()
        );
        if (rec == null) {
            send(playerRef, commandBuffer, Message.translation("server.aetherhaven.pathTool.cementFail"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        rec.navNodes = PathNavPolylineUtil.resampleCenterline(samples, plugin.getConfig().get().getPathNavNodeSpacing());
        rec.townId = resolveTownIdForPath(world, plugin, st, samples);
        PathToolRegistry reg = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
        reg.addRecord(rec);
        AetherhavenWorldRegistries.getOrCreatePathNavGraphService(world).rebuildAll(reg, plugin.getConfig().get());
        PathToolPersistence.save(world, plugin, reg);
        st.clearPath();
        send(
            playerRef,
            commandBuffer,
            Message.translation("server.aetherhaven.pathTool.cemented").param("id", rec.id)
        );
        pathToast(playerRef, commandBuffer, "server.aetherhaven.pathTool.toastCemented");
    }

    @Nullable
    private static String resolveTownIdForPath(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PathToolPlayerComponent st,
        @Nonnull List<PathSplineUtil.PathSample> samples
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        if (!samples.isEmpty()) {
            int n = samples.size();
            int[] probeIdx = { 0, n / 2, n - 1 };
            for (int pi : probeIdx) {
                if (pi < 0 || pi >= n) {
                    continue;
                }
                Vector3d pick = samples.get(pi).position;
                TownRecord town = tm.findTownContainingBlock(
                    world.getName(),
                    (int) Math.floor(pick.getX()),
                    (int) Math.floor(pick.getZ())
                );
                if (town != null) {
                    return town.getTownId().toString();
                }
            }
        } else if (!st.getNodes().isEmpty()) {
            Vector3d pick = st.getNodes().get(0).getPosition();
            TownRecord town = tm.findTownContainingBlock(
                world.getName(),
                (int) Math.floor(pick.getX()),
                (int) Math.floor(pick.getZ())
            );
            if (town != null) {
                return town.getTownId().toString();
            }
        }
        return null;
    }

    @Nullable
    private static ItemStack getHand(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        return com.hypixel.hytale.server.core.inventory.InventoryComponent.getItemInHand(commandBuffer, playerRef);
    }

    private static void send(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Message message
    ) {
        @Nullable
        PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(message);
        }
    }

    private static void pathToast(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull String messageId
    ) {
        @Nullable
        PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation(messageId),
            NotificationStyle.Success
        );
    }
}
