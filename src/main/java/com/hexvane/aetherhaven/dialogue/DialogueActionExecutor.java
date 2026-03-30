package com.hexvane.aetherhaven.dialogue;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.InnkeeperSpawnService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueActionExecutor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public void runBatch(
        @Nonnull List<JsonObject> actions,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out
    ) {
        runBatch(actions, playerRef, store, out, null);
    }

    public void runBatch(
        @Nonnull List<JsonObject> actions,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        for (JsonObject a : actions) {
            apply(a, playerRef, store, out, npcRef);
        }
    }

    private void apply(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        String type = getType(a);
        if (type == null) {
            LOGGER.atWarning().log("Dialogue action missing type: %s", a);
            return;
        }
        switch (type) {
            case "close" -> out.setCloseDialogue(true);
            case "goto" -> {
                String node = stringField(a, "node");
                if (node != null && !node.isBlank()) {
                    out.setGotoNodeId(node.trim());
                }
            }
            case "open_barter_shop" -> {
                String shop = stringField(a, "shop");
                if (shop != null && !shop.isBlank()) {
                    out.setOpenBarterShopAfterClose(shop.trim());
                }
            }
            case "give_item" -> giveItem(a, playerRef, store);
            case "unlock_achievement" -> LOGGER.atInfo().log(
                "[Dialogue stub] unlock_achievement id=%s",
                stringField(a, "id")
            );
            case "start_quest" -> startQuest(a, playerRef, store, npcRef);
            case "complete_quest" -> completeQuest(a, playerRef, store, npcRef);
            case "abandon_quest" -> abandonQuest(a, playerRef, store);
            default -> LOGGER.atWarning().log("Unknown dialogue action type: %s", type);
        }
    }

    private static void startQuest(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        String id = stringField(a, "id");
        if (id == null || id.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForPlayer(playerRef, store, tm);
        if (town == null) {
            return;
        }
        String qid = id.trim();
        town.addActiveQuest(qid);
        boolean lockInn =
            (a.has("lockInnVisitor") && a.get("lockInnVisitor").isJsonPrimitive() && a.get("lockInnVisitor").getAsBoolean())
                || AetherhavenConstants.QUEST_MERCHANT_STALL.equals(qid);
        if (lockInn && npcRef != null && npcRef.isValid()) {
            UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
            if (nu != null) {
                town.addInnLockedEntity(nu.getUuid());
            }
        }
        tm.updateTown(town);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw("Quest started: " + QuestCatalog.displayName(qid)));
        }
    }

    private static void completeQuest(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        String id = stringField(a, "id");
        if (id == null || id.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForPlayer(playerRef, store, tm);
        if (town == null) {
            return;
        }
        String qid = id.trim();
        applyQuestCompletion(world, plugin, town, tm, qid);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw("Quest completed: " + QuestCatalog.displayName(qid)));
        }
    }

    public static void applyQuestCompletion(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String qid
    ) {
        town.completeQuest(qid);
        if (AetherhavenConstants.QUEST_MERCHANT_STALL.equals(qid)) {
            town.getInnLockedEntityUuids().clear();
        }
        if (AetherhavenConstants.QUEST_BUILD_INN.equals(qid)) {
            InnkeeperSpawnService.trySpawnAfterInnQuestComplete(world, plugin, town);
        }
        tm.updateTown(town);
    }

    private static void abandonQuest(@Nonnull JsonObject a, @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        String id = stringField(a, "id");
        if (id == null || id.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForPlayer(playerRef, store, tm);
        if (town == null) {
            return;
        }
        String qid = id.trim();
        town.clearActiveQuest(qid);
        if (AetherhavenConstants.QUEST_MERCHANT_STALL.equals(qid)) {
            town.getInnLockedEntityUuids().clear();
        }
        tm.updateTown(town);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw("Quest abandoned: " + QuestCatalog.displayName(qid)));
        }
    }

    @Nullable
    private static TownRecord townForPlayer(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull TownManager tm
    ) {
        UUIDComponent uuidComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return null;
        }
        UUID owner = uuidComp.getUuid();
        return tm.findTownForOwnerInWorld(owner);
    }

    private static void giveItem(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        String item = stringField(a, "item");
        if (item == null || item.isBlank()) {
            item = stringField(a, "itemId");
        }
        if (item == null || item.isBlank()) {
            return;
        }
        int count = 1;
        if (a.has("count") && a.get("count").isJsonPrimitive()) {
            try {
                count = a.get("count").getAsInt();
            } catch (Exception ignored) {
                count = 1;
            }
        }
        count = Math.max(1, Math.min(count, 9999));
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        ItemStack stack = new ItemStack(item.trim(), count);
        player.giveItem(stack, playerRef, store);
    }

    @Nullable
    private static String getType(@Nonnull JsonObject a) {
        return stringField(a, "type");
    }

    @Nullable
    private static String stringField(@Nonnull JsonObject a, @Nonnull String key) {
        if (!a.has(key) || !a.get(key).isJsonPrimitive()) {
            return null;
        }
        return a.get(key).getAsString();
    }
}
