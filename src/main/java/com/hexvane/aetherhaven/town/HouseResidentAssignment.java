package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.guild.VillagerDeathHandlerSystem;
import com.hexvane.aetherhaven.quest.QuestProgressionService;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** House management block: assign villagers to residential plot slots. House quests finish when the player talks to that NPC. */
public final class HouseResidentAssignment {
    private HouseResidentAssignment() {}

    /**
     * Sets slot 0's home resident (or clears when {@code residentUuid} is null). Updates town data.
     *
     * @deprecated Prefer {@link #assignResident(TownRecord, UUID, int, UUID, TownManager, World, Store, ConstructionCatalog)}.
     */
    @Deprecated
    public static void assignResident(
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nullable UUID residentUuid,
        @Nonnull TownManager tm,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        assignResident(town, plotId, 0, residentUuid, tm, null, null, constructionCatalog);
    }

    /**
     * Same as {@link #assignResident(TownRecord, UUID, UUID, TownManager, ConstructionCatalog)}; when {@code world} and {@code store} are
     * non-null, updates the resident NPC registry for revival UI.
     *
     * @deprecated Prefer {@link #assignResident(TownRecord, UUID, int, UUID, TownManager, World, Store, ConstructionCatalog)}.
     */
    @Deprecated
    public static void assignResident(
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nullable UUID residentUuid,
        @Nonnull TownManager tm,
        @Nullable World world,
        @Nullable Store<EntityStore> store,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        assignResident(town, plotId, 0, residentUuid, tm, world, store, constructionCatalog);
    }

    /**
     * Sets the plot's home resident at {@code slotIndex} (or clears when {@code residentUuid} is null). Updates town data.
     * When {@code world} and {@code store} are non-null, updates the resident NPC registry for revival UI.
     */
    public static void assignResident(
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        int slotIndex,
        @Nullable UUID residentUuid,
        @Nonnull TownManager tm,
        @Nullable World world,
        @Nullable Store<EntityStore> store,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        PlotInstance pi = town.findPlotById(plotId);
        if (pi == null
            || !constructionCatalog.matchesGameplayConstruction(
                pi.getConstructionId(),
                AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE
            )) {
            return;
        }
        if (pi.getState() != PlotInstanceState.COMPLETE) {
            return;
        }
        if (slotIndex < 0) {
            return;
        }
        ConstructionDefinition def = constructionCatalog.get(pi.getConstructionId());
        int maxSlots = def != null ? def.getMaxHomeResidents() : 1;
        if (slotIndex >= maxSlots) {
            return;
        }
        if (residentUuid != null && TownResidentEligibility.excludeFromHouseAssignmentPicker(town, residentUuid)) {
            return;
        }
        if (residentUuid != null) {
            town.clearHomeResidentFromOtherPlots(plotId, residentUuid);
            // Same house: clear other slots so a villager occupies at most one bed.
            for (int i = 0; i < maxSlots; i++) {
                if (i == slotIndex) {
                    continue;
                }
                UUID existing = pi.getHomeResidentAt(i);
                if (residentUuid.equals(existing)) {
                    pi.setHomeResidentAt(i, null);
                }
            }
        }
        pi.setHomeResidentAt(slotIndex, residentUuid);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (residentUuid != null && plugin != null) {
            String roleId = null;
            if (store != null) {
                Ref<EntityStore> residentRef = store.getExternalData().getRefFromUUID(residentUuid);
                NPCEntity npc =
                    residentRef != null && residentRef.isValid()
                        ? store.getComponent(residentRef, NPCEntity.getComponentType())
                        : null;
                roleId = npc != null ? npc.getRoleName() : null;
            }
            QuestProgressionService.onResidentAssigned(plugin, town, residentUuid, roleId);
        }
        tm.updateTown(town);
        if (residentUuid != null && world != null && store != null) {
            if (town.hasQuestActive(AetherhavenConstants.QUEST_HOUSE_GUARD)
                && residentUuid.equals(town.getQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_GUARD))) {
                if (plugin != null) {
                    VillagerDeathHandlerSystem.promoteGuardToCitizen(world, plugin, town, tm, residentUuid, store);
                }
            }
            if (town.hasQuestActive(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK)
                && residentUuid.equals(town.getQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK))) {
                TouristPortalTickService.promoteTouristToCitizen(town, tm, residentUuid, world, store, plugin);
            }
            // Pool tourists (including promoted citizens) are tracked in touristRecords, not residentNpcRecords.
            if (TouristPortalTickService.findTouristRecord(town, residentUuid) == null) {
                ResidentRegistryService.syncHouseAssignment(town, tm, store, residentUuid);
            }
        }
    }
}
