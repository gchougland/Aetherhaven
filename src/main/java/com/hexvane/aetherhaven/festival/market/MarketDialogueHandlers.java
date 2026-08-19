package com.hexvane.aetherhaven.festival.market;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.festival.FestivalRewardNotify;
import com.hexvane.aetherhaven.plugin.DialogueActionRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
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

/** Dialogue actions for Market Festival judging and the town scoreboard. */
public final class MarketDialogueHandlers {
    private MarketDialogueHandlers() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        DialogueActionRegistry actions = plugin.getDialogueActionRegistry();
        actions.register("market_start_judging", MarketDialogueHandlers::startJudging);
        actions.register("market_claim_results", MarketDialogueHandlers::claimResults);
        actions.register("market_open_scoreboard", MarketDialogueHandlers::openScoreboard);
        actions.register("market_open_stall", MarketDialogueHandlers::openStall);
    }

    private static void openStall(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (town == null || playerUuid == null || player == null || !isMarketActive(town)) {
            out.setCloseDialogue(true);
            return;
        }
        if (!town.hasMemberOrOwner(playerUuid)) {
            out.setCloseDialogue(true);
            return;
        }
        MarketSession session = MarketSessionIndex.getOrCreate(town.getTownId());
        if (session.isStallLocked()) {
            out.setCloseDialogue(true);
            return;
        }
        UUID townId = town.getTownId();
        out.setCloseDialogue(true);
        out.setAfterClose(
            () -> {
                Ref<EntityStore> pref = playerRef.isValid() ? playerRef : null;
                if (pref == null) {
                    return;
                }
                Store<EntityStore> st = pref.getStore();
                Player p = st.getComponent(pref, Player.getComponentType());
                MarketSession live = MarketSessionIndex.get(townId);
                if (p == null || live == null || live.isStallLocked()) {
                    return;
                }
                MarketStallService.openStall(p, pref, st, live, townId);
            }
        );
    }

    private static void startJudging(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isMarketActive(town) || !town.hasMemberOrOwner(playerUuid)) {
            out.setCloseDialogue(true);
            return;
        }
        MarketSession session = MarketSessionIndex.getOrCreate(town.getTownId());
        session.snapshotFromContainer(session.getLiveContainer());
        if (!session.tryBeginJudging(System.currentTimeMillis())) {
            out.setCloseDialogue(true);
            return;
        }
        out.setCloseDialogue(true);
    }

    private static void claimResults(
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
        if (town == null || playerUuid == null || player == null || plugin == null || !isMarketActive(town)) {
            out.setCloseDialogue(true);
            return;
        }
        MarketSession session = MarketSessionIndex.get(town.getTownId());
        if (session == null || !session.isJudged()) {
            out.setCloseDialogue(true);
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world != null) {
            MarketLeaderboard.recordTown(world, plugin, town, session.getScore());
        }
        if (session.consumeAnnounce()) {
            MarketAnnounce.announceResults(store, town, session, playerUuid);
        }
        if (town.hasMemberOrOwner(playerUuid)) {
            if (!session.hasClaimedTickets(playerUuid)) {
                int tickets = MarketRewards.ticketCount(session.getScore(), session.getPlace());
                if (tickets > 0) {
                    FestivalRewardNotify.giveAndNotify(
                        player,
                        playerRef,
                        store,
                        new ItemStack(MarketIds.AUTUMN_TICKET_ITEM_ID, tickets)
                    );
                }
                session.markTicketsClaimed(playerUuid);
            }
            if (MarketRewards.grantsPlushie(session.getPlace()) && !session.isPlushieGranted()) {
                FestivalRewardNotify.giveAndNotify(
                    player,
                    playerRef,
                    store,
                    new ItemStack(MarketIds.CORIN_PLUSHIE_ITEM_ID, 1)
                );
                FestivalRewardNotify.giveAndNotify(
                    player,
                    playerRef,
                    store,
                    new ItemStack(MarketIds.HEARTBERRY_ITEM_ID, 1)
                );
                session.markPlushieGranted();
            }
            MarketStallService.returnStallGoodsToPlayer(player, playerRef, store, session, town.getTownId());
        }
        out.setCloseDialogue(true);
    }

    private static void openScoreboard(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        out.setCloseDialogue(true);
        out.setOpenMarketLeaderboardAfterClose(true);
    }

    private static boolean isMarketActive(@Nonnull TownRecord town) {
        return MarketIds.FESTIVAL_ID.equals(town.getActiveFestivalId());
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
        TownRecord atNpc = townWithFestivalAt(store, tm, world.getName(), npcRef);
        if (atNpc != null) {
            return atNpc;
        }
        return townWithFestivalAt(store, tm, world.getName(), playerRef);
    }

    @Nullable
    private static TownRecord townWithFestivalAt(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm,
        @Nonnull String worldName,
        @Nullable Ref<EntityStore> entityRef
    ) {
        if (entityRef == null || !entityRef.isValid()) {
            return null;
        }
        TransformComponent tc = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (tc == null) {
            return null;
        }
        Vector3d pos = tc.getPosition();
        TownRecord town =
            tm.findTownContainingBlock(worldName, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (town != null && isMarketActive(town)) {
            return town;
        }
        UUIDComponent uc = store.getComponent(entityRef, UUIDComponent.getComponentType());
        if (uc != null) {
            for (TownRecord t : tm.allTowns()) {
                if (isMarketActive(t) && t.hasMemberOrOwner(uc.getUuid())) {
                    return t;
                }
            }
        }
        return null;
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }
}
