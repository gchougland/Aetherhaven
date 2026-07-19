package com.hexvane.aetherhaven.dialogue;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.bard.BardEnvironmentMusic;
import com.hexvane.aetherhaven.bard.BardMusicProximityState;
import com.hexvane.aetherhaven.bard.BardPerformanceComponent;
import com.hexvane.aetherhaven.bard.BardPerformanceService;
import com.hexvane.aetherhaven.rescue.RescueVillagerDespawnEffects;
import com.hexvane.aetherhaven.rescue.RescueVillagerTriggers;
import com.hexvane.aetherhaven.guild.GuardHireService;
import com.hexvane.aetherhaven.guild.VillagerDeathHandlerSystem;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerSystem;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtService;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtState;
import com.hexvane.aetherhaven.gaiadraught.PlayerHealUtil;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalService;
import com.hexvane.aetherhaven.quest.QuestAvailability;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.QuestLifecycleEffects;
import com.hexvane.aetherhaven.quest.QuestPlotBlueprintOnStart;
import com.hexvane.aetherhaven.quest.QuestPlotTokenOnStart;
import com.hexvane.aetherhaven.quest.QuestProgressionService;
import com.hexvane.aetherhaven.quest.QuestRewardService;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.ui.UiMaterialLabels;
import com.hexvane.aetherhaven.ui.UiSoundEffects;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.villager.gift.VillagerGiftService;
import com.hexvane.aetherhaven.ui.WorldQuestBoardPage;
import com.hexvane.aetherhaven.worldnpc.WorldNpcBinding;
import com.hexvane.aetherhaven.worldnpc.WorldNpcPlacementRecord;
import com.hexvane.aetherhaven.worldnpc.WorldNpcReputationService;
import com.hexvane.aetherhaven.worldnpc.WorldQuestProgressionService;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueActionExecutor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String BANNER_SOUND_EVENT_ID = "SFX_Discovery_Z1_Short";

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
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null
            && plugin.getDialogueActionRegistry().dispatch(type, a, playerRef, store, out, npcRef)) {
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
            case "open_jewelry_appraisal" -> {
                out.setOpenJewelryAppraisalAfterClose(true);
                out.setJewelryAppraisalChargeGold(boolField(a, "chargeGold", true));
            }
            case "give_item" -> giveItem(a, playerRef, store);
            case "unlock_achievement" -> LOGGER.atInfo().log(
                "[Dialogue stub] unlock_achievement id=%s",
                stringField(a, "id")
            );
            case "set_main_hub_opener" -> setMainHubOpener(a, playerRef, store, npcRef);
            case "start_quest" -> startQuest(a, playerRef, store, npcRef);
            case "complete_quest" -> completeQuest(a, playerRef, store, npcRef);
            case "abandon_quest" -> abandonQuest(a, playerRef, store, npcRef);
            case "reputation_reward_grant" -> reputationRewardGrant(a, playerRef, store, npcRef);
            case "gift_villager" -> giftVillager(a, playerRef, store, out, npcRef);
            case "open_world_quest_board" -> openWorldQuestBoard(a, playerRef, store, out, npcRef);
            case "gaia_draught_refill" -> gaiaDraughtRefill(playerRef, store, npcRef);
            case "gaia_draught_upgrade_shard" -> gaiaDraughtUpgradeShard(playerRef, store, npcRef);
            case "gaia_draught_upgrade_catalyst" -> gaiaDraughtUpgradeCatalyst(playerRef, store, npcRef);
            case "priestess_gold_heal" -> priestessGoldHeal(playerRef, store, npcRef);
            case "hire_guild_adventurer" -> hireGuildAdventurer(playerRef, store, npcRef, out);
            case "despawn_npc" -> despawnNpc(a, playerRef, store, npcRef);
            case "play_bard_song" -> playBardSong(a, playerRef, store, npcRef);
            case "stop_bard_song" -> stopBardSong(playerRef, store, npcRef);
            case "start_follow_player" -> startFollowPlayer(playerRef, store, npcRef);
            case "stop_follow_player" -> stopFollowPlayer(store, npcRef);
            default -> LOGGER.atWarning().log("Unknown dialogue action type: %s", type);
        }
    }

    private static void setMainHubOpener(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        String langKey = stringField(a, "langKey");
        if (langKey == null || langKey.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        WorldNpcBinding worldBinding = worldBinding(store, npcRef);
        if (worldBinding != null && pu != null) {
            WorldNpcReputationService.setPendingMainHubBody(
                world,
                plugin,
                pu.getUuid(),
                worldBinding.getPlacementId(),
                langKey.trim()
            );
            return;
        }
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        if (town == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        UUID npcUuid = npcUuidFromRef(store, npcRef);
        if (pu == null || npcUuid == null) {
            return;
        }
        VillagerReputationService.setPendingMainHubBodyLangKey(town, tm, pu.getUuid(), npcUuid, langKey.trim());
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
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return;
        }
        String qid = id.trim();
        QuestCatalog quests = plugin.getQuestCatalog();
        QuestDefinition qdef = quests.get(qid);
        if (qdef != null && !QuestAvailability.isEnabled(qdef)) {
            return;
        }
        if (worldBinding(store, npcRef) != null || WorldQuestProgressionService.isWorldQuest(qdef)) {
            if (!WorldQuestProgressionService.startQuest(plugin, world, pu.getUuid(), qid)) {
                return;
            }
            PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                sendEventTitleBanner(
                    pr,
                    Message.translation("aetherhaven_misc.aetherhaven.banner.quest.started.secondary")
                        .param("name", quests.displayName(qid)),
                    Message.translation("aetherhaven_misc.aetherhaven.banner.quest.started.primary"),
                    true
                );
                playBannerSound(playerRef, store);
            }
            return;
        }
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        if (town == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        if (!town.playerCanAcceptQuests(pu.getUuid())) {
            return;
        }
        UUID npcUuid = npcUuidFromRef(store, npcRef);
        if (qdef != null && qdef.repeatOrDefault().isPerEntity()) {
            if (town.hasQuestActive(qid)) {
                return;
            }
            if (npcUuid != null && town.hasQuestCompletedForEntity(qid, npcUuid)) {
                return;
            }
            if (town.hasQuestCompleted(qid)) {
                town.clearGlobalQuestCompletion(qid);
            }
        } else if (town.hasQuestActive(qid)) {
            return;
        }
        town.addActiveQuest(qid);
        if (qdef != null) {
            boolean bindTarget = boolField(a, "bindTargetEntity", false) || qdef.assignByEntity();
            if (bindTarget && npcUuid != null) {
                town.setQuestTargetEntityUuid(qid, npcUuid);
            }
            QuestProgressionService.initialize(plugin, town, qid);
            QuestLifecycleEffects.runOnStart(world, plugin, town, tm, qdef, npcUuid);
            if (QuestPlotTokenOnStart.grantIfConfigured(plugin, qdef, playerRef, store)) {
                QuestProgressionService.markStartGrant(
                    plugin,
                    town,
                    qid,
                    QuestProgressionService.PLOT_TOKEN_RECEIVED
                );
            }
            if (QuestPlotBlueprintOnStart.grantIfConfigured(plugin, qdef, playerRef, store)) {
                QuestProgressionService.markStartGrant(
                    plugin,
                    town,
                    qid,
                    QuestProgressionService.PLOT_BLUEPRINT_RECEIVED
                );
            }
        }
        if (a.has("lockInnVisitor") && a.get("lockInnVisitor").isJsonPrimitive() && a.get("lockInnVisitor").getAsBoolean()
            && npcUuid != null) {
            town.addInnLockedEntity(npcUuid);
            InnPoolService.ensureVisitorListedInInnPool(town, npcUuid);
        }
        if (npcUuid != null && town.isInnVisitorLocked(npcUuid)) {
            InnPoolService.ensureVisitorListedInInnPool(town, npcUuid);
        }
        InnVisitorShopPromotion.tryPromoteReadyWorkplaces(world, plugin, town, tm);
        tm.updateTown(town);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            sendEventTitleBanner(
                pr,
                Message.translation("aetherhaven_misc.aetherhaven.banner.quest.started.secondary")
                    .param("name", quests.displayName(qid)),
                Message.translation("aetherhaven_misc.aetherhaven.banner.quest.started.primary"),
                true
            );
            playBannerSound(playerRef, store);
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
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return;
        }
        String qid = id.trim();
        QuestDefinition precheck = plugin.getQuestCatalog().get(qid);
        if (precheck != null && !QuestAvailability.isEnabled(precheck)) {
            return;
        }
        if (worldBinding(store, npcRef) != null || WorldQuestProgressionService.isWorldQuest(precheck)) {
            if (!WorldQuestProgressionService.advanceDialogueTurnIn(plugin, world, pu.getUuid(), qid)) {
                return;
            }
            if (!WorldQuestProgressionService.completeQuest(plugin, world, pu.getUuid(), qid)) {
                return;
            }
            PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                sendEventTitleBanner(
                    pr,
                    Message.translation("aetherhaven_misc.aetherhaven.banner.quest.completed.secondary")
                        .param("name", plugin.getQuestCatalog().displayName(qid)),
                    Message.translation("aetherhaven_misc.aetherhaven.banner.quest.completed.primary"),
                    true
                );
                playBannerSound(playerRef, store);
                pr.sendMessage(
                    Message.translation("aetherhaven_quests_portals.aetherhaven.quest.completed")
                        .param("name", plugin.getQuestCatalog().displayName(qid))
                );
            }
            return;
        }
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        if (town == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        if (!town.playerCanCompleteQuests(pu.getUuid())) {
            return;
        }
        if (!town.hasQuestActive(qid)) {
            return;
        }
        if (precheck != null && !QuestProgressionService.advanceDialogueTurnIn(plugin, town, qid)) {
            tm.updateTown(town);
            return;
        }
        UUID npcUuid = npcUuidFromRef(store, npcRef);
        applyQuestCompletion(world, plugin, town, tm, qid, playerRef, npcUuid, store);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            sendEventTitleBanner(
                pr,
                Message.translation("aetherhaven_misc.aetherhaven.banner.quest.completed.secondary")
                    .param("name", plugin.getQuestCatalog().displayName(qid)),
                Message.translation("aetherhaven_misc.aetherhaven.banner.quest.completed.primary"),
                true
            );
            playBannerSound(playerRef, store);
            pr.sendMessage(
                Message.translation("aetherhaven_quests_portals.aetherhaven.quest.completed")
                    .param("name", plugin.getQuestCatalog().displayName(qid))
            );
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
        QuestDefinition def = plugin.getQuestCatalog().get(qid);
        if (def != null && !QuestAvailability.isEnabled(def)) {
            return;
        }
        UUID guardPromoteUuid = null;
        UUID touristPromoteUuid = null;
        if (AetherhavenConstants.QUEST_HOUSE_GUARD.equals(qid.trim())) {
            guardPromoteUuid = town.getQuestTargetEntityUuid(qid);
            if (guardPromoteUuid == null && beneficiaryNpcUuid != null) {
                guardPromoteUuid = beneficiaryNpcUuid;
            }
        }
        if (AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK.equals(qid.trim())) {
            touristPromoteUuid = town.getQuestTargetEntityUuid(qid);
            if (touristPromoteUuid == null && beneficiaryNpcUuid != null) {
                touristPromoteUuid = beneficiaryNpcUuid;
            }
        }
        if (def != null && def.repeatOrDefault().isPerEntity()) {
            UUID target = town.getQuestTargetEntityUuid(qid);
            if (target == null) {
                target = beneficiaryNpcUuid;
            }
            if (target != null) {
                town.completeQuestForEntity(qid, target);
            } else {
                town.completeQuest(qid);
            }
        } else {
            town.completeQuest(qid);
        }
        if (def != null) {
            QuestLifecycleEffects.runOnComplete(world, plugin, town, tm, def, null);
            if (rewardPlayerRef != null && store != null) {
                QuestRewardService.grantNonReputationRewards(def, town, tm, rewardPlayerRef, store);
            }
        }
        if (rewardPlayerRef != null
            && store != null
            && qid.trim().equals(AetherhavenConstants.QUEST_PRIESTESS_GAIA_DRAUGHT)) {
            UUIDComponent pu = store.getComponent(rewardPlayerRef, UUIDComponent.getComponentType());
            if (pu != null) {
                GaiaDraughtService.unlockAndFill(town, pu.getUuid());
                tm.updateTown(town);
                // Grant on the next world tick so inventory sync cannot stall dialogue UI on the same frame.
                world.execute(() -> {
                    if (!rewardPlayerRef.isValid()) {
                        return;
                    }
                    Store<EntityStore> liveStore = rewardPlayerRef.getStore();
                    TownRecord liveTown = tm.findTownForPlayerInWorld(pu.getUuid());
                    if (liveTown != null) {
                        GaiaDraughtService.ensureDraughtStacksOrGrantFirst(rewardPlayerRef, liveStore, liveTown, pu.getUuid());
                        tm.updateTown(liveTown);
                    }
                });
            }
        }
        tm.updateTown(town);
        if (guardPromoteUuid != null && store != null) {
            VillagerDeathHandlerSystem.promoteGuardToCitizen(world, plugin, town, tm, guardPromoteUuid, store);
            tm.updateTown(town);
        }
        if (touristPromoteUuid != null) {
            TouristPortalTickService.promoteTouristToCitizen(town, tm, touristPromoteUuid);
        }
        if (store != null && isInnVisitorJobQuestForResidentPromotion(qid)) {
            InnPoolService.repairInnPoolForTown(world, plugin, town, tm, store, false);
        }
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

    /** Job quests whose completion should sync inn visitors to resident bindings when the matching plot is built. */
    private static boolean isInnVisitorJobQuestForResidentPromotion(@Nonnull String qid) {
        String q = qid.trim();
        return q.equals(AetherhavenConstants.QUEST_MERCHANT_STALL)
            || q.equals(AetherhavenConstants.QUEST_FARM_PLOT)
            || q.equals(AetherhavenConstants.QUEST_BLACKSMITH_SHOP)
            || q.equals(AetherhavenConstants.QUEST_GAIA_ALTAR)
            || q.equals(AetherhavenConstants.QUEST_MINERS_HUT)
            || q.equals(AetherhavenConstants.QUEST_LUMBERMILL)
            || q.equals(AetherhavenConstants.QUEST_BARN)
            || q.equals(AetherhavenConstants.QUEST_CRYSTAL_KEEPERS_SHOP)
            || q.equals(AetherhavenConstants.QUEST_PYROTECHNIC_SHOP)
            || q.equals(AetherhavenConstants.QUEST_FLORIST_SHOP)
            || q.equals(AetherhavenConstants.QUEST_CHEF_RESTAURANT)
            || q.equals(AetherhavenConstants.QUEST_BUILDERS_HUT)
            || q.equals(AetherhavenConstants.QUEST_BUILD_GUILD_HALL);
    }

    @Nullable
    private static UUID npcUuidFromRef(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        return nu != null ? nu.getUuid() : null;
    }

    private static void giftVillager(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        WorldNpcBinding worldBinding = worldBinding(store, npcRef);
        if (worldBinding != null) {
            giftWorldNpc(a, playerRef, store, out, worldBinding, plugin, world);
            return;
        }
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        if (town == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        VillagerGiftService.GiftApplyResult res = VillagerGiftService.applyGiftFromDialogue(
            a, playerRef, store, npcRef, plugin, tm, town
        );
        if (res.success() && res.gotoNodeId() != null) {
            out.setGotoNodeId(res.gotoNodeId());
            return;
        }
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null || res.failReason() == null) {
            if (pr != null) {
                pr.sendMessage(Message.translation("aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.fail.generic"));
            }
            return;
        }
        String msgKey = giftFailKey(res.failReason());
        pr.sendMessage(Message.translation(msgKey));
    }

    private static void giftWorldNpc(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nonnull WorldNpcBinding worldBinding,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world
    ) {
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pu == null || player == null) {
            return;
        }
        var hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        ItemStack hand = hotbar != null ? hotbar.getActiveItem() : null;
        if (ItemStack.isEmpty(hand)) {
            if (pr != null) {
                pr.sendMessage(Message.translation("aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.fail.emptyHand"));
            }
            return;
        }
        String itemId = hand.getItemId();
        var def = plugin.getVillagerDefinitionCatalog().byNpcRoleId(worldBinding.getNpcRoleId());
        int delta = 5;
        String gotoNode = stringField(a, "gotoLike");
        if (def != null) {
            if (def.getGiftLoves().contains(itemId)) {
                delta = 15;
                gotoNode = stringField(a, "gotoLove");
            } else if (def.getGiftLikes().contains(itemId)) {
                delta = 10;
                gotoNode = stringField(a, "gotoLike");
            } else if (def.getGiftDislikes().contains(itemId)) {
                delta = -5;
                gotoNode = stringField(a, "gotoDislike");
            }
        }
        if (!WorldNpcReputationService.tryGift(
            world,
            plugin,
            store,
            pu.getUuid(),
            worldBinding.getPlacementId(),
            delta
        )) {
            if (pr != null) {
                pr.sendMessage(Message.translation("aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.fail.weeklyLimit"));
            }
            return;
        }
        if (hotbar != null) {
            byte slot = hotbar.getActiveSlot();
            if (slot >= 0) {
                var container = hotbar.getInventory();
                int q = hand.getQuantity();
                ItemStack replacement =
                    q <= 1
                        ? ItemStack.EMPTY
                        : (hand.withQuantity(q - 1) != null ? hand.withQuantity(q - 1) : ItemStack.EMPTY);
                container.replaceItemStackInSlot(slot, hand, replacement);
            }
        }
        if (gotoNode != null && !gotoNode.isBlank()) {
            out.setGotoNodeId(gotoNode.trim());
        }
    }

    @Nonnull
    private static String giftFailKey(@Nonnull VillagerGiftService.GiftEligibility.Reason reason) {
        return switch (reason) {
            case VISITOR -> "aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.fail.visitor";
            case EMPTY_HAND -> "aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.fail.emptyHand";
            case DAILY_LIMIT -> "aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.fail.dailyLimit";
            case WEEKLY_LIMIT -> "aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.fail.weeklyLimit";
            case NOT_BEFRIENDABLE -> "aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.fail.generic";
            case NO_CONTEXT, NO_PLAYER -> "aetherhaven_dialogue_gift.aetherhaven.dialogue.gift.fail.generic";
        };
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
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (pu == null || player == null) {
            return;
        }
        WorldNpcBinding worldBinding = worldBinding(store, npcRef);
        if (worldBinding != null) {
            if (!WorldNpcReputationService.claimPendingReward(
                world,
                plugin,
                pu.getUuid(),
                worldBinding.getPlacementId(),
                def.rewardId()
            )) {
                return;
            }
            grantReputationRewardItems(def, playerRef, store, player);
            return;
        }
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        if (town == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (nu == null || !town.playerCanCompleteQuests(pu.getUuid())) {
            return;
        }
        if (!com.hexvane.aetherhaven.villager.VillagerBefriendableResolver.isBefriendable(store, npcRef, plugin)) {
            return;
        }
        if (!VillagerReputationService.claimPendingReward(town, tm, pu.getUuid(), nu.getUuid(), def.rewardId())) {
            return;
        }
        grantReputationRewardItems(def, playerRef, store, player);
    }

    private static void grantReputationRewardItems(
        @Nonnull ReputationRewardCatalog.ReputationRewardDefinition def,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player
    ) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        String learnId = def.learnRecipeItemId();
        if (learnId != null && !learnId.isBlank()) {
            String learnedItemId = learnId.trim();
            String learnedLabel = resolveItemLabel(pr, learnedItemId);
            CraftingPlugin.learnRecipe(playerRef, learnedItemId, store);
            sendEventTitleBanner(
                pr,
                Message.translation("aetherhaven_misc.aetherhaven.banner.reputation.unlock.recipe")
                    .param("item", learnedLabel),
                Message.translation("aetherhaven_misc.aetherhaven.banner.reputation.unlock.primary"),
                false
            );
            playBannerSound(playerRef, store);
            return;
        }
        String itemId = def.itemId() != null ? def.itemId().trim() : "";
        if (itemId.isBlank() || def.itemCount() <= 0) {
            return;
        }
        int count = Math.max(1, Math.min(def.itemCount(), 9999));
        ItemStack stack = new ItemStack(itemId, count);
        player.giveItem(stack, playerRef, store);
        sendEventTitleBanner(
            pr,
            Message.translation("aetherhaven_misc.aetherhaven.banner.reputation.unlock.item")
                .param("item", resolveItemLabel(pr, itemId))
                .param("count", count),
            Message.translation("aetherhaven_misc.aetherhaven.banner.reputation.unlock.primary"),
            false
        );
        playBannerSound(playerRef, store);
    }

    private static void abandonQuest(
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
        UUIDComponent puAb = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (puAb == null) {
            return;
        }
        String qid = id.trim();
        QuestDefinition def = plugin.getQuestCatalog().get(qid);
        if (worldBinding(store, npcRef) != null || WorldQuestProgressionService.isWorldQuest(def)) {
            if (!WorldQuestProgressionService.abandonQuest(plugin, world, puAb.getUuid(), qid)) {
                return;
            }
            PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(
                    Message.translation("aetherhaven_quests_portals.aetherhaven.quest.abandoned")
                        .param("name", plugin.getQuestCatalog().displayName(qid))
                );
            }
            return;
        }
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = AetherhavenWorldRegistries.findTownForPlayerAcrossWorlds(puAb.getUuid(), localTm);
        if (town == null || !town.playerCanAbandonQuests(puAb.getUuid())) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        town.clearActiveQuest(qid);
        if (def != null) {
            QuestLifecycleEffects.runOnAbandon(world, plugin, town, tm, def, null);
        }
        tm.updateTown(town);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(
                Message.translation("aetherhaven_quests_portals.aetherhaven.quest.abandoned")
                    .param("name", plugin.getQuestCatalog().displayName(qid))
            );
        }
    }

    private static void openWorldQuestBoard(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        String profileId = stringField(a, "profileId");
        if (profileId == null || profileId.isBlank()) {
            WorldNpcBinding binding = worldBinding(store, npcRef);
            if (binding != null) {
                WorldNpcPlacementRecord placement =
                    AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(
                            store.getExternalData().getWorld(),
                            plugin
                        )
                        .findPlacement(binding.getPlacementId());
                if (placement != null) {
                    profileId = placement.boardProfileIdOrEmpty();
                }
            }
        }
        if (profileId == null || profileId.isBlank()) {
            profileId = "hub_default";
        }
        String resolvedProfile = profileId.trim();
        out.setCloseDialogue(true);
        out.setAfterClose(() -> {
            Ref<EntityStore> pref = playerRef;
            if (!pref.isValid()) {
                return;
            }
            Store<EntityStore> st = pref.getStore();
            Player player = st.getComponent(pref, Player.getComponentType());
            PlayerRef pr = st.getComponent(pref, PlayerRef.getComponentType());
            if (player == null || pr == null) {
                return;
            }
            player.getPageManager().openCustomPage(pref, st, new WorldQuestBoardPage(pr, resolvedProfile));
        });
    }

    @Nullable
    private static WorldNpcBinding worldBinding(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        return store.getComponent(npcRef, WorldNpcBinding.getComponentType());
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
                TownRecord t = AetherhavenWorldRegistries.getTownAcrossWorlds(b.getTownId(), tm);
                if (t != null && t.hasMemberOrOwner(playerUuid)) {
                    return t;
                }
                return null;
            }
        }
        return AetherhavenWorldRegistries.findTownForPlayerAcrossWorlds(playerUuid, tm);
    }

    /** Prefer the town's home-world manager so updates persist when talking in a portal instance. */
    @Nonnull
    private static TownManager owningTownManager(@Nonnull TownRecord town, @Nonnull TownManager fallback) {
        TownManager owning = AetherhavenWorldRegistries.townManagerForTown(town);
        return owning != null ? owning : fallback;
    }

    private static void gaiaDraughtRefill(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        GaiaDraughtState s = GaiaDraughtService.getOrCreate(town, pu.getUuid());
        if (!s.isUnlocked()) {
            return;
        }
        GaiaDraughtService.refillToCapacity(town, pu.getUuid());
        tm.updateTown(town);
        GaiaDraughtService.syncDraughtStacksInInventory(playerRef, store, s);
    }

    private static void gaiaDraughtUpgradeShard(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        GaiaDraughtState s = GaiaDraughtService.getOrCreate(town, pu.getUuid());
        if (!s.canApplyShardUpgrade()) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null || !GaiaDraughtService.hasItem(inv, AetherhavenConstants.ITEM_SHARD_OF_GAIA, 1)) {
            return;
        }
        boolean allowTreasury = town.playerCanSpendTreasuryGold(pu.getUuid());
        long cost = AetherhavenConstants.gaiaDraughtShardUpgradeGoldCost(s.getShardUpgradeCount());
        if (!GoldCoinPayment.canAfford(town, inv, cost, allowTreasury)) {
            return;
        }
        if (!GaiaDraughtService.removeOneItemFromInventory(playerRef, store, AetherhavenConstants.ITEM_SHARD_OF_GAIA)) {
            return;
        }
        if (!GoldCoinPayment.trySpend(town, inv, cost, allowTreasury)) {
            return;
        }
        if (!GaiaDraughtService.tryApplyShardCapacityUpgrade(s)) {
            return;
        }
        tm.updateTown(town);
        GaiaDraughtService.syncDraughtStacksInInventory(playerRef, store, s);
        UiSoundEffects.play2dUi(playerRef, store, AetherhavenConstants.SFX_WORKBENCH_UPGRADE_COMPLETE);
    }

    private static void gaiaDraughtUpgradeCatalyst(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        GaiaDraughtState s = GaiaDraughtService.getOrCreate(town, pu.getUuid());
        if (!s.canApplyCatalystUpgrade()) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null || !GaiaDraughtService.hasItem(inv, AetherhavenConstants.ITEM_VERDANT_CATALYST, 1)) {
            return;
        }
        boolean allowTreasury = town.playerCanSpendTreasuryGold(pu.getUuid());
        long cost = AetherhavenConstants.gaiaDraughtCatalystUpgradeGoldCost(s.getCatalystUpgradeCount());
        if (!GoldCoinPayment.canAfford(town, inv, cost, allowTreasury)) {
            return;
        }
        if (!GaiaDraughtService.removeOneItemFromInventory(playerRef, store, AetherhavenConstants.ITEM_VERDANT_CATALYST)) {
            return;
        }
        if (!GoldCoinPayment.trySpend(town, inv, cost, allowTreasury)) {
            return;
        }
        if (!GaiaDraughtService.tryApplyCatalystHealTierUpgrade(s)) {
            return;
        }
        tm.updateTown(town);
        GaiaDraughtService.syncDraughtStacksInInventory(playerRef, store, s);
        UiSoundEffects.play2dUi(playerRef, store, AetherhavenConstants.SFX_WORKBENCH_UPGRADE_COMPLETE);
    }

    private static void priestessGoldHeal(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        float missing = PlayerHealUtil.missingHealth(playerRef, store);
        if (missing <= 0f) {
            return;
        }
        int per = Math.max(1, AetherhavenConstants.PRIESTESS_HEAL_HEALTH_PER_GOLD_COIN);
        long cost = (long) Math.ceil(missing / (float) per);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        boolean allowTreasury = town.playerCanSpendTreasuryGold(pu.getUuid());
        if (!GoldCoinPayment.canAfford(town, inv, cost, allowTreasury)) {
            return;
        }
        if (!GoldCoinPayment.trySpend(town, inv, cost, allowTreasury)) {
            return;
        }
        PlayerHealUtil.healToFull(playerRef, store);
        tm.updateTown(town);
        UiSoundEffects.play2dUi(playerRef, store, AetherhavenConstants.SFX_PRIESTESS_HEAL);
    }

    private static void despawnNpc(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        UUID npcUuid = npcUuidFromRef(store, npcRef);
        if (npcUuid == null) {
            return;
        }
        String particle = blankToNull(stringField(a, "particleSystemId"));
        String sound = blankToNull(stringField(a, "soundEventId"));
        if (particle == null && sound == null && npcRef != null && npcRef.isValid()) {
            TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            if (binding != null) {
                var trigger = RescueVillagerTriggers.byBindingKind(binding.getKind());
                if (trigger != null) {
                    particle = trigger.vanishParticleSystemId();
                    sound = trigger.vanishSoundEventId();
                }
            }
        }
        if (particle == null) {
            particle = AetherhavenConstants.CRYSTAL_KEEPER_RESCUE_VANISH_PARTICLE_SYSTEM_ID;
        }
        if (sound == null) {
            sound = AetherhavenConstants.CRYSTAL_KEEPER_RESCUE_VANISH_SOUND_EVENT_ID;
        }
        if (npcRef != null && npcRef.isValid()) {
            RescueVillagerDespawnEffects.playAtNpc(npcRef, store, particle, sound);
        }
        World world = store.getExternalData().getWorld();
        PendingEntityRemovalService.schedule(world, npcUuid);
    }

    private static void playBardSong(
        @Nonnull JsonObject a,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }
        String songId = stringField(a, "songId");
        if (songId == null || songId.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        BardPerformanceService.startSong(store, null, npcRef, plugin, songId.trim());
        BardPerformanceComponent perf =
            store.getComponent(npcRef, BardPerformanceComponent.getComponentType());
        if (perf == null || perf.getMusicContainerIndex() == 0) {
            return;
        }
        ForcedMusicTracker tracker = store.getComponent(playerRef, ForcedMusicTracker.getComponentType());
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        UUIDComponent playerUuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (tracker == null || playerRefComponent == null || playerUuid == null) {
            return;
        }
        BardEnvironmentMusic.setForcedMusic(
            playerRef,
            null,
            store,
            playerRefComponent,
            tracker,
            perf.getMusicContainerIndex()
        );
        store.getResource(BardMusicProximityState.getResourceType())
            .setActive(playerUuid.getUuid(), perf.getMusicContainerIndex());
    }

    private static void stopBardSong(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }
        BardPerformanceService.stopOnStore(store, npcRef);
    }

    private static void startFollowPlayer(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }
        UUIDComponent playerUuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (playerUuid == null) {
            return;
        }
        VillagerFollowPlayerSystem.startFollow(npcRef, store, playerUuid.getUuid());
    }

    private static void stopFollowPlayer(
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }
        VillagerFollowPlayerSystem.stopFollow(npcRef, store, true);
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

    private static boolean boolField(@Nonnull JsonObject a, @Nonnull String key, boolean def) {
        if (!a.has(key) || !a.get(key).isJsonPrimitive()) {
            return def;
        }
        try {
            return a.get(key).getAsBoolean();
        } catch (Exception e) {
            return def;
        }
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

    @Nullable
    private static String blankToNull(@Nullable String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void sendEventTitleBanner(
        @Nullable PlayerRef playerRef,
        @Nonnull Message primary,
        @Nonnull Message secondary,
        boolean isMajor
    ) {
        if (playerRef == null) {
            return;
        }
        EventTitleUtil.showEventTitleToPlayer(
            playerRef,
            primary,
            secondary,
            isMajor,
            null,
            4.0F,
            0.7F,
            0.9F
        );
    }

    @Nonnull
    private static String resolveItemLabel(@Nullable PlayerRef playerRef, @Nonnull String itemId) {
        String lang = playerRef != null && playerRef.getLanguage() != null ? playerRef.getLanguage() : "en-US";
        return UiMaterialLabels.itemLabelForUi(lang, itemId);
    }

    private static void hireGuildAdventurer(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull DialogueActionBatchResult out
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager localTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townForDialogue(playerRef, store, localTm, npcRef);
        if (town == null) {
            return;
        }
        TownManager tm = owningTownManager(town, localTm);
        if (GuardHireService.tryHire(world, plugin, town, tm, playerRef, npcRef, store)) {
            out.setCloseDialogue(true);
        } else {
            out.setGotoNodeId("hire_confirm");
        }
    }

    private static void playBannerSound(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        int index = SoundEvent.getAssetMap().getIndex(BANNER_SOUND_EVENT_ID);
        if (index == Integer.MIN_VALUE) {
            return;
        }
        SoundUtil.playSoundEvent2d(playerRef, index, SoundCategory.UI, store);
    }
}
