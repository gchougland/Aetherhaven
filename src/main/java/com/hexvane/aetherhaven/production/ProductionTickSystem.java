package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * While a farmer, miner, logger, or rancher is in autonomy {@link VillagerAutonomyState#PHASE_USE} at their job plot
 * WORK POI,
 * advances per-plot production into {@link TownRecord} storage.
 */
public final class ProductionTickSystem extends EntityTickingSystem<EntityStore> {
    private static final ConcurrentHashMap<String, Long> LAST_TOWN_SAVE_MS = new ConcurrentHashMap<>();
    private static final long SAVE_DEBOUNCE_MS = 4000L;

    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public ProductionTickSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(TownVillagerBinding.getComponentType(), VillagerAutonomyState.getComponentType(), NPCEntity.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        VillagerAutonomyState autonomy = archetypeChunk.getComponent(index, VillagerAutonomyState.getComponentType());
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (binding == null || autonomy == null || npc == null || TownVillagerBinding.isVisitorKind(binding.getKind())) {
            return;
        }
        String kind = binding.getKind();
        String expectedConstruction = expectedConstructionForKind(kind);
        if (expectedConstruction == null) {
            return;
        }
        if (autonomy.getPhase() != VillagerAutonomyState.PHASE_USE) {
            return;
        }
        long now = resolveNowMs(store);
        if (now >= autonomy.getPhaseEndEpochMs()) {
            return;
        }
        UUID jobPlotId = binding.getJobPlotId();
        if (jobPlotId == null) {
            return;
        }
        UUID poiUuid = autonomy.getTargetPoiUuid();
        if (poiUuid == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PoiEntry poi = reg.get(poiUuid);
        if (poi == null || !isWorkPoi(poi)) {
            return;
        }
        if (!Objects.equals(poi.getPlotId(), jobPlotId)) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            return;
        }
        PlotInstance plot = town.findPlotById(jobPlotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return;
        }
        String cid = plot.getConstructionId();
        if (!expectedConstruction.equals(cid)) {
            return;
        }
        String roleId = npc.getRoleName();
        if (roleId == null || roleId.isBlank()) {
            return;
        }
        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(roleId.trim());
        if (vdef == null) {
            return;
        }
        String workConstructionId = vdef.getWorkConstructionId();
        if (workConstructionId == null || !workConstructionId.equals(cid)) {
            return;
        }
        ProductionCatalog catalog = plugin.getProductionCatalog();
        ProductionCatalog.Entry entry = catalog.get(cid);
        if (entry == null || entry.catalogSize() <= 0) {
            return;
        }

        PlotProductionState state = town.getOrCreatePlotProduction(jobPlotId);
        state.migrateIfNeeded();
        int ticksPer = entry.ticksPerUnit();
        int acc = state.getTickAccum() + 1;
        if (acc < ticksPer) {
            state.setTickAccum(acc);
            return;
        }
        state.setTickAccum(0);

        boolean changed = false;
        for (int slot = 0; slot < 3; slot++) {
            String selected = entry.itemAtCursor(state.getSlotCursor(slot));
            if (selected == null || selected.isBlank()) {
                continue;
            }
            int mult = 0;
            for (int j = 0; j < 3; j++) {
                String other = entry.itemAtCursor(state.getSlotCursor(j));
                if (selected.equals(other)) {
                    mult++;
                }
            }
            if (mult > 0) {
                state.addAmount(selected, mult);
                changed = true;
            }
        }
        if (changed) {
            maybePersistTown(tm, town, world, now);
        }
    }

    private static void maybePersistTown(@Nonnull TownManager tm, @Nonnull TownRecord town, @Nonnull World world, long nowMs) {
        String key = world.getName() + "|" + town.getTownId();
        Long last = LAST_TOWN_SAVE_MS.get(key);
        if (last != null && nowMs - last < SAVE_DEBOUNCE_MS) {
            return;
        }
        LAST_TOWN_SAVE_MS.put(key, nowMs);
        tm.updateTown(town);
    }

    private static boolean isWorkPoi(@Nonnull PoiEntry poi) {
        return poi.getTags().contains("WORK") || poi.getInteractionKind() == PoiInteractionKind.WORK_SURFACE;
    }

    @Nullable
    private static String expectedConstructionForKind(@Nonnull String kind) {
        return switch (kind) {
            case TownVillagerBinding.KIND_FARMER -> com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_FARM;
            case TownVillagerBinding.KIND_MINER -> com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT;
            case TownVillagerBinding.KIND_LOGGER -> com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL;
            case TownVillagerBinding.KIND_RANCHER -> com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_BARN;
            default -> null;
        };
    }

    private static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        TimeModule mod = TimeModule.get();
        if (mod != null) {
            TimeResource tr = store.getResource(mod.getTimeResourceType());
            if (tr != null) {
                return tr.getNow().toEpochMilli();
            }
        }
        return System.currentTimeMillis();
    }
}
