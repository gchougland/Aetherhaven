package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** When the Gaia altar prefab completes, move the priestess from the inn pool to the WORK POI. Quest completes on dialogue. */
public final class GaiaAltarCompletion {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private GaiaAltarCompletion() {}

    public static void onAltarBuilt(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID altarPlotId,
        @Nonnull TownManager tm
    ) {
        if (!town.hasQuestActiveOrCompleted(AetherhavenConstants.QUEST_GAIA_ALTAR)) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();

        PlotInstance altarPlot = town.findPlotById(altarPlotId);
        if (altarPlot == null) {
            return;
        }
        if (!hasWorkPoiForAltar(world, plugin, town, altarPlotId, altarPlot)) {
            LOGGER.atWarning().log("No WORK POI for Gaia altar plot %s", altarPlotId);
            return;
        }

        Ref<EntityStore> priestessRef = findPriestessRef(store, town);
        if (priestessRef == null || !priestessRef.isValid()) {
            return;
        }
        UUIDComponent uuidComp = store.getComponent(priestessRef, UUIDComponent.getComponentType());
        UUID npcUuid = uuidComp != null ? uuidComp.getUuid() : null;
        if (npcUuid != null) {
            town.getInnPoolNpcIds().removeIf(s -> {
                try {
                    return npcUuid.equals(UUID.fromString(s.trim()));
                } catch (Exception e) {
                    return false;
                }
            });
        }
        store.putComponent(
            priestessRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_PRIESTESS, altarPlotId, altarPlotId)
        );
        VillagerAutonomySystem.promptWorkplaceTravel(
            priestessRef,
            store,
            VillagerAutonomySystem.resolveAutonomyNowMs(store)
        );
        town.addInnVisitorPoolExcludedRoleId(AetherhavenConstants.NPC_PRIESTESS);
        if (uuidComp != null) {
            ResidentRegistryService.upsert(
                town,
                tm,
                AetherhavenConstants.NPC_PRIESTESS,
                TownVillagerBinding.KIND_PRIESTESS,
                altarPlotId,
                uuidComp.getUuid()
            );
        }
        tm.updateTown(town);
        LOGGER.atInfo().log("Assigned priestess to Gaia altar plot %s; pathing to work POI", altarPlotId);
    }

    private static boolean hasWorkPoiForAltar(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID altarPlotId,
        @Nonnull PlotInstance altarPlot
    ) {
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        for (PoiEntry e : reg.listByTown(town.getTownId())) {
            if (altarPlotId.equals(e.getPlotId()) && e.getTags().contains("WORK")) {
                return true;
            }
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(altarPlot.getConstructionId());
        if (def == null) {
            return false;
        }
        for (BuildingPoisDefinition.PoiRow row : def.getPois()) {
            if (row.getTags().contains("WORK")) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Ref<EntityStore> findPriestessRef(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        List<String> ids = town.getInnPoolNpcIds();
        for (String sid : ids) {
            try {
                UUID u = UUID.fromString(sid.trim());
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                var npcType = NPCEntity.getComponentType();
                NPCEntity npc = npcType != null ? store.getComponent(ref, npcType) : null;
                if (npc != null && AetherhavenConstants.NPC_PRIESTESS.equals(npc.getRoleName())) {
                    return ref;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
