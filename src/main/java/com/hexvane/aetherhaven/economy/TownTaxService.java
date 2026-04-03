package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Daily gold tax into {@link TownRecord} treasury; same morning window as {@link com.hexvane.aetherhaven.inn.InnPoolService}.
 */
public final class TownTaxService {
    private TownTaxService() {}

    public static void tickMorningTax(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Store<EntityStore> store
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        int morningStart = cfg.getInnPoolMorningStartHour();
        int morningEndEx = cfg.getInnPoolMorningEndHourExclusive();
        if (!isMorningForTax(wtr, morningStart, morningEndEx)) {
            return;
        }
        long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        int maxPer = cfg.getTreasuryMaxGoldTaxPerVillagerPerDay();

        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            PlotInstance hall = town.findCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_TOWN_HALL_V1);
            if (hall == null) {
                continue;
            }
            Long last = town.getTreasuryLastTaxEpochDay();
            if (last != null && last >= epochDay) {
                continue;
            }
            long added = computeTaxForTown(town, store, maxPer);
            town.addTreasuryGoldCoins(added);
            town.setTreasuryLastTaxEpochDay(epochDay);
            tm.updateTown(town);
        }
    }

    private static long computeTaxForTown(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store, int maxPerVillager) {
        UUID tid = town.getTownId();
        long[] sum = new long[1];
        Query<EntityStore> q =
            Query.and(
                TownVillagerBinding.getComponentType(),
                VillagerNeeds.getComponentType(),
                UUIDComponent.getComponentType(),
                NPCEntity.getComponentType()
            );
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    VillagerNeeds needs = archetypeChunk.getComponent(i, VillagerNeeds.getComponentType());
                    if (needs == null) {
                        continue;
                    }
                    float avg = (needs.getHunger() + needs.getEnergy() + needs.getFun()) / 3f;
                    float ratio = Math.max(0f, Math.min(1f, avg / VillagerNeeds.MAX));
                    sum[0] += (long) Math.floor(maxPerVillager * ratio);
                }
            }
        );
        return sum[0];
    }

    private static boolean isMorningForTax(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        if (isInMorningHourWindow(wtr, morningStartHour, morningEndExclusive)) {
            return true;
        }
        return wtr.isScaledDayTimeWithinRange(0.18f, 0.42f);
    }

    private static boolean isInMorningHourWindow(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        int h = wtr.getCurrentHour();
        int start = Math.max(0, Math.min(23, morningStartHour));
        int end = morningEndExclusive;
        if (end <= start) {
            end = Math.min(start + 6, 24);
        }
        return h >= start && h < end;
    }
}
