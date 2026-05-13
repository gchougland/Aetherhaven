package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.feast.FeastService;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.CharterTaxPolicy;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
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
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Daily gold tithe into {@link TownRecord} treasury. Invoked from {@link TownEconomyTimeService#onGameTimeFromHub}
 * (wired by {@link com.hexvane.aetherhaven.time.AetherhavenGameTimeBridgeSubscriber}) on the entity-store thread during
 * the configured in-game morning window when the town hall is complete, the dawn-aligned day has not been stamped yet,
 * at least one owner or member player is online in this world, at least one paying resident row exists (loaded NPCs
 * and/or roster snapshots with last-known needs when those villagers are not loaded), and the tithe has been computed.
 * {@link VillagerReputationService#currentGameEpochDay} matches reputation and other dawn-based dailies (not raw calendar midnight).
 */
public final class TownTaxService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Persisted {@link TownRecord#getTreasuryLastTaxEpochDay()} values far below any realistic dawn id (e.g. corrupt
     * saves or pre-migration quirks) are ignored so collection can resume.
     */
    private static final long STALE_TREASURY_LAST_TAX_DAY = -200_000L;

    private TownTaxService() {}

    /** One resident row counted during a tax scan (simulated NPC and/or roster-only row). */
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
     * Full morning tax snapshot for debugging. {@link #taxResidentRowCount()} is {@code lines().size()}.
     * {@link #simulatedResidentEntityCount()} is paying residents currently in loaded chunks; automatic tithe runs when
     * {@link #taxResidentRowCount()} is positive (rows can be roster-only).
     */
    public record TaxMorningBreakdown(
        boolean townHallComplete,
        @Nullable String taxPolicyId,
        int maxGoldPerResidentPerDay,
        int taxResidentRowCount,
        int simulatedResidentEntityCount,
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
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        int morningStart = cfg.getGameMorningStartHour();
        int morningEndEx = cfg.getGameMorningEndHourExclusive();
        if (!AetherhavenMorningWindow.isGameMorning(wtr, morningStart, morningEndEx)) {
            return;
        }

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        long titheDay = VillagerReputationService.currentGameEpochDay(store);
        long todayGameLocalEpochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        Set<UUID> onlinePlayers = collectOnlinePlayerUuids(store);

        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            PlotInstance hall = town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL);
            if (hall == null) {
                continue;
            }
            if (!anyAffiliatedTownPlayerOnline(town, onlinePlayers)) {
                continue;
            }
            Long lastRaw = town.getTreasuryLastTaxEpochDay();
            Long last = sanitizeTreasuryLastTaxDay(lastRaw, titheDay);
            if (last != null && last >= titheDay) {
                continue;
            }
            Long lastCal = town.getTreasuryLastTaxGameLocalDateEpochDay();
            if (lastCal != null && lastCal == todayGameLocalEpochDay) {
                continue;
            }
            TaxMorningBreakdown breakdown = computeTaxMorningBreakdown(town, store, cfg, plugin.getConstructionCatalog());
            if (breakdown.taxResidentRowCount() <= 0) {
                continue;
            }
            long added = breakdown.finalTotal();
            town.setTreasuryLastTaxEpochDay(titheDay);
            town.setTreasuryLastTaxGameLocalDateEpochDay(todayGameLocalEpochDay);
            if (added > 0L) {
                town.addTreasuryGoldCoins(added);
            }
            tm.updateTown(town);
            if (added > 0L) {
                notifyTownTaxCollected(store, town, added);
                LOGGER.atInfo().log(
                    "Aetherhaven treasury tithe applied: world=%s townId=%s +%d gold dawnDay=%d gameDateTime=%s residentRows=%d simulated=%d",
                    world.getName(),
                    town.getTownId(),
                    added,
                    titheDay,
                    wtr.getGameDateTime(),
                    breakdown.taxResidentRowCount(),
                    breakdown.simulatedResidentEntityCount()
                );
            } else {
                LOGGER.atFine().log(
                    "Aetherhaven treasury tithe stamped (0 gold): world=%s townId=%s dawnDay=%d residentRows=%d simulated=%d",
                    world.getName(),
                    town.getTownId(),
                    titheDay,
                    breakdown.taxResidentRowCount(),
                    breakdown.simulatedResidentEntityCount()
                );
            }
        }
    }

    /**
     * Debug: apply the current morning tithe math to this town, credit the treasury, and set last-collected day, without
     * requiring the in-game morning window. Does not add gold if the final total is 0 (e.g. no resident tax rows).
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
            && town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL) == null) {
            return -1L;
        }
        long titheDay = VillagerReputationService.currentGameEpochDay(store);
        if (!ignoreAlreadyCollectedThisCalendarDay) {
            Long last = sanitizeTreasuryLastTaxDay(town.getTreasuryLastTaxEpochDay(), titheDay);
            if (last != null && last >= titheDay) {
                return -2L;
            }
            WorldTimeResource wtrForce = store.getResource(WorldTimeResource.getResourceType());
            if (wtrForce != null) {
                long cal = wtrForce.getGameDateTime().toLocalDate().toEpochDay();
                Long lastCal = town.getTreasuryLastTaxGameLocalDateEpochDay();
                if (lastCal != null && lastCal == cal) {
                    return -2L;
                }
            }
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        TaxMorningBreakdown breakdown = computeTaxMorningBreakdown(town, store, cfg, plugin.getConstructionCatalog());
        long added = breakdown.finalTotal();
        if (added > 0L) {
            town.addTreasuryGoldCoins(added);
            town.setTreasuryLastTaxEpochDay(titheDay);
            WorldTimeResource wtrStamp = store.getResource(WorldTimeResource.getResourceType());
            if (wtrStamp != null) {
                town.setTreasuryLastTaxGameLocalDateEpochDay(wtrStamp.getGameDateTime().toLocalDate().toEpochDay());
            }
            tm.updateTown(town);
        }
        return added;
    }

    @Nonnull
    public static TaxMorningBreakdown computeTaxMorningBreakdown(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        int morningStart = cfg.getGameMorningStartHour();
        int morningEndEx = cfg.getGameMorningEndHourExclusive();
        boolean morning = wtr != null && AetherhavenMorningWindow.isGameMorning(wtr, morningStart, morningEndEx);
        long dawnDay = VillagerReputationService.currentGameEpochDay(store);
        Long lastRaw = town.getTreasuryLastTaxEpochDay();
        Long lastSanitized = sanitizeTreasuryLastTaxDay(lastRaw, dawnDay);
        Long lastTaxCal = town.getTreasuryLastTaxGameLocalDateEpochDay();
        boolean alreadyThisGameCalendarDay =
            wtr != null && lastTaxCal != null && lastTaxCal == wtr.getGameDateTime().toLocalDate().toEpochDay();
        boolean hall = town.findCompletePlotWithConstruction(constructionCatalog, AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL) != null;
        Set<UUID> onlinePlayers = collectOnlinePlayerUuids(store);
        boolean anyMember = anyAffiliatedTownPlayerOnline(town, onlinePlayers);

        int maxPer = cfg.getTreasuryMaxGoldTaxPerVillagerPerDay();
        CharterTaxPolicy policy = town.getCharterTaxPolicyEnum();
        String policyId = town.getCharterTaxPolicy();
        if (policyId != null && policyId.isBlank()) {
            policyId = null;
        }

        List<VillagerTaxLine> lines = new ArrayList<>();
        int[] simulatedCount = new int[1];
        long sum = accumulateResidentTaxLines(town, store, maxPer, cfg, policy, lines, simulatedCount);
        int simulated = simulatedCount[0];
        int taxRows = lines.size();
        boolean wouldCollect =
            hall
                && (lastSanitized == null || lastSanitized < dawnDay)
                && !alreadyThisGameCalendarDay
                && morning
                && anyMember
                && taxRows > 0;

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
            taxRows,
            simulated,
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
            lastRaw,
            wouldCollect
        );
    }

    private static long accumulateResidentTaxLines(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        int maxPerVillager,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nullable CharterTaxPolicy policy,
        @Nonnull List<VillagerTaxLine> outLines,
        @Nonnull int[] simulatedResidentEntityCountOut
    ) {
        UUID tid = town.getTownId();
        long[] sum = new long[1];
        Set<UUID> counted = new HashSet<>();
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
                    UUID entityUuid = id.getUuid();
                    counted.add(entityUuid);
                    simulatedResidentEntityCountOut[0]++;
                    String role = npc != null ? npc.getRoleName() : null;
                    String displayName = AetherhavenRoleLabels.listLinePlainEnglish(role, b.getKind());
                    float avg = (needs.getHunger() + needs.getEnergy() + needs.getFun()) / 3f;
                    float ratio = Math.max(0f, Math.min(1f, avg / VillagerNeeds.MAX));
                    long floored = contributionGold(maxPerVillager, cfg, policy, ratio);
                    sum[0] += floored;
                    outLines.add(
                        new VillagerTaxLine(
                            entityUuid,
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
        appendOfflineResidentTaxFromRecords(town, maxPerVillager, cfg, policy, counted, outLines, sum);
        return sum[0];
    }

    private static void appendOfflineResidentTaxFromRecords(
        @Nonnull TownRecord town,
        int maxPerVillager,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nullable CharterTaxPolicy policy,
        @Nonnull Set<UUID> countedEntityIds,
        @Nonnull List<VillagerTaxLine> outLines,
        @Nonnull long[] sum
    ) {
        UUID nil = new UUID(0L, 0L);
        for (ResidentNpcRecord rec : town.getResidentNpcRecords()) {
            if (rec == null) {
                continue;
            }
            if (TownVillagerBinding.isVisitorKind(rec.getKind())) {
                continue;
            }
            UUID id = rec.getLastEntityUuid();
            if (nil.equals(id) || countedEntityIds.contains(id)) {
                continue;
            }
            if (!rec.hasLastKnownNeeds()) {
                continue;
            }
            countedEntityIds.add(id);
            float h = rec.getLastKnownHunger();
            float e = rec.getLastKnownEnergy();
            float f = rec.getLastKnownFun();
            float avg = (h + e + f) / 3f;
            float ratio = Math.max(0f, Math.min(1f, avg / VillagerNeeds.MAX));
            long floored = contributionGold(maxPerVillager, cfg, policy, ratio);
            sum[0] += floored;
            String roleId = rec.getNpcRoleId();
            String role = roleId != null && !roleId.isBlank() ? roleId : null;
            String displayName = AetherhavenRoleLabels.listLinePlainEnglish(role, rec.getKind());
            outLines.add(
                new VillagerTaxLine(
                    id,
                    rec.getKind(),
                    role,
                    displayName,
                    h,
                    e,
                    f,
                    ratio,
                    floored
                )
            );
        }
    }

    private static long contributionGold(
        int treasuryMaxPerResident,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nullable CharterTaxPolicy policy,
        float ratio
    ) {
        double r = Math.max(0.0, Math.min(1.0, ratio));
        if (policy == null) {
            return (long) Math.floor(treasuryMaxPerResident * r);
        }
        if (policy == CharterTaxPolicy.PER_CAPITA) {
            int mn = cfg.getCharterPerCapitaMinGoldPerResidentPerDay();
            int mx = cfg.getCharterPerCapitaMaxGoldPerResidentPerDay();
            double per = mn + (mx - mn) * r;
            return (long) Math.floor(per);
        }
        // HAPPINESS_WEIGHTED: no gold at or below comfort threshold; curved rise to peak at full needs.
        double thr = cfg.getCharterHappinessTaxMinComfortRatio();
        if (r <= thr) {
            return 0L;
        }
        double span = 1.0 - thr;
        if (span <= 1.0e-6) {
            return 0L;
        }
        double t = (r - thr) / span;
        double curved = smoothstep01(t);
        double peak = treasuryMaxPerResident * (cfg.getCharterHappinessTaxPeakPermille() / 1000.0);
        return (long) Math.floor(peak * curved);
    }

    /** Hermite smoothstep on [0,1] for a soft curve (zero first derivative at endpoints). */
    private static double smoothstep01(double t) {
        if (t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }

    @Nullable
    private static Long sanitizeTreasuryLastTaxDay(@Nullable Long raw, long currentTitheDay) {
        if (raw == null) {
            return null;
        }
        if (raw < STALE_TREASURY_LAST_TAX_DAY) {
            return null;
        }
        if (raw > currentTitheDay + 5000L) {
            return null;
        }
        return raw;
    }

    @Nonnull
    private static Set<UUID> collectOnlinePlayerUuids(@Nonnull Store<EntityStore> store) {
        Query<EntityStore> q = Query.and(Player.getComponentType(), UUIDComponent.getComponentType());
        Set<UUID> out = new HashSet<>();
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc != null) {
                        out.add(uc.getUuid());
                    }
                }
            }
        );
        return out;
    }

    private static boolean anyAffiliatedTownPlayerOnline(@Nonnull TownRecord town, @Nonnull Set<UUID> onlinePlayers) {
        for (UUID u : onlinePlayers) {
            if (town.hasMemberOrOwner(u)) {
                return true;
            }
        }
        return false;
    }

    private static void notifyTownTaxCollected(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town, long goldAdded) {
        Message msg =
            Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.notificationTaxCollected").param("amount", Long.toString(goldAdded));
        Query<EntityStore> q = Query.and(Player.getComponentType(), UUIDComponent.getComponentType(), PlayerRef.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                    if (uc == null || pr == null) {
                        continue;
                    }
                    if (!town.hasMemberOrOwner(uc.getUuid())) {
                        continue;
                    }
                    NotificationUtil.sendNotification(pr.getPacketHandler(), msg, NotificationStyle.Success);
                }
            }
        );
    }
}
