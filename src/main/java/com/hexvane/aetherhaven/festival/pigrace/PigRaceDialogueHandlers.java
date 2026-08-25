package com.hexvane.aetherhaven.festival.pigrace;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.festival.FestivalRewardNotify;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.plugin.DialogueActionRegistry;
import com.hexvane.aetherhaven.plugin.DialogueConditionRegistry;
import com.hexvane.aetherhaven.shopspot.ShopSpotBuyerPayment;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
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

/** Dialogue conditions and actions for the pig race merchant. */
public final class PigRaceDialogueHandlers {
    private PigRaceDialogueHandlers() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        DialogueConditionRegistry conditions = plugin.getDialogueConditionRegistry();
        conditions.register("pig_race_can_bet", (c, playerRef, store, npcRef) -> canBet(playerRef, store, npcRef));
        conditions.register("pig_race_can_start", (c, playerRef, store, npcRef) -> canStart(playerRef, store, npcRef));
        conditions.register(
            "pig_race_has_winnings",
            (c, playerRef, store, npcRef) -> hasWinnings(playerRef, store, npcRef)
        );
        conditions.register("pig_race_has_loss", (c, playerRef, store, npcRef) -> hasLoss(playerRef, store, npcRef));

        DialogueActionRegistry actions = plugin.getDialogueActionRegistry();
        actions.register("pig_race_set_stake", PigRaceDialogueHandlers::setStake);
        actions.register("pig_race_place_bet", PigRaceDialogueHandlers::placeBet);
        actions.register("pig_race_start", PigRaceDialogueHandlers::startRace);
        actions.register("pig_race_collect", PigRaceDialogueHandlers::collect);
        actions.register("pig_race_ack_loss", PigRaceDialogueHandlers::ackLoss);
    }

    private static boolean canBet(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isPigRaceActive(town)) {
            return false;
        }
        ensureRacersReady(store, town);
        PigRaceSession session = PigRaceSessionIndex.getOrCreate(town.getTownId());
        return session.canPlaceBet(playerUuid);
    }

    private static boolean canStart(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        if (town == null || !isPigRaceActive(town)) {
            return false;
        }
        ensureRacersReady(store, town);
        return PigRaceSessionIndex.getOrCreate(town.getTownId()).canStartRace();
    }

    private static boolean hasWinnings(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null) {
            return false;
        }
        PigRaceSession session = PigRaceSessionIndex.get(town.getTownId());
        return session != null && session.hasWinnings(playerUuid);
    }

    private static boolean hasLoss(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null) {
            return false;
        }
        PigRaceSession session = PigRaceSessionIndex.get(town.getTownId());
        return session != null && session.hasPendingLoss(playerUuid);
    }

    private static void setStake(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        int amount = intField(action, "amount", 0);
        if (town == null || playerUuid == null || !PigRaceLanes.isAllowedBet(amount)) {
            return;
        }
        PigRaceSessionIndex.getOrCreate(town.getTownId()).setPendingStake(playerUuid, amount);
    }

    private static void placeBet(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        int lane = intField(action, "lane", -1);
        if (town == null || playerUuid == null || !isPigRaceActive(town)) {
            out.setGotoNodeId("bet_failed");
            return;
        }
        PigRaceSession session = PigRaceSessionIndex.getOrCreate(town.getTownId());
        int amount = session.takePendingStake(playerUuid);
        if (amount <= 0 || !session.canPlaceBet(playerUuid) || session.racersView().isEmpty()) {
            out.setGotoNodeId("bet_failed");
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            session.setPendingStake(playerUuid, amount);
            out.setGotoNodeId("bet_failed");
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        // Pay from the player's own / member town treasury (not the festival town they're visiting).
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, playerUuid);
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, playerUuid);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null || !GoldCoinPayment.canAfford(payerTown, inv, amount, allowTreasury)) {
            session.setPendingStake(playerUuid, amount);
            out.setGotoNodeId("bet_failed");
            return;
        }
        SpendBreakdown paid = GoldCoinPayment.trySpendReturningBreakdown(payerTown, inv, amount, allowTreasury);
        if (paid == null) {
            session.setPendingStake(playerUuid, amount);
            out.setGotoNodeId("bet_failed");
            return;
        }
        if (!session.placeBet(playerUuid, lane, amount)) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player != null) {
                GoldCoinPayment.refund(payerTown, player, playerRef, store, paid);
            }
            if (payerTown != null) {
                tm.updateTown(payerTown);
            }
            session.setPendingStake(playerUuid, amount);
            out.setGotoNodeId("bet_failed");
            return;
        }
        if (payerTown != null) {
            tm.updateTown(payerTown);
        }
        // Pig positions are reset on the next lobby tick via needsReturnToStart (no Store writes here).
    }

    private static void startRace(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        if (town == null || !isPigRaceActive(town)) {
            out.setGotoNodeId("race_busy");
            return;
        }
        PigRaceSession session = PigRaceSessionIndex.getOrCreate(town.getTownId());
        if (!session.canStartRace()) {
            out.setGotoNodeId("race_busy");
            return;
        }
        // Speeds and start-line reset are applied by PigRaceSystem on the first race tick.
        if (!session.beginRacing()) {
            out.setGotoNodeId("race_busy");
        }
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
        PigRaceSession session = PigRaceSessionIndex.get(town.getTownId());
        if (session == null) {
            out.setGotoNodeId("collect_none");
            return;
        }
        int tickets = session.collectWinnings(playerUuid);
        if (tickets <= 0) {
            out.setGotoNodeId("collect_none");
            return;
        }
        FestivalRewardNotify.giveAndNotify(
            player,
            playerRef,
            store,
            new ItemStack(PigRaceLanes.SPRING_TICKET_ITEM_ID, tickets)
        );
    }

    private static void ackLoss(
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
        PigRaceSession session = PigRaceSessionIndex.get(town.getTownId());
        if (session != null && session.acknowledgeLoss(playerUuid)) {
            FestivalRewardNotify.notifyLoss(store, playerRef);
        }
    }

    private static boolean isPigRaceActive(@Nonnull TownRecord town) {
        return PigRaceLanes.FESTIVAL_ID.equals(town.getActiveFestivalId());
    }

    /** Rebuilds the race lobby after a restart when the festival is still active but pigs were forgotten. */
    private static void ensureRacersReady(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        PigRaceSession session = PigRaceSessionIndex.getOrCreate(town.getTownId());
        if (!session.racersView().isEmpty()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        if (square == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        try {
            PigRaceSpawnService.ensureRacersForFestival(world, store, plugin, town, square);
        } catch (RuntimeException e) {
            world.execute(() ->
                PigRaceSpawnService.ensureRacersForFestival(world, store, plugin, town, square)
            );
        }
    }

    /**
     * Town for this dialogue: prefer the town that owns the festival merchant, so bets always bind to the race you
     * are talking to (not the player's owned / active town elsewhere).
     */
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

        TownRecord fromMerchant = townForFestivalNpc(tm, store, npcRef);
        if (fromMerchant != null) {
            return fromMerchant;
        }
        TownRecord atNpc = townWithPigRaceAt(store, tm, world.getName(), npcRef);
        if (atNpc != null) {
            return atNpc;
        }
        return townWithPigRaceAt(store, tm, world.getName(), playerRef);
    }

    @Nullable
    private static TownRecord townWithPigRaceAt(
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
        if (town != null && isPigRaceActive(town)) {
            return town;
        }
        return null;
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
        UUID npcUuid = playerUuid(npcRef, store);
        if (npcUuid == null) {
            return null;
        }
        String id = npcUuid.toString();
        for (TownRecord town : tm.allTowns()) {
            if (!isPigRaceActive(town)) {
                continue;
            }
            for (String raw : town.getActiveFestivalNpcEntityUuids()) {
                if (raw != null && id.equalsIgnoreCase(raw.trim())) {
                    return town;
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

    private static int intField(@Nonnull JsonObject o, @Nonnull String key, int def) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            return def;
        }
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }
}
