package com.hexvane.aetherhaven.festival.treeclimb;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.festival.FestivalRewardNotify;
import com.hexvane.aetherhaven.plugin.DialogueActionRegistry;
import com.hexvane.aetherhaven.plugin.DialogueConditionRegistry;
import com.hexvane.aetherhaven.shopspot.ShopSpotBuyerPayment;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Dialogue conditions and actions for the tree climb attendant and merchant. */
public final class TreeClimbDialogueHandlers {
    private TreeClimbDialogueHandlers() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        DialogueConditionRegistry conditions = plugin.getDialogueConditionRegistry();
        conditions.register("tree_climb_can_join", (c, playerRef, store, npcRef) -> canJoin(playerRef, store, npcRef));
        conditions.register("tree_climb_can_leave", (c, playerRef, store, npcRef) -> canLeave(playerRef, store, npcRef));
        conditions.register("tree_climb_can_start", (c, playerRef, store, npcRef) -> canStart(playerRef, store, npcRef));
        conditions.register("tree_climb_race_busy", (c, playerRef, store, npcRef) -> raceBusy(playerRef, store, npcRef));
        conditions.register(
            "tree_climb_has_tickets",
            (c, playerRef, store, npcRef) -> hasTickets(playerRef, store, npcRef)
        );

        DialogueActionRegistry actions = plugin.getDialogueActionRegistry();
        actions.register("tree_climb_join", TreeClimbDialogueHandlers::join);
        actions.register("tree_climb_leave", TreeClimbDialogueHandlers::leave);
        actions.register("tree_climb_start", TreeClimbDialogueHandlers::startRace);
        actions.register("tree_climb_collect", TreeClimbDialogueHandlers::collect);
        actions.register("tree_climb_open_leaderboard", TreeClimbDialogueHandlers::openLeaderboard);
    }

    private static boolean canJoin(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isTreeClimbActive(town)) {
            return false;
        }
        TreeClimbSession session = sessionReady(store, town);
        if (session == null || !session.canJoin(playerUuid)) {
            return false;
        }
        return canAffordEntry(playerRef, store, playerUuid);
    }

    private static boolean canLeave(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isTreeClimbActive(town)) {
            return false;
        }
        TreeClimbSession session = TreeClimbSessionIndex.get(town.getTownId());
        return session != null
            && session.getPhase() == TreeClimbSession.Phase.LOBBY
            && session.isJoined(playerUuid);
    }

    private static boolean canStart(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isTreeClimbActive(town)) {
            return false;
        }
        TreeClimbSession session = sessionReady(store, town);
        return session != null && session.canStartRace() && session.isJoined(playerUuid);
    }

    private static boolean raceBusy(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        if (town == null || !isTreeClimbActive(town)) {
            return false;
        }
        TreeClimbSession session = TreeClimbSessionIndex.get(town.getTownId());
        return session != null && session.isRaceBusy();
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
        TreeClimbSession session = TreeClimbSessionIndex.get(town.getTownId());
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
        Player player = store.getComponent(playerRef, Player.getComponentType());
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (town == null || playerUuid == null || player == null || plugin == null) {
            out.setGotoNodeId("join_failed");
            return;
        }
        TreeClimbSession session = sessionReady(store, town);
        if (session == null || !session.canJoin(playerUuid)) {
            out.setGotoNodeId("join_failed");
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, playerUuid);
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, playerUuid);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null
            || !GoldCoinPayment.canAfford(payerTown, inv, TreeClimbIds.RACE_COST_GOLD, allowTreasury)) {
            out.setGotoNodeId("join_need_gold");
            return;
        }
        SpendBreakdown paid =
            GoldCoinPayment.trySpendReturningBreakdown(
                payerTown, inv, TreeClimbIds.RACE_COST_GOLD, allowTreasury
            );
        if (paid == null) {
            out.setGotoNodeId("join_need_gold");
            return;
        }
        if (!session.join(playerUuid, paid)) {
            GoldCoinPayment.refund(payerTown, player, playerRef, store, paid);
            if (payerTown != null) {
                tm.updateTown(payerTown);
            }
            out.setGotoNodeId("join_failed");
            return;
        }
        if (payerTown != null) {
            tm.updateTown(payerTown);
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
        Player player = store.getComponent(playerRef, Player.getComponentType());
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (town == null || playerUuid == null || player == null || plugin == null) {
            return;
        }
        TreeClimbSession session = TreeClimbSessionIndex.get(town.getTownId());
        if (session == null) {
            return;
        }
        SpendBreakdown fee = session.leave(playerUuid);
        if (fee == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, playerUuid);
        GoldCoinPayment.refund(payerTown, player, playerRef, store, fee);
        if (payerTown != null) {
            tm.updateTown(payerTown);
        }
    }

    private static void startRace(
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
        TreeClimbSession session = sessionReady(store, town);
        if (session == null || !session.isJoined(playerUuid) || !session.canStartRace()) {
            out.setGotoNodeId(session != null && session.isRaceBusy() ? "race_busy" : "start_failed");
            return;
        }
        long now = System.currentTimeMillis();
        if (!session.beginRacing(now)) {
            out.setGotoNodeId("start_failed");
            return;
        }
        // Close dialogue; teleports run on the next race-system tick via CommandBuffer.
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
        TreeClimbSession session = TreeClimbSessionIndex.get(town.getTownId());
        if (session == null) {
            out.setGotoNodeId("collect_none");
            return;
        }
        int tickets = session.collectTickets(playerUuid);
        if (tickets <= 0) {
            out.setGotoNodeId("collect_none");
            return;
        }
        FestivalRewardNotify.giveAndNotify(
            player,
            playerRef,
            store,
            new ItemStack(TreeClimbIds.SUMMER_TICKET_ITEM_ID, tickets)
        );
    }

    private static void openLeaderboard(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        // Open like barter: do not close() before openCustomPage (PageManager ACK race).
        out.setCloseDialogue(true);
        out.setOpenTreeClimbLeaderboardAfterClose(true);
    }

    private static boolean canAffordEntry(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, playerUuid);
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, playerUuid);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        return inv != null
            && GoldCoinPayment.canAfford(payerTown, inv, TreeClimbIds.RACE_COST_GOLD, allowTreasury);
    }

    @Nullable
    private static TreeClimbSession sessionReady(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        TreeClimbSession session = TreeClimbSessionIndex.getOrCreate(town.getTownId());
        TreeClimbCourse.ensureCourse(plugin, town, session);
        return session;
    }

    private static boolean isTreeClimbActive(@Nonnull TownRecord town) {
        return TreeClimbIds.FESTIVAL_ID.equals(town.getActiveFestivalId());
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
        TownRecord atNpc = townWithTreeClimbAt(store, tm, world.getName(), npcRef);
        if (atNpc != null) {
            return atNpc;
        }
        return townWithTreeClimbAt(store, tm, world.getName(), playerRef);
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
            if (!isTreeClimbActive(town)) {
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
    private static TownRecord townWithTreeClimbAt(
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
        if (town != null && isTreeClimbActive(town)) {
            return town;
        }
        return null;
    }
}
