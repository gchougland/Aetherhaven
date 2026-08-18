package com.hexvane.aetherhaven.festival.hallowseve;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalLookSelection;
import com.hexvane.aetherhaven.festival.FestivalService;
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
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Dialogue conditions and actions for the Hallow's Eve merchant. */
public final class HallowsEveDialogueHandlers {
    private HallowsEveDialogueHandlers() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        DialogueConditionRegistry conditions = plugin.getDialogueConditionRegistry();
        conditions.register("hallows_eve_maze_can_start", (c, p, s, n) -> mazeCanStart(p, s, n));
        conditions.register("hallows_eve_maze_busy", (c, p, s, n) -> mazeBusy(p, s, n));
        conditions.register("hallows_eve_maze_playing", (c, p, s, n) -> mazePlaying(p, s, n));

        DialogueActionRegistry actions = plugin.getDialogueActionRegistry();
        actions.register("hallows_eve_maze_start", HallowsEveDialogueHandlers::mazeStart);
        actions.register("hallows_eve_open_scoreboard", HallowsEveDialogueHandlers::openScoreboard);
    }

    private static boolean mazeCanStart(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isHallowsEveActive(town)) {
            return false;
        }
        return HallowsEveSessionIndex.getOrCreate(town.getTownId()).canStart(playerUuid);
    }

    private static boolean mazeBusy(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isHallowsEveActive(town)) {
            return false;
        }
        return HallowsEveSessionIndex.getOrCreate(town.getTownId()).isBusyForOther(playerUuid);
    }

    private static boolean mazePlaying(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null || !isHallowsEveActive(town)) {
            return false;
        }
        HallowsEveSession session = HallowsEveSessionIndex.get(town.getTownId());
        return session != null && session.isRacer(playerUuid) && session.isBusy();
    }

    private static void mazeStart(
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
        if (town == null || playerUuid == null || player == null || plugin == null || !isHallowsEveActive(town)) {
            out.setGotoNodeId("maze_busy");
            return;
        }
        HallowsEveSession session = HallowsEveSessionIndex.getOrCreate(town.getTownId());
        if (!session.canStart(playerUuid)) {
            out.setGotoNodeId("maze_busy");
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord payerTown = ShopSpotBuyerPayment.buyerHomeTown(tm, playerUuid);
        boolean allowTreasury = ShopSpotBuyerPayment.mayDebitBuyerTownTreasury(payerTown, playerUuid);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null
            || !GoldCoinPayment.canAfford(payerTown, inv, HallowsEveIds.MAZE_COST_GOLD, allowTreasury)) {
            out.setGotoNodeId("maze_need_gold");
            return;
        }
        SpendBreakdown paid =
            GoldCoinPayment.trySpendReturningBreakdown(
                payerTown, inv, HallowsEveIds.MAZE_COST_GOLD, allowTreasury
            );
        if (paid == null) {
            out.setGotoNodeId("maze_need_gold");
            return;
        }
        if (!session.tryBegin(playerUuid, System.currentTimeMillis())) {
            GoldCoinPayment.refund(payerTown, player, playerRef, store, paid);
            if (payerTown != null) {
                tm.updateTown(payerTown);
            }
            out.setGotoNodeId("maze_busy");
            return;
        }
        if (payerTown != null) {
            tm.updateTown(payerTown);
        }
        if (session.getStartX() == 0.0 && session.getStartY() == 0.0 && session.getStartZ() == 0.0) {
            var square = FestivalService.findFestivalSquare(plugin, town);
            FestivalDefinition festival = FestivalLookSelection.activeLayout(plugin, town);
            if (square != null && festival != null) {
                HallowsEveTeleport.bindStartPad(plugin, square, festival, session);
            }
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
        out.setOpenHallowsEveLeaderboardAfterClose(true);
    }

    private static boolean isHallowsEveActive(@Nonnull TownRecord town) {
        return HallowsEveIds.FESTIVAL_ID.equals(town.getActiveFestivalId());
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
        if (town != null && isHallowsEveActive(town)) {
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
            if (!isHallowsEveActive(town)) {
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
