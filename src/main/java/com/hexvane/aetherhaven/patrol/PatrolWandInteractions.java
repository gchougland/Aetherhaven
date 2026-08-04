package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.pathtool.PathToolInteractions;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.protocol.SoundCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class PatrolWandInteractions {
    private static final double NODE_PICK_RADIUS = 0.55;
    private static final double PICK_RAY_MAX = 128.0;
    private static final String SAVE_SOUND = "SFX_Creative_Play_Add_Mask";

    private PatrolWandInteractions() {}

    public static boolean isPatrolWandItem(@Nullable ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && AetherhavenConstants.PATROL_WAND_ITEM_ID.equals(stack.getItemId());
    }

    public static void ensureState(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        resolveState(playerRef, commandBuffer);
    }

    /** Ensures a patrol wand session exists on the player and returns the live component, if present. */
    @Nullable
    public static PatrolWandPlayerComponent resolveState(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PatrolWandPlayerComponent st = commandBuffer.getComponent(playerRef, PatrolWandPlayerComponent.getComponentType());
        if (st == null) {
            commandBuffer.addComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), new PatrolWandPlayerComponent());
            st = commandBuffer.getComponent(playerRef, PatrolWandPlayerComponent.getComponentType());
        }
        if (st == null) {
            st = commandBuffer.getStore().getComponent(playerRef, PatrolWandPlayerComponent.getComponentType());
        }
        return st;
    }

    public static void handleSecondary(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock,
        @Nonnull InteractionContext context,
        @Nonnull Store<EntityStore> store
    ) {
        if (!isPatrolWandItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!requireMember(playerRef, commandBuffer, world, targetBlock)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PatrolWandPlayerComponent st = resolveState(playerRef, commandBuffer);
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (st.getMode() == PatrolWandMode.Remove) {
            wrongModeToast(playerRef, commandBuffer);
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (st.getMode() != PatrolWandMode.Build) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.needBuildMode"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        Transform look = TargetUtil.getLook(playerRef, store);
        Vector3d origin = look.getPosition();
        Vector3d dir = look.getDirection();
        @Nullable
        PatrolWandNode looked = PatrolWandRayPick.pickDraftNode(
            origin,
            dir,
            PICK_RAY_MAX,
            new ArrayList<>(st.getDraftNodes()),
            NODE_PICK_RADIUS
        );
        if (looked != null) {
            List<PatrolWandNode> next = new ArrayList<>();
            for (PatrolWandNode n : st.getDraftNodes()) {
                if (!n.getId().equals(looked.getId())) {
                    next.add(n);
                }
            }
            st.setDraftNodesFromList(next);
            send(
                playerRef,
                commandBuffer,
                Message
                    .translation("aetherhaven_items.aetherhaven.patrolWand.removedNode")
                    .param("n", String.valueOf(next.size()))
            );
            toast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.patrolWand.toastRemoved");
            commandBuffer.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);
            return;
        }
        Vector3d pos = PathToolInteractions.blockTopCenter(targetBlock, cfg.getPathToolNodeBlockYOffset());
        st.getDraftNodes().add(new PatrolWandNode(UUID.randomUUID(), pos));
        commandBuffer.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);
        send(
            playerRef,
            commandBuffer,
            Message
                .translation("aetherhaven_items.aetherhaven.patrolWand.addedNode")
                .param("n", String.valueOf(st.getDraftNodes().size()))
        );
        toast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.patrolWand.toastAdded");
    }

    public static void handlePrimary(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull InteractionContext context,
        @Nonnull Store<EntityStore> store
    ) {
        if (!isPatrolWandItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!requireMemberAtPlayer(playerRef, commandBuffer, world, store)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PatrolWandPlayerComponent st = resolveState(playerRef, commandBuffer);
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        TownRecord town = resolveTownAtPlayer(world, plugin, store, playerRef);
        if (town == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.notInTown"));
            return;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        Transform look = TargetUtil.getLook(playerRef, store);
        @Nullable
        PatrolRouteRecord route = PatrolWandRayPick.pickSavedRoute(
            look.getPosition(),
            look.getDirection(),
            PICK_RAY_MAX,
            reg.listByTown(town.getTownId()),
            NODE_PICK_RADIUS
        );
        if (route == null) {
            @Nullable
            PatrolWandNode draftHit = PatrolWandRayPick.pickDraftNode(
                look.getPosition(),
                look.getDirection(),
                PICK_RAY_MAX,
                st.getDraftNodes(),
                NODE_PICK_RADIUS
            );
            if (draftHit == null) {
                send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.noRouteHit"));
            }
            return;
        }
        if (st.getMode() == PatrolWandMode.Assign || st.getMode() == PatrolWandMode.Remove) {
            UUID rid = route.getIdUuid();
            if (rid == null) {
                return;
            }
            st.selectRouteForAssign(rid);
        } else {
            st.loadFromRecord(route);
        }
        commandBuffer.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);
        String messageKey =
            switch (st.getMode()) {
                case Assign -> "aetherhaven_items.aetherhaven.patrolWand.selectedRoute";
                case Remove -> "aetherhaven_items.aetherhaven.patrolWand.removeSelected";
                default -> "aetherhaven_items.aetherhaven.patrolWand.selectedRoute";
            };
        send(
            playerRef,
            commandBuffer,
            Message
                .translation(messageKey)
                .param("name", route.safeDisplayName())
        );
        toast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.patrolWand.toastSelected");
    }

    public static void handleUse(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull InteractionContext context,
        @Nonnull Store<EntityStore> store
    ) {
        if (!isPatrolWandItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!requireMemberAtPlayer(playerRef, commandBuffer, world, store)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PatrolWandPlayerComponent st = resolveState(playerRef, commandBuffer);
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (st.getMode() == PatrolWandMode.Build) {
            openNameRoutePage(playerRef, commandBuffer, world, st, store);
        } else if (st.getMode() == PatrolWandMode.Assign) {
            openAssignGuardPage(playerRef, commandBuffer, world, st, store);
        } else {
            handleRemoveRoute(playerRef, commandBuffer, world, st, context, store);
        }
    }

    public static void handleModeCycle(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!isPatrolWandItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PatrolWandPlayerComponent st = resolveState(playerRef, commandBuffer);
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        st.cycleMode();
        commandBuffer.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);
        String key =
            switch (st.getMode()) {
                case Build -> "aetherhaven_items.aetherhaven.patrolWand.modeBuild";
                case Assign -> "aetherhaven_items.aetherhaven.patrolWand.modeAssign";
                case Remove -> "aetherhaven_items.aetherhaven.patrolWand.modeRemove";
            };
        send(playerRef, commandBuffer, Message.translation(key));
        String toastKey =
            st.getMode() == PatrolWandMode.Remove
                ? "aetherhaven_items.aetherhaven.patrolWand.toastModeRemove"
                : key;
        toast(playerRef, commandBuffer, toastKey);
    }

    public static void handleNewRoute(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull InteractionContext context
    ) {
        if (!isPatrolWandItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!requireMemberAtPlayer(playerRef, commandBuffer, world, commandBuffer.getStore())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PatrolWandPlayerComponent st = resolveState(playerRef, commandBuffer);
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (st.getMode() == PatrolWandMode.Assign) {
            clearGuardFromSelectedRoute(playerRef, commandBuffer, world, st);
            return;
        }
        if (st.getMode() == PatrolWandMode.Remove) {
            wrongModeToast(playerRef, commandBuffer);
            context.getState().state = InteractionState.Failed;
            return;
        }
        st.startNewRoute();
        commandBuffer.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);
        send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.newRoute"));
        toast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.patrolWand.toastNewRoute");
    }

    public static void handleToggleClosedLoop(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull InteractionContext context,
        @Nonnull Store<EntityStore> store
    ) {
        if (!isPatrolWandItem(getHand(commandBuffer, playerRef))) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!requireMemberAtPlayer(playerRef, commandBuffer, world, store)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PatrolWandPlayerComponent st = resolveState(playerRef, commandBuffer);
        if (st == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (st.getMode() != PatrolWandMode.Build) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.needBuildMode"));
            return;
        }
        if (st.getDraftNodes().size() < 2) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.needTwoNodesForLoop"));
            return;
        }
        st.toggleDraftClosedLoop();
        commandBuffer.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);
        String key =
            st.isDraftClosedLoop()
                ? "aetherhaven_items.aetherhaven.patrolWand.loopClosed"
                : "aetherhaven_items.aetherhaven.patrolWand.loopOpen";
        send(playerRef, commandBuffer, Message.translation(key));
        toast(playerRef, commandBuffer, key);
    }

    private static void openNameRoutePage(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull PatrolWandPlayerComponent st,
        @Nonnull Store<EntityStore> store
    ) {
        if (st.getDraftNodes().size() < 2) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.needTwoNodes"));
            return;
        }
        if (!requireMemberAtPlayer(playerRef, commandBuffer, world, store)) {
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        if (player.getPageManager().getCustomPage() != null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        if (resolveTownAtPlayer(world, plugin, store, playerRef) == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.notInTown"));
            return;
        }
        player.getPageManager().openCustomPage(playerRef, store, new PatrolWandNameRoutePage(pr));
    }

    /**
     * Saves the current draft from {@link PatrolWandPlayerComponent} with the given display name, selects the route
     * for assignment, and switches to assign mode.
     *
     * @return saved route id, or null if validation failed
     */
    @Nullable
    public static UUID commitSaveRoute(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String displayName
    ) {
        PatrolWandPlayerComponent st = store.getComponent(playerRef, PatrolWandPlayerComponent.getComponentType());
        if (st == null || st.getDraftNodes().size() < 2) {
            return null;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            return null;
        }
        TownRecord town = resolveTownAtPlayer(world, plugin, store, playerRef);
        if (town == null) {
            PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(Message.translation("aetherhaven_items.aetherhaven.patrolWand.notInTown"));
            }
            return null;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        PatrolRouteRecord rec;
        UUID editId = st.getEditingRouteId();
        if (editId != null) {
            rec = reg.get(editId);
            if (rec == null) {
                rec = new PatrolRouteRecord();
                rec.id = editId.toString();
            }
        } else {
            rec = new PatrolRouteRecord();
            rec.id = UUID.randomUUID().toString();
        }
        rec.townId = town.getTownId().toString();
        rec.displayName = displayName;
        rec.nodes = new ArrayList<>();
        for (PatrolWandNode n : st.getDraftNodes()) {
            Vector3d p = n.getPosition();
            rec.nodes.add(new PatrolRouteNode(p.x(), p.y(), p.z()));
        }
        rec.closedLoop = st.isDraftClosedLoop();
        reg.upsert(rec);
        PatrolRoutePersistence.save(world, plugin, reg);
        UUID routeId = rec.getIdUuid();
        if (routeId != null) {
            st.selectRouteForAssign(routeId);
            st.setMode(PatrolWandMode.Assign);
        }
        store.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);

        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            int idx = SoundEvent.getAssetMap().getIndex(SAVE_SOUND);
            if (idx > 0) {
                SoundUtil.playSoundEvent2dToPlayer(pr, idx, SoundCategory.SFX);
            }
            pr.sendMessage(
                Message
                    .translation("aetherhaven_items.aetherhaven.patrolWand.savedRoute")
                    .param("name", rec.safeDisplayName())
                    .param("n", String.valueOf(rec.nodes.size()))
            );
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_items.aetherhaven.patrolWand.toastSaved"),
                NotificationStyle.Success
            );
        }
        return routeId;
    }

    /** Opens the guard picker for the route currently selected on the player (assign mode). */
    public static void openAssignGuardPageFromStore(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PatrolWandPlayerComponent st = store.getComponent(playerRef, PatrolWandPlayerComponent.getComponentType());
        if (st == null) {
            return;
        }
        UUID routeId = st.getSelectedRouteId();
        if (routeId == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            return;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        PatrolRouteRecord route = reg.get(routeId);
        if (route == null) {
            return;
        }
        UUID routeTown = route.getTownIdUuid();
        if (routeTown == null) {
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        player.getPageManager().openCustomPage(playerRef, store, new PatrolWandAssignGuardPage(pr, routeId, routeTown));
    }

    @Nullable
    public static TownRecord resolveTownAtPlayerForUi(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        return resolveTownAtPlayer(world, plugin, store, playerRef);
    }

    private static void openAssignGuardPage(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull PatrolWandPlayerComponent st,
        @Nonnull Store<EntityStore> store
    ) {
        UUID routeId = st.getSelectedRouteId();
        if (routeId == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.selectRouteFirst"));
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        PatrolRouteRecord route = reg.get(routeId);
        if (route == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.routeMissing"));
            return;
        }
        UUID routeTown = route.getTownIdUuid();
        if (routeTown == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.routeMissing"));
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        if (player.getPageManager().getCustomPage() != null) {
            return;
        }
        player.getPageManager().openCustomPage(playerRef, store, new PatrolWandAssignGuardPage(pr, routeId, routeTown));
    }

    public static void assignGuardToRoute(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull PatrolRouteRecord route,
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull UUID guardUuid
    ) {
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        route.assignedGuardUuid = guardUuid.toString();
        reg.upsert(route);
        PatrolRoutePersistence.save(world, plugin, reg);
        UUID routeId = route.getIdUuid();
        if (routeId != null) {
            GuardPatrolState st = store.getComponent(guardRef, GuardPatrolState.getComponentType());
            if (st == null) {
                st = new GuardPatrolState();
                store.addComponent(guardRef, GuardPatrolState.getComponentType(), st);
            }
            st.setActiveRouteId(routeId);
            st.resetProgress();
            store.putComponent(guardRef, GuardPatrolState.getComponentType(), st);
        }
    }

    private static void handleRemoveRoute(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull PatrolWandPlayerComponent st,
        @Nonnull InteractionContext context,
        @Nonnull Store<EntityStore> store
    ) {
        UUID targetId = st.getSelectedRouteId();
        if (targetId == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.removeNeedSelection"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        PatrolRouteRecord existing = reg.get(targetId);
        if (existing == null) {
            st.setSelectedRouteId(null);
            commandBuffer.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.removeUnknown"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        String routeName = existing.safeDisplayName();
        @Nullable
        UUID guardUuid = existing.getAssignedGuardUuidParsed();
        @Nullable
        PatrolRouteRecord removed = reg.remove(targetId);
        if (removed == null) {
            st.setSelectedRouteId(null);
            commandBuffer.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.removeUnknown"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        PatrolRoutePersistence.save(world, plugin, reg);
        if (guardUuid != null) {
            Ref<EntityStore> guardRef = store.getExternalData().getRefFromUUID(guardUuid);
            if (guardRef != null && guardRef.isValid()) {
                GuardPatrolState gps = store.getComponent(guardRef, GuardPatrolState.getComponentType());
                if (gps != null && targetId.equals(gps.getActiveRouteId())) {
                    gps.setActiveRouteId(null);
                    gps.resetProgress();
                    commandBuffer.putComponent(guardRef, GuardPatrolState.getComponentType(), gps);
                }
            }
        }
        if (targetId.equals(st.getEditingRouteId()) || targetId.equals(st.getSelectedRouteId())) {
            st.startNewRoute();
        }
        commandBuffer.putComponent(playerRef, PatrolWandPlayerComponent.getComponentType(), st);
        send(
            playerRef,
            commandBuffer,
            Message
                .translation("aetherhaven_items.aetherhaven.patrolWand.removedRoute")
                .param("name", routeName)
        );
        toast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.patrolWand.toastRemovedRoute");
    }

    private static void wrongModeToast(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        toast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.patrolWand.wrongMode");
    }

    private static void clearGuardFromSelectedRoute(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull PatrolWandPlayerComponent st
    ) {
        UUID routeId = st.getSelectedRouteId();
        if (routeId == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.selectRouteFirst"));
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        PatrolRouteRecord route = reg.get(routeId);
        if (route == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.routeMissing"));
            return;
        }
        route.assignedGuardUuid = null;
        reg.upsert(route);
        PatrolRoutePersistence.save(world, plugin, reg);
        send(
            playerRef,
            commandBuffer,
            Message
                .translation("aetherhaven_items.aetherhaven.patrolWand.clearedGuard")
                .param("route", route.safeDisplayName())
        );
        toast(playerRef, commandBuffer, "aetherhaven_items.aetherhaven.patrolWand.toastCleared");
    }

    public static boolean isValidGuardForRoute(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PatrolRouteRecord route
    ) {
        TownVillagerBinding binding = store.getComponent(guardRef, TownVillagerBinding.getComponentType());
        if (binding == null || !TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
            return false;
        }
        UUID routeTown = route.getTownIdUuid();
        if (routeTown == null || !routeTown.equals(binding.getTownId())) {
            return false;
        }
        NPCEntity npc = store.getComponent(guardRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null) {
            return false;
        }
        String role = npc.getRoleName();
        return AetherhavenConstants.NPC_GUARD_KNIGHT.equals(role)
            || AetherhavenConstants.NPC_GUARD_ARCHER.equals(role)
            || AetherhavenConstants.NPC_GUARD_MAGE.equals(role)
            || AetherhavenConstants.NPC_GUARD_ROGUE.equals(role);
    }

    @Nonnull
    public static String guardDisplayName(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> guardRef) {
        PersistentDisplayName dn = store.getComponent(guardRef, PersistentDisplayName.getComponentType());
        if (dn != null && dn.getDisplayName() != null) {
            return dn.getDisplayName().getRawText();
        }
        return "Guard";
    }

    private static boolean requireMember(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        UUIDComponent uc = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        TownRecord town = resolveTown(world, plugin, targetBlock);
        if (town == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.notInTown"));
            return false;
        }
        if (!town.isMemberPlayer(uc.getUuid()) && !town.getOwnerUuid().equals(uc.getUuid())) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.notMember"));
            return false;
        }
        return true;
    }

    private static boolean requireMemberAtPlayer(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        UUIDComponent uc = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        TownRecord town = resolveTownAtPlayer(world, plugin, store, playerRef);
        if (town == null) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.notInTown"));
            return false;
        }
        if (!town.isMemberPlayer(uc.getUuid()) && !town.getOwnerUuid().equals(uc.getUuid())) {
            send(playerRef, commandBuffer, Message.translation("aetherhaven_items.aetherhaven.patrolWand.notMember"));
            return false;
        }
        return true;
    }

    @Nullable
    private static TownRecord resolveTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3i block
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        return tm.findTownContainingBlock(world.getName(), block.x(), block.z());
    }

    @Nullable
    private static TownRecord resolveTownAtPlayer(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (tc == null) {
            return null;
        }
        Vector3d pos = tc.getPosition();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        return tm.findTownContainingBlock(world.getName(), (int) Math.floor(pos.x), (int) Math.floor(pos.z));
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

    private static void toast(
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

    private static void playSound(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        int idx = SoundEvent.getAssetMap().getIndex(SAVE_SOUND);
        if (idx <= 0) {
            return;
        }
        @Nullable
        PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            SoundUtil.playSoundEvent2dToPlayer(pr, idx, SoundCategory.SFX);
        }
    }
}
