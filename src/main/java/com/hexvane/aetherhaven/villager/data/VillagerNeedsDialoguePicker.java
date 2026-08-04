package com.hexvane.aetherhaven.villager.data;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueNpcConditionUtil;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentEligibility;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Picks a stable-per-day low-need greeting when a town villager's meters are depleted. */
public final class VillagerNeedsDialoguePicker {
    private static final String LANG_PREFIX = "aetherhaven_dialogue_needs.aetherhaven.dialogue.needs.";
    private static final int VARIANTS_PER_NEED = 3;

    private VillagerNeedsDialoguePicker() {}

    @Nullable
    public static Message pickMessage(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull UUID npcEntityUuid
    ) {
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (binding == null || TownVillagerBinding.isVisitorKind(binding.getKind())) {
            return null;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        String roleId = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
        if (!TownResidentEligibility.usesVillagerNeeds(binding.getKind(), roleId, plugin)) {
            return null;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        DialogueNpcConditionUtil.VillagerNeedsSnapshot snapshot =
            DialogueNpcConditionUtil.resolveSpeakerNeeds(store, npcRef, town);
        DialogueNpcConditionUtil.LowNeedKind kind = DialogueNpcConditionUtil.resolveLowestLowNeed(snapshot);
        if (kind == null) {
            return null;
        }
        long day = VillagerReputationService.currentGameEpochDay(store);
        long seed =
            playerUuid.getMostSignificantBits()
                ^ playerUuid.getLeastSignificantBits()
                ^ npcEntityUuid.getMostSignificantBits()
                ^ npcEntityUuid.getLeastSignificantBits()
                ^ day
                ^ kind.ordinal();
        int variant = new Random(seed).nextInt(VARIANTS_PER_NEED);
        return Message.translation(LANG_PREFIX + kind.name().toLowerCase() + "." + variant);
    }
}
