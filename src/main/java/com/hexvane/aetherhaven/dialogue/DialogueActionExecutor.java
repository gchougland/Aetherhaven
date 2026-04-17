package com.hexvane.aetherhaven.dialogue;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.QuestLifecycleEffects;
import com.hexvane.aetherhaven.quest.QuestRewardService;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
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
import com.hypixel.hytale.server.npc.entities.NPCEntity;
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
            case "open_blacksmith_repair" -> out.setOpenBlacksmithRepairAfterClose(true);
            case "open_geode_ui" -> out.setOpenGeodePageAfterClose(true);
            case "give_item" -> giveItem(a, playerRef, store);
            case "unlock_achievement" -> LOGGER.atInfo().log(
                "[Dialogue stub] unlock_achievement id=%s",
                stringField(a, "id")
            );
            case "start_quest" -> startQuest(a, playerRef, store, npcRef);
            case "complete_quest" -> completeQuest(a, playerRef, store, npcRef);
            case "abandon_quest" -> abandonQuest(a, playerRef, store);
            case "reputation_reward_grant" -> reputationRewardGrant(a, playerRef, store, npcRef);
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
        TownRecord town = townForDialogue(playerRef, store, tm, npcRef);
        if (town == null) {
            return;
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null || !town.playerHasQuestPermission(pu.getUuid())) {
            return;
        }
        String qid = id.trim();
        town.addActiveQuest(qid);
        QuestCatalog quests = plugin.getQuestCatalog();
        QuestDefinition qdef = quests.get(qid);
        UUID npcUuid = npcUuidFromRef(store, npcRef);
        if (qdef != null) {
            town.initQuestObjectiveProgress(qid, qdef.trackableObjectiveIds());
            QuestLifecycleEffects.runOnStart(world, plugin, town, tm, qdef, npcUuid);
        }
        if (a.has("lockInnVisitor") && a.get("lockInnVisitor").isJsonPrimitive() && a.get("lockInnVisitor").getAsBoolean()
            && npcUuid != null) {
            town.addInnLockedEntity(npcUuid);
        }
        tm.updateTown(town);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw("Quest started: " + quests.displayName(qid)));
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
        TownRecord town = townForDialogue(playerRef, store, tm, npcRef);
        if (town == null) {
            return;
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null || !town.playerHasQuestPermission(pu.getUuid())) {
            return;
        }
        String qid = id.trim();
        UUID npcUuid = npcUuidFromRef(store, npcRef);
        applyQuestCompletion(world, plugin, town, tm, qid, playerRef, npcUuid, store);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw("Quest completed: " + plugin.getQuestCatalog().displayName(qid)));
        }
    }

    public static void applyQuestCompletion(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String qid
    ) {
        applyQuestCompletion(world, plugin, town, tm, qid, null, null, null);
    }

    public static void applyQuestCompletion(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String qid,
        @Nullable Ref<EntityStore> rewardPlayerRef,
        @Nullable UUID beneficiaryNpcUuid,
        @Nullable Store<EntityStore> store
    ) {
        town.completeQuest(qid);
        QuestDefinition def = plugin.getQuestCatalog().get(qid);
        if (def != null) {
            QuestLifecycleEffects.runOnComplete(world, plugin, town, tm, def, null);
            if (rewardPlayerRef != null && store != null) {
                QuestRewardService.grantNonReputationRewards(def, rewardPlayerRef, store);
            }
        }
        tm.updateTown(town);
        if (rewardPlayerRef != null && beneficiaryNpcUuid != null && store != null) {
            UUIDComponent pu = store.getComponent(rewardPlayerRef, UUIDComponent.getComponentType());
            Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(beneficiaryNpcUuid);
            NPCEntity npc = npcRef != null ? store.getComponent(npcRef, NPCEntity.getComponentType()) : null;
            if (pu != null && npc != null && npc.getRoleName() != null) {
                VillagerReputationService.addQuestReputation(
                    world,
                    town,
                    tm,
                    pu.getUuid(),
                    beneficiaryNpcUuid,
                    npc.getRoleName(),
                    qid
                );
            }
        }
    }

    @Nullable
    private static UUID npcUuidFromRef(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        return nu != null ? nu.getUuid() : null;
    }

    private static void reputationRewardGrant(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        String rewardId = stringField(a, "rewardId");
        if (rewardId == null || rewardId.isBlank() || npcRef == null || !npcRef.isValid()) {
            return;
        }
        ReputationRewardCatalog.ReputationRewardDefinition def = ReputationRewardCatalog.byId(rewardId.trim());
        if (def == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, tm, npcRef);
        if (town == null) {
            return;
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (pu == null || nu == null || !town.playerHasQuestPermission(pu.getUuid())) {
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (!VillagerReputationService.claimPendingReward(town, tm, pu.getUuid(), nu.getUuid(), def.rewardId())) {
            return;
        }
        String learnId = def.learnRecipeItemId();
        if (learnId != null && !learnId.isBlank()) {
            CraftingPlugin.learnRecipe(playerRef, learnId.trim(), store);
            return;
        }
        int count = Math.max(1, Math.min(def.itemCount(), 9999));
        ItemStack stack = new ItemStack(def.itemId().trim(), count);
        player.giveItem(stack, playerRef, store);
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
        UUIDComponent puAb = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (puAb == null) {
            return;
        }
        TownRecord town = tm.findTownForPlayerInWorld(puAb.getUuid());
        if (town == null || !town.playerHasQuestPermission(puAb.getUuid())) {
            return;
        }
        String qid = id.trim();
        town.clearActiveQuest(qid);
        QuestDefinition def = plugin.getQuestCatalog().get(qid);
        if (def != null) {
            QuestLifecycleEffects.runOnAbandon(world, plugin, town, tm, def, null);
        }
        tm.updateTown(town);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw("Quest abandoned: " + plugin.getQuestCatalog().displayName(qid)));
        }
    }

    @Nullable
    private static TownRecord townForDialogue(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm,
        @Nullable Ref<EntityStore> npcRef
    ) {
        UUIDComponent uuidComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return null;
        }
        UUID playerUuid = uuidComp.getUuid();
        if (npcRef != null && npcRef.isValid()) {
            TownVillagerBinding b = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            if (b != null) {
                TownRecord t = tm.getTown(b.getTownId());
                if (t != null && t.hasMemberOrOwner(playerUuid)) {
                    return t;
                }
                return null;
            }
        }
        return tm.findTownForPlayerInWorld(playerUuid);
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
