package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.feast.FeastService;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.CharterTaxPolicy;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.time.AetherhavenMorningWindow;
import com.hexvane.aetherhaven.villager.AetherhavenRoleLabels;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Daily gold tithe into {@link TownRecord} treasury. Invoked only from {@link TownEconomyTimeService#onGameTimeFromHub}
 * (wired by {@link com.hexvane.aetherhaven.time.AetherhavenGameTimeBridgeSubscriber}) on the entity-store thread, once
 * per dawn-aligned game day when town hall is complete and the tithe total is positive.
 * {@link VillagerReputationService#currentGameEpochDay} matches reputation and other dawn-based dailies (not raw calendar midnight).
 */
public final class TownTaxService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TownTaxService() {}

    /** One resident row counted during a tax scan (loaded simulation only). */
    public record VillagerTaxLine(
        @Nonnull UUID entityUuid,
        @Nonnull String bindingKind,
        @Nullable String npcRole,
        @Nonnull String displayName,
        float hunger,
        float energy,
        float fun,
        float needsRatio,
        long contributionGold
    ) {}

    /**
     * Full morning tax snapshot for debugging. {@link #loadedResidentCount()} is NPCs in loaded chunks with binding +
     * needs; unloaded residents do not contribute until their chunks simulate.
     */
    public record TaxMorningBreakdown(
        boolean townHallComplete,
        @Nullable String taxPolicyId,
        int maxGoldPerResidentPerDay,
        int loadedResidentCount,
        @Nonnull List<VillagerTaxLine> lines,
        long sumBeforeTownMultipliers,
        boolean founderMonumentActive,
        int founderMonumentPermille,
        long sumAfterFounderMonument,
        boolean stewardsFeastTaxActive,
        int feastTaxBonusPermille,
        long finalTotal,
        boolean morningTaxWindow,
        long dawnAlignedEpochDay,
        @Nullable Long treasuryLastTaxEpochDay,
        boolean wouldCollectGoldOnNextMorningTick
    ) {}

    /**
     * Applies automatic daily tithe for all towns in this world. Called from {@link TownEconomyTimeService#onGameTimeFromHub}
     * on the entity-store tick thread (not deferred through {@link World#execute}).
     */
    public static void applyDailyTreasuryTithe(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Store<EntityStore> store
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        long titheDay = VillagerReputationService.currentGameEpochDay(store);

        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            PlotInstance hall = town.findCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL);
            if (hall == null) {
                continue;
            }
            Long last = town.getTreasuryLastTaxEpochDay();
            if (last != null && last >= titheDay) {
                continue;
            }
            TaxMorningBreakdown breakdown = computeTaxMorningBreakdown(town, store, cfg);
            long added = breakdown.finalTotal();
            // Only stamp the dawn day when gold was credited (never advance last on a dry run).
            if (added > 0L) {
                town.addTreasuryGoldCoins(added);
                town.setTreasuryLastTaxEpochDay(titheDay);
                tm.updateTown(town);
                LOGGER.atInfo().log(
                    "Aetherhaven treasury tithe applied: world=%s townId=%s +%d gold dawnDay=%d gameDateTime=%s loadedResidents=%d",
                    world.getName(),
                    town.getTownId(),
                    added,
                    titheDay,
                    wtr.getGameDateTime(),
                    breakdown.loadedResidentCount()
                );
            }
        }
    }

    /**
     * Debug: apply the current morning tithe math to this town, credit the treasury, and set last-collected day, without
     * requiring the in-game morning window. Does not add gold if the final total is 0 (e.g. no residents loaded).
     * Still requires a complete town hall when {@code requireCompleteTownHall} is true.
     *
     * @return gold credited, or -1 if town hall required but missing, -2 if already collected this dawn day (when
     *     not ignoring), or 0 if the tithe total was 0
     */
    public static long forceApplyTitheNow(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        boolean requireCompleteTownHall,
        boolean ignoreAlreadyCollectedThisCalendarDay
    ) {
        if (requireCompleteTownHall
            && town.findCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL) == null) {
            return -1L;
        }
        long titheDay = VillagerReputationService.currentGameEpochDay(store);
        if (!ignoreAlreadyCollectedThisCalendarDay) {
            Long last = town.getTreasuryLastTaxEpochDay();
            if (last != null && last >= titheDay) {
                return -2L;
            }
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        TaxMorningBreakdown breakdown = computeTaxMorningBreakdown(town, store, cfg);
        long added = breakdown.finalTotal();
        if (added > 0L) {
            town.addTreasuryGoldCoins(added);
            town.setTreasuryLastTaxEpochDay(titheDay);
            tm.updateTown(town);
        }
        return added;
    }

    @Nonnull
    public static TaxMorningBreakdown computeTaxMorningBreakdown(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        int morningStart = cfg.getGameMorningStartHour();
        int morningEndEx = cfg.getGameMorningEndHourExclusive();
        boolean morning = wtr != null && AetherhavenMorningWindow.isGameMorning(wtr, morningStart, morningEndEx);
        long dawnDay = VillagerReputationService.currentGameEpochDay(store);
        Long last = town.getTreasuryLastTaxEpochDay();
        boolean hall = town.findCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL) != null;
        boolean wouldCollect = hall && (last == null || last < dawnDay);

        int maxPer = cfg.getTreasuryMaxGoldTaxPerVillagerPerDay();
        CharterTaxPolicy policy = town.getCharterTaxPolicyEnum();
        String policyId = town.getCharterTaxPolicy();
        if (policyId != null && policyId.isBlank()) {
            policyId = null;
        }
        double flatFrac = cfg.getCharterTaxPerCapitaFlatFraction();
        double exp = cfg.getCharterTaxHappinessExponent();

        List<VillagerTaxLine> lines = new ArrayList<>();
        long sum = accumulateResidentTaxLines(town, store, maxPer, policy, flatFrac, exp, lines);

        boolean founder = town.isFounderMonumentActive();
        int founderPm = cfg.getFounderMonumentTaxPermille();
        long afterFounder = founder ? (long) Math.floor(sum * (founderPm / 1000.0)) : sum;

        boolean stewards = FeastService.isStewardsTaxActive(town, dawnDay);
        int feastPm = cfg.getFeastTaxBonusPermille();
        long finalTotal =
            stewards ? (long) Math.floor(afterFounder * (feastPm / 1000.0)) : afterFounder;

        return new TaxMorningBreakdown(
            hall,
            policyId,
            maxPer,
            lines.size(),
            List.copyOf(lines),
            sum,
            founder,
            founderPm,
            afterFounder,
            stewards,
            feastPm,
            finalTotal,
            morning,
            dawnDay,
            last,
            wouldCollect
        );
    }

    private static long accumulateResidentTaxLines(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        int maxPerVillager,
        @Nullable CharterTaxPolicy policy,
        double flatFrac,
        double exp,
        @Nonnull List<VillagerTaxLine> outLines
    ) {
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
                    UUIDComponent id = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    if (id == null) {
                        continue;
                    }
                    String role = npc != null ? npc.getRoleName() : null;
                    String displayName = AetherhavenRoleLabels.listLinePlainEnglish(role, b.getKind());
                    float avg = (needs.getHunger() + needs.getEnergy() + needs.getFun()) / 3f;
                    float ratio = Math.max(0f, Math.min(1f, avg / VillagerNeeds.MAX));
                    double per;
                    if (policy == null) {
                        per = maxPerVillager * ratio;
                    } else if (policy == CharterTaxPolicy.PER_CAPITA) {
                        per = maxPerVillager * (flatFrac + (1.0 - flatFrac) * ratio);
                    } else {
                        per = maxPerVillager * Math.pow(ratio, exp);
                    }
                    long floored = (long) Math.floor(per);
                    sum[0] += floored;
                    outLines.add(
                        new VillagerTaxLine(
                            id.getUuid(),
                            b.getKind(),
                            role,
                            displayName,
                            needs.getHunger(),
                            needs.getEnergy(),
                            needs.getFun(),
                            ratio,
                            floored
                        )
                    );
                }
            }
        );
        return sum[0];
    }
}
