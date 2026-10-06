package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure population rules shared by the journal, admission checks, and recruitment. */
public final class TownPopulation {
    public static final int FOOD_PER_RESIDENT = 2;
    private static final int[] LEVEL_STARTS = {0, 4, 7, 11, 16, 21};
    private TownPopulation() {}

    public record Snapshot(int residents, int foodPoints, int capacity, int level, int nextLevelPopulation,
                           float levelProgress) {
        public boolean canAdmit() { return (long) foodPoints >= FOOD_PER_RESIDENT * ((long) residents + 1); }
        public int foodShortfallForNextResident() { return Math.max(0, FOOD_PER_RESIDENT * (residents + 1) - foodPoints); }
    }

    public static Snapshot snapshot(TownRecord town, ConstructionCatalog catalog) {
        int residents = count(town);
        int food = 0;
        for (String source : new String[]{"plot_inn", "plot_farm", "plot_barn", "plot_restaurant"}) {
            if (town.hasCompletePlotWithConstruction(catalog, source)) food += 10;
        }
        for (var site : town.getApprenticeWorkplaces().sites()) {
            if (com.hexvane.aetherhaven.workplace.WorkplaceFood.supports(site.type())) food += site.food().foodPoints();
        }
        int level = levelFor(residents);
        int next = level < LEVEL_STARTS.length ? LEVEL_STARTS[level] : 0;
        int start = LEVEL_STARTS[level - 1];
        float progress = next == 0 ? 1f : Math.clamp((float) (residents - start) / (next - start), 0f, 1f);
        return new Snapshot(residents, food, food / FOOD_PER_RESIDENT, level, next, progress);
    }

    public static int levelFor(int population) {
        int level = 1;
        while (level < LEVEL_STARTS.length && population >= LEVEL_STARTS[level]) level++;
        return level;
    }

    public static int count(TownRecord town) {
        Set<String> residents = new HashSet<>();
        Map<UUID, String> knownIdentities = new HashMap<>();
        Set<UUID> visitors = new HashSet<>();
        for (String id : town.getInnPoolNpcIds()) { try { visitors.add(UUID.fromString(id)); } catch (RuntimeException ignored) {} }
        for (String id : town.getGuildHallAdventurerNpcIds()) { try { visitors.add(UUID.fromString(id)); } catch (RuntimeException ignored) {} }
        for (var tourist : town.getTouristRecords()) if (!tourist.isCitizen()) visitors.add(tourist.getEntityUuid());
        Set<UUID> townsfolkResidents = new HashSet<>();
        for (var guard : town.getHiredGuardRecords()) if (guard.getEntityUuid() != null) townsfolkResidents.add(guard.getEntityUuid());
        for (var tourist : town.getTouristRecords()) if (tourist.isCitizen()) townsfolkResidents.add(tourist.getEntityUuid());
        for (var plot : town.getPlotInstances()) townsfolkResidents.addAll(plot.getHomeResidentEntityUuids());
        for (var row : town.getResidentNpcRecords()) {
            UUID uuid = row.getLastEntityUuid();
            if (TownVillagerBinding.isVisitorKind(row.getKind()) || visitors.contains(uuid)) continue;
            boolean individual = TownVillagerBinding.KIND_GUARD.equals(row.getKind()) || TownVillagerBinding.KIND_TOWNSFOLK.equals(row.getKind());
            if (individual && !townsfolkResidents.contains(uuid) && !row.isPendingDawnRevival()) continue;
            String key = individual ? "person:" + uuid : "core:" + row.getNpcRoleId();
            residents.add(key);
            knownIdentities.put(uuid, key);
        }
        if (town.isElderSpawned() || town.getElderEntityUuid() != null) {
            String key = "core:" + AetherhavenConstants.ELDER_NPC_ROLE_ID;
            residents.add(key);
            if (town.getElderEntityUuid() != null) knownIdentities.put(town.getElderEntityUuid(), key);
        }
        if (town.isInnActive() || town.getInnkeeperEntityUuid() != null) {
            String key = "core:" + AetherhavenConstants.INNKEEPER_NPC_ROLE_ID;
            residents.add(key);
            if (town.getInnkeeperEntityUuid() != null) knownIdentities.put(town.getInnkeeperEntityUuid(), key);
        }
        for (UUID uuid : townsfolkResidents) if (uuid != null && !visitors.contains(uuid)) {
            residents.add(knownIdentities.getOrDefault(uuid, "person:" + uuid));
        }
        return residents.size();
    }
}
