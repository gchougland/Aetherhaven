package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.guild.GuardHireService;
import com.hexvane.aetherhaven.guild.GuildHallAdventurerPoolService;
import com.hexvane.aetherhaven.ui.GuardRoleLabels;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtMetadata;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtState;
import com.hexvane.aetherhaven.gaiadraught.PlayerHealUtil;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.QuestProgressionService;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.questboard.TownRankCapacity;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.gift.VillagerGiftService;
import com.hexvane.aetherhaven.worldnpc.WorldNpcBinding;
import com.hexvane.aetherhaven.worldnpc.WorldNpcPlayerProgress;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves dialogue conditions from the player's town in this world. */
public final class AetherhavenDialogueWorldView implements DialogueWorldView {
    private final World world;
    private final AetherhavenPlugin plugin;
    private final DialogueWorldView base = new DialogueWorldView.DefaultDialogueWorldView();
    @Nullable
    private final Ref<EntityStore> contextNpcRef;

    public AetherhavenDialogueWorldView(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        this(world, plugin, null);
    }

    /**
     * When opening dialogue with an NPC, pass their entity ref so town-scoped conditions use that NPC's town
     * when the player is a resident (owner or member), not another town the player may own elsewhere.
     */
    public AetherhavenDialogueWorldView(
        @Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nullable Ref<EntityStore> contextNpcRef
    ) {
        this.world = world;
        this.plugin = plugin;
        this.contextNpcRef = contextNpcRef;
    }

    @Nullable
    private TownRecord townFor(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        UUIDComponent uuidComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return null;
        }
        UUID playerUuid = uuidComp.getUuid();
        var tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        if (contextNpcRef != null && contextNpcRef.isValid()) {
            TownVillagerBinding b = store.getComponent(contextNpcRef, TownVillagerBinding.getComponentType());
            if (b != null) {
                TownRecord nt = AetherhavenWorldRegistries.getTownAcrossWorlds(b.getTownId(), tm);
                if (nt != null && nt.hasMemberOrOwner(playerUuid)) {
                    return nt;
                }
                return null;
            }
        }
        return TownPlayerResolution.resolveActiveTown(world, store, playerRef, tm);
    }

    @Override
    public boolean hasAchievement(@Nonnull String id) {
        return base.hasAchievement(id);
    }

    @Override
    public boolean getFlag(@Nonnull String id) {
        return base.getFlag(id);
    }

    @Override
    public boolean hasItem(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String itemId, int minCount
    ) {
        return base.hasItem(playerRef, store, itemId, minCount);
    }

    @Override
    public boolean isVillagerInTown(@Nonnull String villagerId) {
        return base.isVillagerInTown(villagerId);
    }

    @Override
    public boolean townQuestActive(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        // Hub speakers only match world-category progress for town_quest_* conditions.
        if (talkingToWorldNpc(store)) {
            return worldQuestActive(playerRef, store, questId);
        }
        if (worldQuestActive(playerRef, store, questId)) {
            return true;
        }
        TownRecord t = townFor(playerRef, store);
        return t != null && t.hasQuestActive(questId.trim());
    }

    @Override
    public boolean townQuestCompleted(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        if (talkingToWorldNpc(store)) {
            return worldQuestCompleted(playerRef, store, questId);
        }
        if (worldQuestCompleted(playerRef, store, questId)) {
            return true;
        }
        TownRecord t = townFor(playerRef, store);
        return t != null && t.hasQuestCompleted(questId.trim());
    }

    @Override
    public boolean worldQuestActive(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        WorldNpcPlayerProgress progress = worldProgress(playerRef, store);
        return progress != null && progress.hasQuestActive(questId.trim());
    }

    @Override
    public boolean worldQuestCompleted(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        WorldNpcPlayerProgress progress = worldProgress(playerRef, store);
        return progress != null && progress.hasQuestCompleted(questId.trim());
    }

    @Nullable
    private WorldNpcPlayerProgress worldProgress(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return null;
        }
        return AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin)
            .getOrCreatePlayerProgress(pu.getUuid());
    }

    private boolean talkingToWorldNpc(@Nonnull Store<EntityStore> store) {
        return contextNpcRef != null
            && contextNpcRef.isValid()
            && store.getComponent(contextNpcRef, WorldNpcBinding.getComponentType()) != null;
    }

    @Override
    public boolean townHasCompletePlot(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String constructionId
    ) {
        TownRecord t = townFor(playerRef, store);
        return t != null && t.hasCompletePlotWithConstruction(plugin.getConstructionCatalog(), constructionId.trim());
    }

    @Override
    public boolean aetherhavenHasTown(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return townFor(playerRef, store) != null;
    }

    @Override
    public boolean aetherhavenPlayerCanAcceptQuests(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store
    ) {
        if (talkingToWorldNpc(store)) {
            return true;
        }
        TownRecord t = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        return t != null && pu != null && t.playerCanAcceptQuests(pu.getUuid());
    }

    @Override
    public boolean townInnVisitorPoolEmpty(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        TownRecord t = townFor(playerRef, store);
        if (t == null) {
            return false;
        }
        if (!t.hasQuestCompleted(AetherhavenConstants.QUEST_BUILD_INN)) {
            return false;
        }
        if (!t.hasCompletePlotWithConstruction(plugin.getConstructionCatalog(), AetherhavenConstants.CONSTRUCTION_PLOT_INN)) {
            return false;
        }
        return t.getInnPoolNpcIds().isEmpty();
    }

    @Override
    public boolean innPoolHasNpcRole(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String npcRoleId
    ) {
        String want = npcRoleId.trim();
        if (want.isEmpty()) {
            return false;
        }
        TownRecord t = townFor(playerRef, store);
        if (t == null) {
            return false;
        }
        var es = world.getEntityStore();
        if (es == null) {
            return false;
        }
        Store<EntityStore> entityStore = es.getStore();
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) {
            return false;
        }
        for (String sid : t.getInnPoolNpcIds()) {
            try {
                UUID u = UUID.fromString(sid.trim());
                Ref<EntityStore> ref = entityStore.getExternalData().getRefFromUUID(u);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                NPCEntity npc = entityStore.getComponent(ref, npcType);
                if (npc != null && want.equals(npc.getRoleName())) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @Override
    public boolean playerHoldsItemInActiveHotbar(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String itemId, int minCount
    ) {
        if (store.getComponent(playerRef, Player.getComponentType()) == null) {
            return false;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(
            playerRef, InventoryComponent.Hotbar.getComponentType()
        );
        ItemStack s = hotbar != null ? hotbar.getActiveItem() : null;
        if (ItemStack.isEmpty(s)) {
            return false;
        }
        int need = Math.max(1, minCount);
        return itemId.trim().equals(s.getItemId()) && s.getQuantity() >= need;
    }

    @Override
    public boolean playerHoldsAnyItemInActiveHotbar(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store
    ) {
        if (store.getComponent(playerRef, Player.getComponentType()) == null) {
            return false;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(
            playerRef, InventoryComponent.Hotbar.getComponentType()
        );
        return hotbar != null && !ItemStack.isEmpty(hotbar.getActiveItem());
    }

    @Override
    public boolean villagerGiftAllowed(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        if (store.getComponent(playerRef, UUIDComponent.getComponentType()) == null) {
            return false;
        }
        TownRecord town = townForVillagerGift(playerRef, store, npcRef);
        return VillagerGiftService.canGift(playerRef, store, town, npcRef).allowed;
    }

    @Override
    @Nullable
    public Message villagerGiftBlockMessage(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        if (store.getComponent(playerRef, UUIDComponent.getComponentType()) == null) {
            return null;
        }
        TownRecord town = townForVillagerGift(playerRef, store, npcRef);
        return VillagerGiftService.giftBlockMessageForDialogue(playerRef, store, town, npcRef);
    }

    @Nullable
    private TownRecord townForVillagerGift(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        var tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return null;
        }
        if (npcRef != null && npcRef.isValid()) {
            TownVillagerBinding b = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            if (b != null) {
                TownRecord town = AetherhavenWorldRegistries.getTownAcrossWorlds(b.getTownId(), tm);
                if (town == null || !town.hasMemberOrOwner(pu.getUuid())) {
                    return null;
                }
                return town;
            }
            return AetherhavenWorldRegistries.findTownForPlayerAcrossWorlds(pu.getUuid(), tm);
        }
        return AetherhavenWorldRegistries.findTownForPlayerAcrossWorlds(pu.getUuid(), tm);
    }

    @Override
    public boolean townNpcHomeResidentOnHousePlot(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        UUIDComponent uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return false;
        }
        UUID npcUuid = uuidComp.getUuid();
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return false;
        }
        UUID playerUuid = pu.getUuid();
        var tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord t;
        TownVillagerBinding nb = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (nb != null) {
            t = AetherhavenWorldRegistries.getTownAcrossWorlds(nb.getTownId(), tm);
            if (t != null && !t.hasMemberOrOwner(playerUuid)) {
                t = null;
            }
        } else {
            t = townFor(playerRef, store);
        }
        return t != null && t.isNpcHomeResidentOnHousePlot(npcUuid, plugin.getConstructionCatalog());
    }

    @Override
    public boolean goldCoinPaymentCanAfford(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef, long cost
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return false;
        }
        return GoldCoinPayment.canAfford(town, inv, cost, town.playerCanSpendTreasuryGold(pu.getUuid()));
    }

    @Override
    public boolean playerHealthBelowMax(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return PlayerHealUtil.missingHealth(playerRef, store) > 0f;
    }

    @Override
    public boolean gaiaDraughtUnlocked(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = townFor(playerRef, store);
        if (town == null) {
            return false;
        }
        if (townQuestCompleted(playerRef, store, AetherhavenConstants.QUEST_PRIESTESS_GAIA_DRAUGHT)) {
            return true;
        }
        if (town.sharesCraftRecipeItem(AetherhavenConstants.ITEM_GAIAS_DRAUGHT)) {
            return true;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        return GaiaDraughtMetadata.hasAnyDraught(inv);
    }

    @Override
    public boolean gaiaDraughtChargesBelowCapacity(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        if (townFor(playerRef, store) == null) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        return GaiaDraughtMetadata.selectServiceTarget(inv, GaiaDraughtMetadata.ServiceKind.REFILL) != null;
    }

    @Override
    public boolean gaiaDraughtCapacityBelowMax(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        if (townFor(playerRef, store) == null) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        return GaiaDraughtMetadata.selectServiceTarget(inv, GaiaDraughtMetadata.ServiceKind.SHARD_UPGRADE) != null;
    }

    @Override
    public boolean gaiaDraughtHealTierBelowMax(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        if (townFor(playerRef, store) == null) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        return GaiaDraughtMetadata.selectServiceTarget(inv, GaiaDraughtMetadata.ServiceKind.CATALYST_UPGRADE) != null;
    }

    @Override
    public boolean gaiaDraughtShardUpgradeGoldAffordable(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        GaiaDraughtMetadata.ServiceTarget target = GaiaDraughtMetadata.selectServiceTarget(
            inv,
            GaiaDraughtMetadata.ServiceKind.SHARD_UPGRADE
        );
        if (target == null) {
            return false;
        }
        GaiaDraughtState s = GaiaDraughtMetadata.readProgress(target.stack());
        if (!s.canApplyShardUpgrade()) {
            return false;
        }
        if (inv == null) {
            return false;
        }
        long cost = AetherhavenConstants.gaiaDraughtShardUpgradeGoldCost(s.getShardUpgradeCount());
        return GoldCoinPayment.canAfford(town, inv, cost, town.playerCanSpendTreasuryGold(pu.getUuid()));
    }

    @Override
    public boolean gaiaDraughtCatalystUpgradeGoldAffordable(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        GaiaDraughtMetadata.ServiceTarget target = GaiaDraughtMetadata.selectServiceTarget(
            inv,
            GaiaDraughtMetadata.ServiceKind.CATALYST_UPGRADE
        );
        if (target == null) {
            return false;
        }
        GaiaDraughtState s = GaiaDraughtMetadata.readProgress(target.stack());
        if (!s.canApplyCatalystUpgrade()) {
            return false;
        }
        if (inv == null) {
            return false;
        }
        long cost = AetherhavenConstants.gaiaDraughtCatalystUpgradeGoldCost(s.getCatalystUpgradeCount());
        return GoldCoinPayment.canAfford(town, inv, cost, town.playerCanSpendTreasuryGold(pu.getUuid()));
    }

    @Override
    public long nextGaiaDraughtShardUpgradeGoldCost(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        GaiaDraughtMetadata.ServiceTarget target = GaiaDraughtMetadata.selectServiceTarget(
            inv,
            GaiaDraughtMetadata.ServiceKind.SHARD_UPGRADE
        );
        if (target == null) {
            return AetherhavenConstants.gaiaDraughtShardUpgradeGoldCost(0);
        }
        GaiaDraughtState s = GaiaDraughtMetadata.readProgress(target.stack());
        s.ensureLegacyMigrated();
        return AetherhavenConstants.gaiaDraughtShardUpgradeGoldCost(s.getShardUpgradeCount());
    }

    @Override
    public long nextGaiaDraughtCatalystUpgradeGoldCost(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        GaiaDraughtMetadata.ServiceTarget target = GaiaDraughtMetadata.selectServiceTarget(
            inv,
            GaiaDraughtMetadata.ServiceKind.CATALYST_UPGRADE
        );
        if (target == null) {
            return AetherhavenConstants.gaiaDraughtCatalystUpgradeGoldCost(0);
        }
        GaiaDraughtState s = GaiaDraughtMetadata.readProgress(target.stack());
        s.ensureLegacyMigrated();
        return AetherhavenConstants.gaiaDraughtCatalystUpgradeGoldCost(s.getCatalystUpgradeCount());
    }

    @Override
    public boolean townQuestEntityKillsMet(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String questId,
        @Nullable String objectiveId
    ) {
        TownRecord town = townFor(playerRef, store);
        if (town == null || !town.hasQuestActive(questId.trim())) {
            return false;
        }
        QuestCatalog cat = plugin.getQuestCatalog();
        QuestDefinition def = cat.get(questId.trim());
        if (def == null) {
            return false;
        }
        String want = objectiveId != null ? objectiveId.trim() : "";
        boolean anyKillObjective = false;
        for (QuestObjective o : def.objectivesOrEmpty()) {
            if (o.kind() == null || !"entity_kills".equalsIgnoreCase(o.kind().trim())) {
                continue;
            }
            if (o.id() == null || o.id().isBlank()) {
                continue;
            }
            if (!want.isEmpty() && !want.equalsIgnoreCase(o.id().trim())) {
                continue;
            }
            anyKillObjective = true;
            int need = Math.max(1, o.killCount());
            if (town.getQuestKillCount(questId.trim(), o.id().trim()) < need) {
                return false;
            }
        }
        return anyKillObjective;
    }

    @Override
    public boolean priestessHealGoldAffordable(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        float missing = PlayerHealUtil.missingHealth(playerRef, store);
        if (missing <= 0f) {
            return false;
        }
        int per = Math.max(1, AetherhavenConstants.PRIESTESS_HEAL_HEALTH_PER_GOLD_COIN);
        long cost = (long) Math.ceil(missing / (float) per);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return false;
        }
        return GoldCoinPayment.canAfford(town, inv, cost, town.playerCanSpendTreasuryGold(pu.getUuid()));
    }

    @Override
    public boolean npcIsGuildHallAdventurer(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        TownRecord town = townFor(playerRef, store);
        return nu != null && town != null && GuildHallAdventurerPoolService.isGuildHallAdventurer(town, nu.getUuid());
    }

    @Override
    public boolean guardHireAffordable(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null || npcRef == null || !npcRef.isValid()) {
            return false;
        }
        String profileId = GuardHireService.equipmentProfileForNpc(plugin, npcRef, store);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        return profileId != null
            && inv != null
            && GuardHireService.canAfford(plugin, town, inv, pu.getUuid(), profileId)
            && TownRankCapacity.canHireGuard(town, plugin.getQuestBoardCatalog());
    }

    @Override
    public long guardHireGoldCost(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return 0L;
        }
        String profileId = GuardHireService.equipmentProfileForNpc(plugin, npcRef, store);
        return profileId != null ? GuardHireService.hireCost(plugin, profileId) : 0L;
    }

    @Override
    public int hiredGuardCount(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        TownRecord town = townFor(playerRef, store);
        return town != null ? town.getHiredGuardRecords().size() : 0;
    }

    @Override
    public int maxHiredGuards(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        TownRecord town = townFor(playerRef, store);
        if (town == null) {
            return 0;
        }
        return TownRankCapacity.maxHiredGuards(town, plugin.getQuestBoardCatalog());
    }

    @Override
    public boolean guardHireAtLimit(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        TownRecord town = townFor(playerRef, store);
        return town != null && !TownRankCapacity.canHireGuard(town, plugin.getQuestBoardCatalog());
    }

    @Override
    @Nonnull
    public String guardHireGuardTypeLangKey(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return GuardRoleLabels.guardTypeLangKey(AetherhavenConstants.NPC_GUARD_KNIGHT);
        }
        return GuardHireService.guardTypeLangKeyForNpc(plugin, npcRef, store);
    }

    @Override
    public boolean playerHasUnhousedHiredGuard(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        TownRecord town = townFor(playerRef, store);
        return town != null && GuardHireService.hasUnhousedHiredGuard(town, plugin);
    }

    @Override
    public boolean guardHouseQuestTargetHoused(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return questTargetEntityHoused(playerRef, store, AetherhavenConstants.QUEST_HOUSE_GUARD);
    }

    @Override
    public boolean npcIsUnhousedHiredGuard(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        TownRecord town = townFor(playerRef, store);
        return nu != null && town != null && GuardHireService.isUnhousedHiredGuard(town, nu.getUuid());
    }

    @Override
    public boolean npcIsHiredGuard(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        TownRecord town = townFor(playerRef, store);
        return nu != null && town != null && GuardHireService.isHiredGuard(town, nu.getUuid());
    }

    @Override
    public boolean npcIsActiveTourist(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        TownRecord town = townFor(playerRef, store);
        return nu != null && town != null && TouristPortalTickService.isActivePortalTourist(town, nu.getUuid());
    }

    @Override
    public boolean npcIsInvitedUnhousedTownsfolk(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        TownRecord town = townFor(playerRef, store);
        return nu != null
            && town != null
            && TouristPortalTickService.isInvitedUnhousedTourist(town, nu.getUuid(), plugin);
    }

    @Override
    public boolean npcIsQuestTarget(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull String questId
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        TownRecord town = townFor(playerRef, store);
        if (nu == null || town == null) {
            return false;
        }
        UUID target = town.getQuestTargetEntityUuid(questId);
        return target != null && target.equals(nu.getUuid());
    }

    @Override
    public boolean questTargetEntityHoused(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        TownRecord town = townFor(playerRef, store);
        if (town == null) {
            return false;
        }
        UUID target = town.getQuestTargetEntityUuid(questId);
        return target != null && town.isNpcHomeResidentOnHousePlot(target, plugin.getConstructionCatalog());
    }

    @Override
    public boolean questCompletedForNpc(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull String questId
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        TownRecord town = townFor(playerRef, store);
        return nu != null && town != null && town.hasQuestCompletedForEntity(questId, nu.getUuid());
    }

    @Override
    public boolean playerHasTouristMoveInItems(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        var requirements = com.hexvane.aetherhaven.tourist.TouristMoveInRequirements.forNpc(plugin, store, npcRef);
        if (requirements.isEmpty()) {
            TownRecord town = townFor(playerRef, store);
            if (town != null) {
                requirements = com.hexvane.aetherhaven.tourist.TouristMoveInRequirements.forQuestTarget(plugin, town, store);
            }
        }
        if (requirements.isEmpty()) {
            return false;
        }
        return com.hexvane.aetherhaven.tourist.TouristMoveInRequirements.playerHasAll(playerRef, store, requirements);
    }

    @Override
    public boolean townQuestObjectiveIncomplete(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String questId,
        @Nonnull String objectiveId
    ) {
        TownRecord town = townFor(playerRef, store);
        if (town == null) {
            return false;
        }
        return QuestProgressionService.isQuestObjectiveIncomplete(plugin, town, questId, objectiveId);
    }

    @Override
    public boolean townQuestObjectiveComplete(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String questId,
        @Nonnull String objectiveId
    ) {
        TownRecord town = townFor(playerRef, store);
        if (town == null) {
            return false;
        }
        return QuestProgressionService.isQuestObjectiveComplete(plugin, town, questId, objectiveId);
    }

    @Override
    public boolean npcOtherVillagerNearby(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        float radiusBlocks,
        @Nullable String kindFilter
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        TownVillagerBinding speakerBinding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (speakerBinding == null) {
            return false;
        }
        return DialogueNpcConditionUtil.isOtherTownVillagerNearby(
            store,
            npcRef,
            speakerBinding.getTownId(),
            radiusBlocks,
            kindFilter
        );
    }

    @Override
    public boolean npcReputationHeartsAtLeast(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        int hearts
    ) {
        int rep = speakerReputation(playerRef, store, npcRef);
        return rep >= DialogueNpcConditionUtil.reputationForHearts(hearts);
    }

    @Override
    public boolean npcReputationHeartsBelow(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        int hearts
    ) {
        int rep = speakerReputation(playerRef, store, npcRef);
        return rep < DialogueNpcConditionUtil.reputationForHearts(hearts);
    }

    @Override
    public boolean npcMoodAtLeast(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        int percent
    ) {
        DialogueNpcConditionUtil.VillagerNeedsSnapshot snapshot = speakerNeedsSnapshot(playerRef, store, npcRef);
        return DialogueNpcConditionUtil.minNeedPercent(snapshot) >= percent;
    }

    @Override
    public boolean npcMoodBelow(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        int percent
    ) {
        DialogueNpcConditionUtil.VillagerNeedsSnapshot snapshot = speakerNeedsSnapshot(playerRef, store, npcRef);
        return DialogueNpcConditionUtil.minNeedPercent(snapshot) < percent;
    }

    @Override
    public boolean npcHungerAtLeast(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        int percent
    ) {
        DialogueNpcConditionUtil.VillagerNeedsSnapshot snapshot = speakerNeedsSnapshot(playerRef, store, npcRef);
        return DialogueNpcConditionUtil.hungerPercent(snapshot) >= percent;
    }

    @Override
    public boolean npcHungerBelow(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        int percent
    ) {
        DialogueNpcConditionUtil.VillagerNeedsSnapshot snapshot = speakerNeedsSnapshot(playerRef, store, npcRef);
        return DialogueNpcConditionUtil.hungerPercent(snapshot) < percent;
    }

    @Override
    public boolean playerRecentlyFinishedQuestBoardQuest(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        int withinDays,
        @Nullable String giverRoleId,
        @Nullable String configEntryId
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        return town.wasQuestBoardCompleteWithin(
            pu.getUuid(),
            VillagerReputationService.currentGameEpochDay(store),
            withinDays,
            giverRoleId,
            configEntryId
        );
    }

    @Override
    public boolean playerRecentlyFailedQuestBoardQuest(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, int withinDays
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        return town.wasQuestBoardFailWithin(
            pu.getUuid(),
            VillagerReputationService.currentGameEpochDay(store),
            withinDays
        );
    }

    private int speakerReputation(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return 0;
        }
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (town == null || pu == null || nu == null) {
            return 0;
        }
        return VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), nu.getUuid()).getReputation();
    }

    @Nullable
    private DialogueNpcConditionUtil.VillagerNeedsSnapshot speakerNeedsSnapshot(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        return DialogueNpcConditionUtil.resolveSpeakerNeeds(store, npcRef, townFor(playerRef, store));
    }
}
