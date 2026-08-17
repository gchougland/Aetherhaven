package com.hexvane.aetherhaven.festival.snowball;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.plugin.DialogueActionRegistry;
import com.hexvane.aetherhaven.plugin.DialogueConditionRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.TownVillagerDirectory;
import com.hexvane.aetherhaven.ui.TownVillagerRow;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Dialogue conditions and actions for the snowball merchant. */
public final class SnowballDialogueHandlers {
    private SnowballDialogueHandlers() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        DialogueConditionRegistry conditions = plugin.getDialogueConditionRegistry();
        conditions.register("snowball_can_join", (c, playerRef, store, npcRef) -> canJoin(playerRef, store, npcRef));
        conditions.register("snowball_can_leave", (c, playerRef, store, npcRef) -> canLeave(playerRef, store, npcRef));
        conditions.register("snowball_can_start", (c, playerRef, store, npcRef) -> canStart(playerRef, store, npcRef));
        conditions.register("snowball_fight_busy", (c, playerRef, store, npcRef) -> fightBusy(playerRef, store, npcRef));
        conditions.register(
            "snowball_has_tickets",
            (c, playerRef, store, npcRef) -> hasTickets(playerRef, store, npcRef)
        );

        DialogueActionRegistry actions = plugin.getDialogueActionRegistry();
        actions.register("snowball_join", SnowballDialogueHandlers::join);
        actions.register("snowball_leave", SnowballDialogueHandlers::leave);
        actions.register("snowball_start", SnowballDialogueHandlers::startFight);
        actions.register("snowball_collect", SnowballDialogueHandlers::collect);
        actions.register("snowball_open_scoreboard", SnowballDialogueHandlers::openScoreboard);
    }

    private static boolean canJoin(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isSnowballActive(town)) {
            return false;
        }
        SnowballSession session = sessionReady(store, town);
        return session != null && session.canJoin(playerUuid);
    }

    private static boolean canLeave(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isSnowballActive(town)) {
            return false;
        }
        SnowballSession session = SnowballSessionIndex.get(town.getTownId());
        return session != null
            && session.getPhase() == SnowballSession.Phase.LOBBY
            && session.isJoined(playerUuid);
    }

    private static boolean canStart(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isSnowballActive(town)) {
            return false;
        }
        SnowballSession session = sessionReady(store, town);
        if (session == null || !session.canStartFight() || !session.isJoined(playerUuid)) {
            return false;
        }
        int residents = 0;
        for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
            if (!session.isJoined(row.entityUuid())) {
                residents++;
            }
        }
        return session.joinedPlayerCount() + residents >= 2;
    }

    private static boolean fightBusy(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        if (town == null || !isSnowballActive(town)) {
            return false;
        }
        SnowballSession session = SnowballSessionIndex.get(town.getTownId());
        return session != null && session.isFightBusy();
    }

    private static boolean hasTickets(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null) {
            return false;
        }
        SnowballSession session = SnowballSessionIndex.get(town.getTownId());
        return session != null && session.hasPendingTickets(playerUuid);
    }

    private static void join(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null) {
            out.setGotoNodeId("join_failed");
            return;
        }
        SnowballSession session = sessionReady(store, town);
        if (session == null || !session.join(playerUuid)) {
            out.setGotoNodeId("join_failed");
        }
    }

    private static void leave(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null) {
            return;
        }
        SnowballSession session = SnowballSessionIndex.get(town.getTownId());
        if (session != null) {
            session.leave(playerUuid);
        }
    }

    private static void startFight(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null) {
            out.setGotoNodeId("start_failed");
            return;
        }
        SnowballSession session = sessionReady(store, town);
        if (session == null || !session.isJoined(playerUuid) || !session.canStartFight()) {
            out.setGotoNodeId(session != null && session.isFightBusy() ? "fight_busy" : "start_failed");
            return;
        }
        if (!canStart(playerRef, store, npcRef)) {
            out.setGotoNodeId("start_failed");
            return;
        }
        if (!session.beginFighting(System.currentTimeMillis())) {
            out.setGotoNodeId("start_failed");
            return;
        }
        out.setCloseDialogue(true);
    }

    private static void collect(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (town == null || playerUuid == null || player == null) {
            out.setGotoNodeId("collect_none");
            return;
        }
        SnowballSession session = SnowballSessionIndex.get(town.getTownId());
        if (session == null) {
            out.setGotoNodeId("collect_none");
            return;
        }
        int tickets = session.collectTickets(playerUuid);
        if (tickets <= 0) {
            out.setGotoNodeId("collect_none");
            return;
        }
        player.giveItem(new ItemStack(SnowballIds.WINTER_TICKET_ITEM_ID, tickets), playerRef, store);
    }

    private static void openScoreboard(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        out.setCloseDialogue(true);
        out.setOpenSnowballLeaderboardAfterClose(true);
    }

    @Nullable
    private static SnowballSession sessionReady(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        SnowballSession session = SnowballSessionIndex.getOrCreate(town.getTownId());
        SnowballCourse.ensureCourse(plugin, town, session);
        return session;
    }

    private static boolean isSnowballActive(@Nonnull TownRecord town) {
        return SnowballIds.FESTIVAL_ID.equals(town.getActiveFestivalId());
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    @Nullable
    private static TownRecord resolveTown(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord fromNpc = townForFestivalNpc(tm, store, npcRef);
        if (fromNpc != null) {
            return fromNpc;
        }
        TownRecord atNpc = townWithSnowballAt(store, tm, world.getName(), npcRef);
        if (atNpc != null) {
            return atNpc;
        }
        return townWithSnowballAt(store, tm, world.getName(), playerRef);
    }

    @Nullable
    private static TownRecord townForFestivalNpc(
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        UUIDComponent uc = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        String uuid = uc.getUuid().toString();
        for (TownRecord town : tm.allTowns()) {
            if (!isSnowballActive(town)) {
                continue;
            }
            for (String raw : town.getActiveFestivalNpcEntityUuids()) {
                if (uuid.equals(raw)) {
                    return town;
                }
            }
        }
        return null;
    }

    @Nullable
    private static TownRecord townWithSnowballAt(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm,
        @Nonnull String worldName,
        @Nullable Ref<EntityStore> ref
    ) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return null;
        }
        Vector3d pos = tc.getPosition();
        TownRecord town = tm.findTownContainingBlock(worldName, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (town != null && isSnowballActive(town)) {
            return town;
        }
        return null;
    }
}
