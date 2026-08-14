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
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PathToolInteractions {
    private static final double NODE_PICK_RADIUS = 0.45;
    private static final double PICK_RAY_MAX = 128.0;
    /** Matches path tool item UseDistance (creative). */
    private static final double BLOCK_PICK_MAX = 6.0;
    private static final float ROTATE_STEP_DEG = 15f;

    /** Shovel clicks can invoke both interaction chains at once; ignore duplicate filter toggles. */
    private static final ConcurrentHashMap<UUID, Long> LAST_FILTER_CLICK_NS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> LAST_FILTER_CLICK_BLOCK = new ConcurrentHashMap<>();
    private static final long FILTER_CLICK_DEDUPE_NS = 200_000_000L;

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
        PlayerRef pr = accessor.getComponent(playerRef, PlayerRef.getComponentType());
        return pr != null && pr.hasPermission(AetherhavenConstants.PERMISSION_PATH_TOOL);
    }

    public static void ensureState(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PathToolPlayerComponent.ensurePresent(playerRef, commandBuffer);
    }

    @Nonnull
    public static Vector3d blockTopCenter(@Nonnull Vector3i b, double yOffsetBlocks) {
        return new Vector3d(
            b.x() + 0.5,
            b.y() + 0.5 + yOffsetBlocks,
            b.z() + 0.5
        );
    }

    public static void handleAddNode(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
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
        if (st.getGizmoMode() == PathToolGizmoMode.Remove) {
            handleRemoveModeSelect(playerRef, commandBuffer, world, store, st);
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.StyleDesigner) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.ReplaceFilter) {
            handleReplaceFilterToggleBlock(playerRef, commandBuffer, world, context, store, st);
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
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.removedNode").param("n", String.valueOf(next.size())));
            pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastRemoved");
            return;
        }
        @Nullable
        Vector3i targetBlock = PathToolBlockTarget.resolve(playerRef, store, context, null);
        if (targetBlock == null) {
            context.getState().state = InteractionState.Failed;
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
            Message.translation("aetherhaven_items.aetherhaven.pathTool.addedNode").param("n", String.valueOf(st.getNodes().size()))
        );
        pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastAdd");
    }

    public static void handleSelect(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
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
        if (st.getGizmoMode() == PathToolGizmoMode.Remove) {
            handleRemoveModeSelect(playerRef, commandBuffer, world, store, st);
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.ReplaceFilter) {
            handleReplaceFilterToggleBlock(playerRef, commandBuffer, world, context, store, st);
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.StyleDesigner) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        double yo = plugin.getConfig().get().getPathToolNodeBlockYOffset();
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
                Message.translation("aetherhaven_items.aetherhaven.pathTool.selectedNode")
            );
            pathToast(playerRef, commandBuffer, modeToastId(st.getGizmoMode()));
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.Translate
            && st.getSelectedNodeId() != null
            && st.findNode(st.getSelectedNodeId()) != null) {
            @Nullable
            Vector3i targetBlock = pickTargetBlock(playerRef, store);
            if (targetBlock == null) {
                send(
                    playerRef,
                    commandBuffer,
                    Message.translation("aetherhaven_items.aetherhaven.pathTool.noGroundOnAim")
                );
                context.getState().state = InteractionState.Failed;
                return;
            }
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
                send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.movedNode"));
                pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastMoved");
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
                Message.translation("aetherhaven_items.aetherhaven.pathTool.noNodeOnAim")
            );
        }
    }

    @Nonnull
    private static String modeToastId(@Nonnull PathToolGizmoMode m) {
        return switch (m) {
            case Translate -> "aetherhaven_items.aetherhaven.pathTool.toastModeTranslate";
            case Rotate -> "aetherhaven_items.aetherhaven.pathTool.toastModeRotate";
            case Commit -> "aetherhaven_items.aetherhaven.pathTool.toastModeCommit";
            case Remove -> "aetherhaven_items.aetherhaven.pathTool.toastModeRemove";
            case StyleDesigner -> "aetherhaven_items.aetherhaven.pathTool.toastModeStyleDesigner";
            case ReplaceFilter -> "aetherhaven_items.aetherhaven.pathTool.toastModeReplaceFilter";
        };
    }

    @Nonnull
    private static String modeCycleMessageId(@Nonnull PathToolGizmoMode m) {
        return switch (m) {
            case Translate -> "aetherhaven_items.aetherhaven.pathTool.modeCycledToTranslate";
            case Rotate -> "aetherhaven_items.aetherhaven.pathTool.modeCycledToRotate";
            case Commit -> "aetherhaven_items.aetherhaven.pathTool.modeCycledToCommit";
            case Remove -> "aetherhaven_items.aetherhaven.pathTool.modeCycledToRemove";
            case StyleDesigner -> "aetherhaven_items.aetherhaven.pathTool.modeCycledToStyleDesigner";
            case ReplaceFilter -> "aetherhaven_items.aetherhaven.pathTool.modeCycledToReplaceFilter";
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
        Store<EntityStore> store = commandBuffer.getStore();
        if (st.getGizmoMode() == PathToolGizmoMode.ReplaceFilter) {
            PathToolReplaceFilterUi.syncPendingFromSession(playerRef, store, st);
        }
        st.cycleGizmoMode();
        send(
            playerRef,
            commandBuffer,
            Message.translation(modeCycleMessageId(st.getGizmoMode()))
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
        Store<EntityStore> store = commandBuffer.getStore();
        if (st.getGizmoMode() == PathToolGizmoMode.StyleDesigner) {
            @Nullable
            PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null && PathToolStyleUi.isActivelyEditing(playerRef, store)) {
                if (PathToolStyleUi.tryFinishEditing(playerRef, store, pr)) {
                    pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastStyleSaved");
                } else {
                    context.getState().state = InteractionState.Failed;
                }
                return;
            }
        }
        if (st.getGizmoMode() == PathToolGizmoMode.ReplaceFilter) {
            @Nullable
            PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null && PathToolReplaceFilterUi.isActivelyEditing(playerRef, store)) {
                if (PathToolReplaceFilterUi.tryFinishEditing(playerRef, store, pr, st)) {
                    pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastReplaceFilterSaved");
                } else {
                    context.getState().state = InteractionState.Failed;
                }
                return;
            }
        }
        if (isPathLayoutEditMode(st.getGizmoMode())) {
            @Nullable
            PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                PathToolWidthPage.open(playerRef, store, pr);
            }
            return;
        }
        st.cyclePathWidth();
        send(
            playerRef,
            commandBuffer,
            Message
                .translation("aetherhaven_items.aetherhaven.pathTool.widthCycled")
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
        Store<EntityStore> store = commandBuffer.getStore();
        int n = plugin.getConfig().get().getPathToolStyleDefinitions().size();
        if (n <= 0) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        st.clampPathStyleIndex(n);
        if (isPathLayoutEditMode(st.getGizmoMode())) {
            @Nullable
            PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                PathToolStylePickPage.open(playerRef, store, pr);
            }
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.StyleDesigner && !PathToolStyleUi.isActivelyEditing(playerRef, store)) {
            @Nullable
            PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                PathToolStylePickPage.open(playerRef, store, pr);
            }
            return;
        }
        st.cyclePathStyle(n);
        send(
            playerRef,
            commandBuffer,
            Message
                .translation("aetherhaven_items.aetherhaven.pathTool.styleCycled")
                .param("style", plugin.getConfig().get().getPathToolStyleName(st.getPathStyleIndex()))
        );
        pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastStyleCycled");
    }

    public static void handleToggleTownsfolkWalk(
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
        if (!isPathLayoutEditMode(st.getGizmoMode())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        st.toggleVillagerNav();
        String messageId =
            st.isVillagerNav()
                ? "aetherhaven_items.aetherhaven.pathTool.hudTownsfolkOn"
                : "aetherhaven_items.aetherhaven.pathTool.hudTownsfolkOff";
        send(playerRef, commandBuffer, Message.translation(messageId));
        pathToast(playerRef, commandBuffer, messageId);
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
        Store<EntityStore> store = commandBuffer.getStore();
        ensureState(playerRef, commandBuffer);
        PathToolPlayerComponent st = commandBuffer.getComponent(playerRef, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        st.clampPathStyleIndex(plugin.getConfig().get().getPathToolStyleDefinitions().size());
        if (st.getGizmoMode() == PathToolGizmoMode.StyleDesigner) {
            @Nullable
            PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                PathToolStyleUi.handleUse(playerRef, store, pr);
            }
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.ReplaceFilter) {
            @Nullable
            PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                PathToolReplaceFilterUi.handleUse(playerRef, store, pr, st);
            }
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.Remove) {
            handleRemovePath(playerRef, commandBuffer, world, plugin, st, context);
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.Translate) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.useInTranslateMode"));
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
                Message.translation("aetherhaven_items.aetherhaven.pathTool.rotated").param("deg", String.valueOf(ROTATE_STEP_DEG))
            );
            pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastRotated");
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.Rotate) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.rotateNoSelection"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (st.getNodes().size() < 2) {
            send(
                playerRef,
                commandBuffer,
                Message.translation("aetherhaven_items.aetherhaven.pathTool.needTwoNodes")
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
        @Nullable
        java.util.Set<String> playerReplace =
            PathToolReplaceFilterResolver.nullableAllowlistForPredicate(playerRef, store, st);
        boolean navOnly;
        @Nonnull
        PathCommitRecord rec;
        if (plan.isEmpty()) {
            if (!st.isVillagerNav()) {
                send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.emptyPlan"));
                context.getState().state = InteractionState.Failed;
                return;
            }
            rec = PathCementService.newShellRecord();
            navOnly = true;
        } else {
            @Nullable
            PathCommitRecord cemented =
                PathCementService.tryCement(
                    world,
                    plugin.getConfig().get(),
                    plan,
                    st.getPathStyleIndex(),
                    st.getPathWidthBlocks(),
                    playerReplace,
                    ThreadLocalRandom.current()
                );
            if (cemented == null) {
                send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.cementFail"));
                context.getState().state = InteractionState.Failed;
                return;
            }
            rec = cemented;
            navOnly = rec.undo.isEmpty();
            if (navOnly && !st.isVillagerNav()) {
                send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.cementFail"));
                context.getState().state = InteractionState.Failed;
                return;
            }
        }
        rec.navNodes = PathNavPolylineUtil.resampleCenterline(samples, plugin.getConfig().get().getPathNavNodeSpacing());
        rec.villagerNav = st.isVillagerNav();
        rec.townId = resolveTownIdForPath(world, plugin, st, samples);
        PathToolRegistry reg = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
        reg.addRecord(rec);
        AetherhavenWorldRegistries.getOrCreatePathNavGraphService(world).rebuildAll(reg, plugin.getConfig().get());
        PathToolPersistence.save(world, plugin, reg);
        st.clearPath();
        if (navOnly) {
            send(
                playerRef,
                commandBuffer,
                Message.translation("aetherhaven_items.aetherhaven.pathTool.committedNavOnly")
            );
            pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastCommittedNavOnly");
        } else {
            send(
                playerRef,
                commandBuffer,
                Message.translation("aetherhaven_items.aetherhaven.pathTool.cemented")
            );
            pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastCemented");
        }
    }

    private static void handleRemoveModeSelect(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull PathToolPlayerComponent st
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        Transform look = TargetUtil.getLook(playerRef, store);
        if (look == null) {
            return;
        }
        PathToolRegistry reg = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
        @Nullable
        UUID picked =
            PathToolRemovePick.pickPathId(look.getPosition(), look.getDirection(), PICK_RAY_MAX, reg.all());
        if (picked != null) {
            st.setSelectedRemovePathId(picked);
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.removeSelected"));
        } else if (!reg.all().isEmpty()) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.removeNoHit"));
        }
    }

    private static void handleRemovePath(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PathToolPlayerComponent st,
        @Nonnull InteractionContext context
    ) {
        UUID targetId = st.getSelectedRemovePathId();
        if (targetId == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.removeNeedSelection"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        PathToolRegistry reg = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
        @Nullable
        PathCommitRecord rec = reg.remove(targetId);
        if (rec == null) {
            st.setSelectedRemovePathId(null);
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.pathTool.removeUnknown"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        int cells = PathToolRestoreService.restoreAndRemove(world, rec);
        AetherhavenWorldRegistries.getOrCreatePathNavGraphService(world).rebuildAll(reg, plugin.getConfig().get());
        PathToolPersistence.save(world, plugin, reg);
        st.setSelectedRemovePathId(null);
        send(
            playerRef,
            commandBuffer,
            Message
                .translation("aetherhaven_items.aetherhaven.pathTool.removedPath")
                .param("cells", String.valueOf(cells))
        );
        pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastRemovedPath");
    }

    private static void wrongModeToast(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.wrongMode");
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
                    (int) Math.floor(pick.x()),
                    (int) Math.floor(pick.z())
                );
                if (town != null) {
                    return town.getTownId().toString();
                }
            }
        } else if (!st.getNodes().isEmpty()) {
            Vector3d pick = st.getNodes().get(0).getPosition();
            TownRecord town = tm.findTownContainingBlock(
                world.getName(),
                (int) Math.floor(pick.x()),
                (int) Math.floor(pick.z())
            );
            if (town != null) {
                return town.getTownId().toString();
            }
        }
        return null;
    }

    @Nullable
    private static Vector3i pickTargetBlock(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        return TargetUtil.getTargetBlock(playerRef, BLOCK_PICK_MAX, store);
    }

    /**
     * Replace-filter mode: look at a block and click to add it to the allowlist, or click again to remove it.
     * Uses toggle semantics so shovel tools that only route block clicks through one interaction chain still work.
     */
    private static void handleReplaceFilterToggleBlock(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull InteractionContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull PathToolPlayerComponent st
    ) {
        @Nullable
        Vector3i target = PathToolBlockTarget.resolve(playerRef, store, context, null);
        if (target == null) {
            send(
                playerRef,
                commandBuffer,
                Message.translation("aetherhaven_items.aetherhaven.pathTool.replaceFilterBlockNoTarget")
            );
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (shouldDedupeReplaceFilterClick(playerRef, store, target)) {
            return;
        }
        markReplaceFilterClick(playerRef, store, target);
        @Nullable
        String blockId = PathToolReplaceFilterEditorHelper.resolveBlockIdAt(world, target);
        if (blockId == null) {
            send(
                playerRef,
                commandBuffer,
                Message.translation("aetherhaven_items.aetherhaven.pathTool.replaceFilterBlockNoTarget")
            );
            context.getState().state = InteractionState.Failed;
            return;
        }
        LinkedHashSet<String> next =
            new LinkedHashSet<>(PathToolReplaceFilterResolver.effectiveBlockIds(playerRef, store, st));
        if (PathToolReplaceFilterEditorHelper.containsBlockId(next, blockId)) {
            if (!PathToolReplaceFilterEditorHelper.removeMatchingBlockId(next, blockId)) {
                send(
                    playerRef,
                    commandBuffer,
                    Message.translation("aetherhaven_items.aetherhaven.pathTool.replaceFilterBlockNotInFilter")
                );
                context.getState().state = InteractionState.Failed;
                return;
            }
            PathToolReplaceFilterUi.saveAllowlistAndSyncSession(playerRef, store, st, next);
            send(
                playerRef,
                commandBuffer,
                Message.translation("aetherhaven_items.aetherhaven.pathTool.replaceFilterBlockRemoved")
            );
            pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastReplaceFilterBlockRemoved");
            return;
        }
        if (next.size() >= PathToolReplaceFilterEditorHelper.CAPACITY) {
            send(
                playerRef,
                commandBuffer,
                Message.translation("aetherhaven_items.aetherhaven.pathTool.replaceFilterBlockFull")
            );
            context.getState().state = InteractionState.Failed;
            return;
        }
        next.add(blockId);
        PathToolReplaceFilterUi.saveAllowlistAndSyncSession(playerRef, store, st, next);
        send(
            playerRef,
            commandBuffer,
            Message.translation("aetherhaven_items.aetherhaven.pathTool.replaceFilterBlockAdded")
        );
        pathToast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.pathTool.toastReplaceFilterBlockAdded");
    }

    private static boolean shouldDedupeReplaceFilterClick(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i target
    ) {
        @Nullable
        UUID playerId = playerUuid(playerRef, store);
        if (playerId == null) {
            return false;
        }
        String blockKey = target.x + "," + target.y + "," + target.z;
        @Nullable
        Long clickedAt = LAST_FILTER_CLICK_NS.get(playerId);
        @Nullable
        String lastBlock = LAST_FILTER_CLICK_BLOCK.get(playerId);
        return clickedAt != null
            && blockKey.equals(lastBlock)
            && System.nanoTime() - clickedAt < FILTER_CLICK_DEDUPE_NS;
    }

    private static void markReplaceFilterClick(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i target
    ) {
        @Nullable
        UUID playerId = playerUuid(playerRef, store);
        if (playerId == null) {
            return;
        }
        LAST_FILTER_CLICK_NS.put(playerId, System.nanoTime());
        LAST_FILTER_CLICK_BLOCK.put(playerId, target.x + "," + target.y + "," + target.z);
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
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

    private static boolean isPathLayoutEditMode(@Nonnull PathToolGizmoMode mode) {
        return mode == PathToolGizmoMode.Commit
            || mode == PathToolGizmoMode.Translate
            || mode == PathToolGizmoMode.Rotate;
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
