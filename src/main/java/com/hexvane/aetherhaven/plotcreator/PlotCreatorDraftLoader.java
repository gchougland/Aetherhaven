package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import java.util.ArrayList;
import java.util.List;
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
        draft.setMaxHomeResidents(def.getMaxHomeResidents());
        draft.setMaxHomeResidentsInput(String.valueOf(def.getMaxHomeResidents()));
        draft.setScheduleSharedUtilityPick(def.isScheduleSharedUtilityPick());
        draft.setTouristDestination(def.isTouristDestination());
        draft.setPreserveWater(def.isPreserveWater());
        draft.setPlotTokenLockedByDefault(def.isPlotTokenLockedByDefault());
        draft.setFloatingGiftBlueprint(def.isFloatingGiftBlueprint());
        draft.setExcludeFromTownJournal(def.isExcludeFromTownJournal());
        draft.setStyleId(def.getStyleId());
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
            p.setWorkResidentKind(row.getWorkResidentKind());
            p.setInteractionTargetYawDegrees(row.getInteractionTargetYawDegrees());
            p.setEquipmentProfileId(row.getEquipmentProfileId());
            draft.getPois().add(p);
        }
        copyPos(def.getManagementBlockLocalPos(), draft::setManagementBlockLocalPos);
        copyPos(def.getTreasuryLocalPos(), draft::setTreasuryLocalPos);
        copyPos(def.getShopSafeLocalPos(), draft::setShopSafeLocalPos);
        copyPos(def.getProductionStorageLocalPos(), draft::setProductionStorageLocalPos);
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
        draft.setCountsAsConstructionIds(def.getCountsAsConstructionIds());
        draft.setKinds(inferKinds(def));
        stampWorkResidentKinds(draft);
        PlotCreatorGaiaStatueSupport.extractLocalPosFromPois(draft);
        seedSelectedSpotsFromDefinition(draft, def);
    }

    private static void seedSelectedSpotsFromDefinition(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull ConstructionDefinition def
    ) {
        draft.getSelectedSpots().clear();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        List<PlotBuildingKindRequirements.SubstepRequirement> defaults =
            PlotBuildingKindRequirements.defaultRequirements(draft, plugin);
        for (PlotBuildingKindRequirements.SubstepRequirement req : defaults) {
            draft.getSelectedSpots().add(req.toSpotEntry());
        }
        // Ensure recorded work roles are represented even if defaults missed them.
        for (BuildingPoisDefinition.PoiRow row : def.getPois()) {
            String role = row.getWorkResidentKind();
            if (role == null || role.isBlank()) {
                if (row.getTags().contains(AetherhavenConstants.POI_TAG_BARD)) {
                    role = com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_BARD;
                } else {
                    continue;
                }
            }
            PlotCreatorSpotEntry entry =
                com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_BARD.equals(role)
                    ? PlotCreatorSpotEntry.bard(1)
                    : PlotCreatorSpotEntry.work(role, 1);
            if (!draft.getSelectedSpots().contains(entry)) {
                draft.getSelectedSpots().add(entry);
            }
        }
        if (!draft.getSelectedSpots().isEmpty()) {
            draft.setImportantSpotsConfirmed(true);
        }
    }

    /**
     * Shipped WORK POIs often omit {@code workResidentKind}. Stamp the workplace role so markers, checklist counts,
     and the miner (etc.) substep treat them as already placed.
     */
    private static void stampWorkResidentKinds(@Nonnull PlotCreatorDraft draft) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        List<String> roles = PlotBuildingKindRequirements.workplaceRolesForDraft(draft, plugin);
        String primary =
            roles.stream()
                .filter(r -> r != null && !r.isBlank() && !com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_BARD.equals(r))
                .findFirst()
                .orElse(roles.isEmpty() ? null : roles.get(0));
        if (primary == null || primary.isBlank()) {
            return;
        }
        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            if (!poi.getTags().contains("WORK")) {
                continue;
            }
            if (poi.getTags().contains(AetherhavenConstants.POI_TAG_BARD)) {
                if (poi.getWorkResidentKind() == null) {
                    poi.setWorkResidentKind(com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_BARD);
                }
                continue;
            }
            if (poi.getWorkResidentKind() == null || poi.getWorkResidentKind().isBlank()) {
                poi.setWorkResidentKind(primary);
            }
        }
    }

    @Nonnull
    private static List<PlotBuildingKind> inferKinds(@Nonnull ConstructionDefinition def) {
        java.util.LinkedHashSet<PlotBuildingKind> kinds = new java.util.LinkedHashSet<>();
        List<String> countsAs = def.getCountsAsConstructionIds();
        if (!countsAs.isEmpty()) {
            kinds.add(PlotBuildingKind.VARIANT);
            for (String baseId : countsAs) {
                PlotBuildingKind base = PlotBuildingKindRequirements.resolveBaseKind(baseId, AetherhavenPlugin.get());
                // VARIANT alone is enough for authoring; tourist portal add-on from spots.
            }
        } else {
            kinds.add(inferPrimaryKind(def));
        }
        if (def.getPois().stream().anyMatch(p ->
            AetherhavenConstants.TOURIST_PORTAL_BLOCK_TYPE_ID.equals(p.getBlockTypeId()))) {
            kinds.add(PlotBuildingKind.TOURIST_PORTAL);
        }
        // Tourist portal block on definition fields is only the portal kind itself.
        if (AetherhavenConstants.CONSTRUCTION_PLOT_TOURIST_PORTAL.equals(def.getId())
            || countsAs.contains(AetherhavenConstants.CONSTRUCTION_PLOT_TOURIST_PORTAL)) {
            kinds.add(PlotBuildingKind.TOURIST_PORTAL);
        }
        if (kinds.isEmpty()) {
            kinds.add(PlotBuildingKind.WORK);
        }
        return new ArrayList<>(kinds);
    }

    @Nonnull
    private static PlotBuildingKind inferPrimaryKind(@Nonnull ConstructionDefinition def) {
        if (def.isDecorationPlot()) {
            return PlotBuildingKind.DECORATION;
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
            || AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT.equals(id)) {
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
