package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtState;
import com.hexvane.aetherhaven.gaiadraught.PlayerHealUtil;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.gift.VillagerGiftService;
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
                TownRecord nt = tm.getTown(b.getTownId());
                if (nt != null && nt.hasMemberOrOwner(playerUuid)) {
                    return nt;
                }
                return null;
            }
        }
        return tm.findTownForPlayerInWorld(playerUuid);
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
        TownRecord t = townFor(playerRef, store);
        return t != null && t.hasQuestActive(questId.trim());
    }

    @Override
    public boolean townQuestCompleted(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        TownRecord t = townFor(playerRef, store);
        return t != null && t.hasQuestCompleted(questId.trim());
    }

    @Override
    public boolean townHasCompletePlot(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String constructionId
    ) {
        TownRecord t = townFor(playerRef, store);
        return t != null && t.hasCompletePlotWithConstruction(constructionId.trim());
    }

    @Override
    public boolean aetherhavenHasTown(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return townFor(playerRef, store) != null;
    }

    @Override
    public boolean aetherhavenPlayerCanAcceptQuests(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store
    ) {
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
        if (!t.hasCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_PLOT_INN)) {
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
                TownRecord town = tm.getTown(b.getTownId());
                if (town == null || !town.hasMemberOrOwner(pu.getUuid())) {
                    return null;
                }
                return town;
            }
            return tm.findTownForPlayerInWorld(pu.getUuid());
        }
        return tm.findTownForPlayerInWorld(pu.getUuid());
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
            t = tm.getTown(nb.getTownId());
            if (t != null && !t.hasMemberOrOwner(playerUuid)) {
                t = null;
            }
        } else {
            t = townFor(playerRef, store);
        }
        return t != null && t.isNpcHomeResidentOnHousePlot(npcUuid);
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
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        GaiaDraughtState s = town.findGaiaDraughtState(pu.getUuid());
        return s != null && s.isUnlocked();
    }

    @Override
    public boolean gaiaDraughtChargesBelowCapacity(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        GaiaDraughtState s = town.findGaiaDraughtState(pu.getUuid());
        return s != null && s.isUnlocked() && s.getCharges() < s.getCapacity();
    }

    @Override
    public boolean gaiaDraughtCapacityBelowMax(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        GaiaDraughtState s = town.findGaiaDraughtState(pu.getUuid());
        return s != null && s.isUnlocked() && s.canApplyShardUpgrade();
    }

    @Override
    public boolean gaiaDraughtHealTierBelowMax(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return false;
        }
        GaiaDraughtState s = town.findGaiaDraughtState(pu.getUuid());
        return s != null && s.isUnlocked() && s.canApplyCatalystUpgrade();
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
        GaiaDraughtState s = town.findGaiaDraughtState(pu.getUuid());
        if (s == null || !s.isUnlocked() || !s.canApplyShardUpgrade()) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
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
        GaiaDraughtState s = town.findGaiaDraughtState(pu.getUuid());
        if (s == null || !s.isUnlocked() || !s.canApplyCatalystUpgrade()) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
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
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return 0L;
        }
        GaiaDraughtState s = town.findGaiaDraughtState(pu.getUuid());
        if (s == null) {
            return AetherhavenConstants.gaiaDraughtShardUpgradeGoldCost(0);
        }
        s.ensureLegacyMigrated();
        return AetherhavenConstants.gaiaDraughtShardUpgradeGoldCost(s.getShardUpgradeCount());
    }

    @Override
    public long nextGaiaDraughtCatalystUpgradeGoldCost(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = townFor(playerRef, store);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (town == null || pu == null) {
            return 0L;
        }
        GaiaDraughtState s = town.findGaiaDraughtState(pu.getUuid());
        if (s == null) {
            return AetherhavenConstants.gaiaDraughtCatalystUpgradeGoldCost(0);
        }
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
}
