package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Loads an existing custom building definition into a plot creator draft for editing. */
public final class PlotCreatorDraftLoader {
    private PlotCreatorDraftLoader() {}

    public static void loadIntoDraft(@Nonnull PlotCreatorDraft draft, @Nonnull ConstructionDefinition def) {
        draft.setEditingConstructionId(def.getId());
        draft.setConstructionId(def.getId());
        draft.setDisplayName(def.getDisplayName());
        draft.setDescription(def.getDescription());
        draft.setPrefabPath(def.getPrefabPath());
        draft.setPrefabFileName(def.getPrefabPath());
        draft.setPlotAnchorOffset(def.getPlotAnchorOffset());
        draft.setRotationYaw(def.getRotationYaw());
        draft.setTreasuryGoldCoinCost(def.getTreasuryGoldCoinCost());
        draft.setSelfBuildGameDays(def.getSelfBuildGameDays());
        draft.setSelfBuildDaysInput(PlotCreatorService.formatSelfBuildDaysForField(def.getSelfBuildGameDays()));
        draft.setAssemblyPrefabSectionsPerAxis(def.getAssemblyPrefabSectionsPerAxis());
        draft.setAssemblySectionsInput(
            def.getAssemblyPrefabSectionsPerAxis() > 1
                ? String.valueOf(def.getAssemblyPrefabSectionsPerAxis())
                : null
        );
        draft.setScheduleSharedUtilityPick(def.isScheduleSharedUtilityPick());
        draft.setTouristDestination(def.isTouristDestination());
        draft.setPlotTokenLockedByDefault(def.isPlotTokenLockedByDefault());
        draft.setExcludeFromTownJournal(def.isExcludeFromTownJournal());
        draft.getBuildingTags().clear();
        draft.getBuildingTags().addAll(def.getBuildingTags());
        draft.getMaterials().clear();
        draft.getMaterials().addAll(def.getMaterials());
        draft.getPois().clear();
        for (BuildingPoisDefinition.PoiRow row : def.getPois()) {
            PlotCreatorPoiDraft p = new PlotCreatorPoiDraft();
            p.setLocal(row.getLocalX(), row.getLocalY(), row.getLocalZ());
            p.setTags(new ArrayList<>(row.getTags()));
            p.setCapacity(row.getCapacity());
            p.setBlockTypeId(row.getBlockTypeId());
            p.setInteractionKind(row.getInteractionKind().name());
            if (row.hasInteractionTargetLocal()) {
                p.setInteractionTargetLocal(
                    row.getInteractionTargetLocalX(),
                    row.getInteractionTargetLocalY(),
                    row.getInteractionTargetLocalZ()
                );
            }
            draft.getPois().add(p);
        }
        copyPos(def.getManagementBlockLocalPos(), draft::setManagementBlockLocalPos);
        copyPos(def.getTreasuryLocalPos(), draft::setTreasuryLocalPos);
        copyPos(def.getShopSafeLocalPos(), draft::setShopSafeLocalPos);
        copyPos(def.getInnkeeperSpawnLocal(), draft::setInnkeeperSpawnLocal);
        copyPos(def.getGuildMasterSpawnLocal(), draft::setGuildMasterSpawnLocal);
        draft.getVisitorSpawnLocals().clear();
        if (def.getVisitorSpawnLocals() != null) {
            for (int[] v : def.getVisitorSpawnLocals()) {
                if (v != null && v.length >= 3) {
                    draft.getVisitorSpawnLocals().add(new int[] {v[0], v[1], v[2]});
                }
            }
        }
        draft.getAdventurerSpawns().clear();
        int[][] adventurerLocals = def.getAdventurerSpawnLocals();
        if (adventurerLocals != null) {
            float[] yaws = def.getAdventurerSpawnYaws();
            for (int i = 0; i < adventurerLocals.length; i++) {
                int[] a = adventurerLocals[i];
                if (a != null && a.length >= 3) {
                    float yaw = yaws != null && i < yaws.length ? yaws[i] : 0f;
                    draft.getAdventurerSpawns().add(new PlotCreatorAdventurerSpawnEntry(a[0], a[1], a[2], yaw));
                }
            }
        }
        String countsAs = def.getCountsAsConstructionIdRaw();
        if (countsAs != null && !countsAs.isBlank()) {
            draft.setCountsAsConstructionId(countsAs.trim());
        }
        draft.setKind(inferKind(def));
    }

    @Nonnull
    private static PlotBuildingKind inferKind(@Nonnull ConstructionDefinition def) {
        String countsAs = def.getCountsAsConstructionIdRaw();
        if (countsAs != null && !countsAs.isBlank() && !countsAs.trim().equals(def.getId())) {
            return PlotBuildingKind.VARIANT;
        }
        String id = def.getId();
        if (AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL.equals(id)) {
            return PlotBuildingKind.TOWN_HALL;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_INN.equals(id)) {
            return PlotBuildingKind.INN;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(id)) {
            return PlotBuildingKind.GUILD_HALL;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_PARK.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR.equals(id)) {
            return PlotBuildingKind.AMENITY;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_BOMB_SHOP.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_CRYSTAL_KEEPERS_SHOP.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP.equals(id)) {
            return PlotBuildingKind.SHOP;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP.equals(id)) {
            return PlotBuildingKind.PLAYER_SHOP;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_TOURIST_PORTAL.equals(id)) {
            return PlotBuildingKind.TOURIST_PORTAL;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(id)) {
            return PlotBuildingKind.HOME;
        }
        if (def.getInnkeeperSpawnLocal() != null) {
            return PlotBuildingKind.INN;
        }
        if (def.getAdventurerSpawnLocals() != null && def.getAdventurerSpawnLocals().length > 0) {
            return PlotBuildingKind.GUILD_HALL;
        }
        if (def.getTreasuryLocalPos() != null) {
            return PlotBuildingKind.TOWN_HALL;
        }
        if (def.isExcludeFromTownJournal() && def.getPois().isEmpty()) {
            return PlotBuildingKind.DECORATION;
        }
        for (BuildingPoisDefinition.PoiRow p : def.getPois()) {
            if (p.getTags().contains("SHOP")) {
                return PlotBuildingKind.SHOP;
            }
            if (p.getTags().contains("FUN") || p.getTags().contains("SIT")) {
                return PlotBuildingKind.AMENITY;
            }
            if (p.getTags().contains("SLEEP") || p.getTags().contains("ENERGY")) {
                return PlotBuildingKind.HOME;
            }
        }
        return PlotBuildingKind.WORK;
    }

    private static void copyPos(@Nullable int[] src, @Nonnull java.util.function.Consumer<int[]> setter) {
        if (src != null && src.length >= 3) {
            setter.accept(new int[] {src[0], src[1], src[2]});
        }
    }
}
