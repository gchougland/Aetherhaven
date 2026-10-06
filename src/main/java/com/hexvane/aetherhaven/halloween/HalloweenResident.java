package com.hexvane.aetherhaven.halloween;

import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticAppearanceService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;

/** The encounter follows the server's real calendar. Resident life is never seasonal. */
public final class HalloweenResident {
    public static final String KIND = "halloween";
    public static final String RESCUE_KIND = "rescue_halloween";
    public static final String RESCUE_QUEST = "q_halloween_rescue";
    public static final String MODEL = "Aetherhaven_Halloween";
    public static final String HEADLESS_MODEL = "Aetherhaven_Halloween_Headless";
    private HalloweenResident() {}

    public static boolean rescueSeason() { return rescueSeason(Clock.systemDefaultZone()); }
    public static boolean rescueSeason(Clock clock) { return LocalDate.now(clock).getMonth() == Month.OCTOBER; }
    public static String modelForHead(boolean worn) { return worn ? MODEL : HEADLESS_MODEL; }

    public static boolean canAcceptRescue(Ref<EntityStore> player, Store<EntityStore> store, Ref<EntityStore> npc) {
        var plugin=com.hexvane.aetherhaven.AetherhavenPlugin.get();
        if(plugin==null||player==null||!player.isValid()||npc==null||!npc.isValid())return false;
        var binding=store.getComponent(npc,TownVillagerBinding.getComponentType());
        var identity=store.getComponent(player,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        var npcIdentity=store.getComponent(npc,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        if(binding==null||!RESCUE_KIND.equals(binding.getKind())||identity==null||npcIdentity==null)return false;
        var manager=com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getTownAcrossWorlds(binding.getTownId(),manager);
        return canAcceptRescue(town,identity.getUuid(),npcIdentity.getUuid(),store.getExternalData().getWorld().getName(),Clock.systemDefaultZone());
    }
    static boolean canAcceptRescue(com.hexvane.aetherhaven.town.TownRecord town,java.util.UUID player,
            java.util.UUID npc,String sourceWorld,Clock calendar) {
        return canAcceptRescue(town,player,calendar)&&town.halloweenRescue().authorizes(npc,sourceWorld);
    }
    static boolean canAcceptRescue(com.hexvane.aetherhaven.town.TownRecord town,java.util.UUID player,Clock calendar) {
        return rescueSeason(calendar)&&town!=null&&town.hasMemberOrOwner(player)&&town.playerCanCompleteQuests(player)
            && (town.hasQuestActive(RESCUE_QUEST)||town.hasQuestCompleted(RESCUE_QUEST)||town.playerCanAcceptQuests(player));
    }

    /** Swap only Jack's authored attachment variant, retaining body scale and persistent appearance. */
    public static boolean setHeadWorn(Ref<EntityStore> npc, Store<EntityStore> store, boolean worn) {
        if (npc == null || !npc.isValid()) return false;
        var binding = store.getComponent(npc, TownVillagerBinding.getComponentType());
        if (binding == null || !(KIND.equals(binding.getKind()) || RESCUE_KIND.equals(binding.getKind()))) return false;
        var current = store.getComponent(npc, ModelComponent.getComponentType());
        if (current != null && current.getModel() != null && modelForHead(worn).equals(current.getModel().getModelAssetId())) return true;
        var asset = ModelAsset.getAssetMap().getAsset(modelForHead(worn));
        if (asset == null) return false;
        float scale = current == null || current.getModel() == null ? 1f : current.getModel().getScale();
        VillagerCosmeticAppearanceService.applyModel(npc, store, Model.createScaledModel(asset, scale));
        return true;
    }
}
