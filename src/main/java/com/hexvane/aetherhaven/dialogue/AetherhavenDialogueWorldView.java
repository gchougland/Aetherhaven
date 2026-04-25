package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
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
}
