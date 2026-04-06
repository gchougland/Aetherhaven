package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionExecutor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** House management block: assign a villager to a residential plot and complete matching house quests. */
public final class HouseResidentAssignment {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HouseResidentAssignment() {}

    /**
     * Sets the plot's home resident (or clears when {@code residentUuid} is null). Updates town data.
     * Completes an active house quest when the assigned villager matches that quest's NPC role.
     */
    public static void assignResident(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nullable UUID residentUuid,
        @Nonnull TownManager tm,
        @Nullable Ref<EntityStore> assignerPlayerRef
    ) {
        PlotInstance pi = town.findPlotById(plotId);
        if (pi == null || !AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(pi.getConstructionId())) {
            return;
        }
        if (pi.getState() != PlotInstanceState.COMPLETE) {
            return;
        }
        if (residentUuid != null) {
            town.clearHomeResidentFromOtherPlots(plotId, residentUuid);
        }
        pi.setHomeResidentEntityUuid(residentUuid);
        tm.updateTown(town);
        if (residentUuid != null) {
            tryCompleteHouseQuest(world, plugin, town, tm, residentUuid, assignerPlayerRef);
        }
    }

    private static void tryCompleteHouseQuest(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID assignedNpcUuid,
        @Nullable Ref<EntityStore> assignerPlayerRef
    ) {
        String role = resolveRoleForEntity(world, assignedNpcUuid);
        if (role == null) {
            return;
        }
        String qid = questIdForRole(role);
        if (qid == null || !town.hasQuestActive(qid)) {
            return;
        }
        var es = world.getEntityStore();
        Store<EntityStore> store = es != null ? es.getStore() : null;
        DialogueActionExecutor.applyQuestCompletion(world, plugin, town, tm, qid, assignerPlayerRef, assignedNpcUuid, store);
        LOGGER.atInfo().log("House quest completed via assignment: %s for %s", qid, assignedNpcUuid);
    }

    @Nullable
    private static String resolveRoleForEntity(@Nonnull World world, @Nonnull UUID entityUuid) {
        var es = world.getEntityStore();
        if (es == null) {
            return null;
        }
        Store<EntityStore> store = es.getStore();
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) {
            return null;
        }
        NPCEntity npc = store.getComponent(ref, npcType);
        return npc != null ? npc.getRoleName() : null;
    }

    @Nullable
    private static String questIdForRole(@Nonnull String roleId) {
        if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equals(roleId)) {
            return AetherhavenConstants.QUEST_HOUSE_ELDER;
        }
        if (AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equals(roleId)) {
            return AetherhavenConstants.QUEST_HOUSE_INNKEEPER;
        }
        if (AetherhavenConstants.NPC_MERCHANT.equals(roleId)) {
            return AetherhavenConstants.QUEST_HOUSE_MERCHANT;
        }
        if (AetherhavenConstants.NPC_FARMER.equals(roleId)) {
            return AetherhavenConstants.QUEST_HOUSE_FARMER;
        }
        if (AetherhavenConstants.NPC_BLACKSMITH.equals(roleId)) {
            return AetherhavenConstants.QUEST_HOUSE_BLACKSMITH;
        }
        return null;
    }
}
