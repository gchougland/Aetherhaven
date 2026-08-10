package com.hexvane.aetherhaven.festival.carnival;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
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

/** Dialogue conditions and actions for carnival balloon / wheel attendants. */
public final class CarnivalDialogueHandlers {
    private CarnivalDialogueHandlers() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        DialogueConditionRegistry conditions = plugin.getDialogueConditionRegistry();
        conditions.register("carnival_balloon_can_start", (c, p, s, n) -> balloonCanStart(p, s, n));
        conditions.register("carnival_balloon_busy", (c, p, s, n) -> balloonBusy(p, s, n));
        conditions.register("carnival_balloon_is_playing", (c, p, s, n) -> balloonPlaying(p, s, n));
        conditions.register("carnival_balloon_has_result", (c, p, s, n) -> balloonHasResult(p, s, n));

        conditions.register("carnival_wheel_can_start", (c, p, s, n) -> wheelCanStart(p, s, n));
        conditions.register("carnival_wheel_busy", (c, p, s, n) -> wheelBusy(p, s, n));
        conditions.register("carnival_wheel_is_spinning", (c, p, s, n) -> wheelSpinning(p, s, n));
        conditions.register("carnival_wheel_has_win", (c, p, s, n) -> wheelHasWin(p, s, n));
        conditions.register("carnival_wheel_has_loss", (c, p, s, n) -> wheelHasLoss(p, s, n));

        DialogueActionRegistry actions = plugin.getDialogueActionRegistry();
        actions.register("carnival_balloon_start", CarnivalDialogueHandlers::balloonStart);
        actions.register("carnival_balloon_collect", CarnivalDialogueHandlers::balloonCollect);
        actions.register("carnival_wheel_start", CarnivalDialogueHandlers::wheelStart);
        actions.register("carnival_wheel_collect", CarnivalDialogueHandlers::wheelCollect);
        actions.register("carnival_wheel_ack_loss", CarnivalDialogueHandlers::wheelAckLoss);
    }

    private static boolean balloonCanStart(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isCarnivalActive(town)) {
            return false;
        }
        return CarnivalBalloonSessionIndex.getOrCreate(town.getTownId()).canStart(playerUuid);
    }

    private static boolean balloonBusy(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isCarnivalActive(town)) {
            return false;
        }
        return CarnivalBalloonSessionIndex.getOrCreate(town.getTownId()).isBusyForOther(playerUuid);
    }

    private static boolean balloonPlaying(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isCarnivalActive(town)) {
            return false;
        }
        return CarnivalBalloonSessionIndex.getOrCreate(town.getTownId()).isPlaying(playerUuid);
    }

    private static boolean balloonHasResult(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isCarnivalActive(town)) {
            return false;
        }
        return CarnivalBalloonSessionIndex.getOrCreate(town.getTownId()).hasResult(playerUuid);
    }

    private static boolean wheelCanStart(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isCarnivalActive(town)) {
            return false;
        }
        return CarnivalWheelSessionIndex.getOrCreate(town.getTownId()).canStart(playerUuid);
    }

    private static boolean wheelBusy(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isCarnivalActive(town)) {
            return false;
        }
        return CarnivalWheelSessionIndex.getOrCreate(town.getTownId()).isBusyForOther(playerUuid);
    }

    private static boolean wheelSpinning(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isCarnivalActive(town)) {
            return false;
        }
        return CarnivalWheelSessionIndex.getOrCreate(town.getTownId()).isSpinning(playerUuid);
    }

    private static boolean wheelHasWin(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isCarnivalActive(town)) {
            return false;
        }
        return CarnivalWheelSessionIndex.getOrCreate(town.getTownId()).hasWin(playerUuid);
    }

    private static boolean wheelHasLoss(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isCarnivalActive(town)) {
            return false;
        }
        return CarnivalWheelSessionIndex.getOrCreate(town.getTownId()).hasLoss(playerUuid);
    }

    private static void balloonStart(
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
        if (town == null || playerUuid == null || player == null || plugin == null || !isCarnivalActive(town)) {
            out.setGotoNodeId("busy");
            return;
        }
        CarnivalBalloonSession session = CarnivalBalloonSessionIndex.getOrCreate(town.getTownId());
        if (!session.canStart(playerUuid)) {
            out.setGotoNodeId("busy");
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, playerUuid);
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, playerUuid);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null
            || !GoldCoinPayment.canAfford(payerTown, inv, CarnivalIds.GAME_COST_GOLD, allowTreasury)) {
            out.setGotoNodeId("busy");
            return;
        }
        SpendBreakdown paid =
            GoldCoinPayment.trySpendReturningBreakdown(payerTown, inv, CarnivalIds.GAME_COST_GOLD, allowTreasury);
        if (paid == null) {
            out.setGotoNodeId("busy");
            return;
        }
        if (!session.tryBegin(playerUuid)) {
            GoldCoinPayment.refund(payerTown, player, playerRef, store, paid);
            if (payerTown != null) {
                tm.updateTown(payerTown);
            }
            out.setGotoNodeId("busy");
            return;
        }
        if (payerTown != null) {
            tm.updateTown(payerTown);
        }
        player.giveItem(new ItemStack(CarnivalIds.DART_ITEM_ID, CarnivalIds.BALLOON_DARTS), playerRef, store);
        CarnivalAudio.playBalloonStart(store, CarnivalAudio.squareCenter(plugin, town));
    }

    private static void balloonCollect(
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
            return;
        }
        CarnivalBalloonSession session = CarnivalBalloonSessionIndex.get(town.getTownId());
        if (session == null) {
            return;
        }
        int tickets = session.collectResult(playerUuid);
        if (tickets < 0) {
            return;
        }
        removeDarts(store, playerRef, CarnivalIds.BALLOON_DARTS);
        if (tickets > 0) {
            player.giveItem(new ItemStack(CarnivalIds.SUMMER_TICKET_ITEM_ID, tickets), playerRef, store);
        }
    }

    private static void wheelStart(
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
        if (town == null || playerUuid == null || player == null || plugin == null || !isCarnivalActive(town)) {
            out.setGotoNodeId("busy");
            return;
        }
        CarnivalWheelSession session = CarnivalWheelSessionIndex.getOrCreate(town.getTownId());
        if (!session.canStart(playerUuid)) {
            out.setGotoNodeId("busy");
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, playerUuid);
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, playerUuid);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null
            || !GoldCoinPayment.canAfford(payerTown, inv, CarnivalIds.GAME_COST_GOLD, allowTreasury)) {
            out.setGotoNodeId("busy");
            return;
        }
        SpendBreakdown paid =
            GoldCoinPayment.trySpendReturningBreakdown(payerTown, inv, CarnivalIds.GAME_COST_GOLD, allowTreasury);
        if (paid == null) {
            out.setGotoNodeId("busy");
            return;
        }
        float currentRoll = CarnivalIds.WHEEL_IDLE_OFFSET_RAD;
        UUID faceUuid = session.getFaceEntityUuid();
        if (faceUuid != null) {
            Ref<EntityStore> faceRef = store.getExternalData().getRefFromUUID(faceUuid);
            if (faceRef != null && faceRef.isValid()) {
                CarnivalWheelFaceComponent face =
                    store.getComponent(faceRef, CarnivalWheelFaceComponent.getComponentType());
                if (face != null) {
                    currentRoll = face.getRoll();
                }
            }
        }
        if (!session.tryBegin(playerUuid, currentRoll)) {
            GoldCoinPayment.refund(payerTown, player, playerRef, store, paid);
            if (payerTown != null) {
                tm.updateTown(payerTown);
            }
            out.setGotoNodeId("busy");
            return;
        }
        if (payerTown != null) {
            tm.updateTown(payerTown);
        }
    }

    private static void wheelCollect(
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
            return;
        }
        CarnivalWheelSession session = CarnivalWheelSessionIndex.get(town.getTownId());
        if (session == null) {
            return;
        }
        int tickets = session.collectWin(playerUuid);
        if (tickets > 0) {
            player.giveItem(new ItemStack(CarnivalIds.SUMMER_TICKET_ITEM_ID, tickets), playerRef, store);
        }
    }

    private static void wheelAckLoss(
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
        CarnivalWheelSession session = CarnivalWheelSessionIndex.get(town.getTownId());
        if (session != null) {
            session.acknowledgeLoss(playerUuid);
        }
    }

    private static void removeDarts(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        int maxCount
    ) {
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null || maxCount <= 0) {
            return;
        }
        inv.removeItemStack(new ItemStack(CarnivalIds.DART_ITEM_ID, maxCount));
    }

    private static boolean isCarnivalActive(@Nonnull TownRecord town) {
        return CarnivalIds.FESTIVAL_ID.equals(town.getActiveFestivalId());
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
        TownRecord atNpc = townWithCarnivalAt(store, tm, world.getName(), npcRef);
        if (atNpc != null) {
            return atNpc;
        }
        return townWithCarnivalAt(store, tm, world.getName(), playerRef);
    }

    @Nullable
    private static TownRecord townWithCarnivalAt(
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
        if (town != null && isCarnivalActive(town)) {
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
            if (!isCarnivalActive(town)) {
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
}
