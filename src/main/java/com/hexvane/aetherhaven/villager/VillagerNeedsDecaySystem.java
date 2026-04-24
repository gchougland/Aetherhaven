package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.feast.FeastService;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
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
import java.util.Set;
import javax.annotation.Nonnull;

public final class VillagerNeedsDecaySystem extends EntityTickingSystem<EntityStore> {
    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public VillagerNeedsDecaySystem(@Nonnull AetherhavenPlugin plugin) {
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
        return VillagerNeeds.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        VillagerNeeds needs = archetypeChunk.getComponent(index, VillagerNeeds.getComponentType());
        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        if (needs == null || binding == null) {
            return;
        }
        long nowMs = resolveNowMs(store);
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        float rate = cfg.getVillagerNeedsDecayPerSecond();
        if (!TownVillagerBinding.isVisitorKind(binding.getKind())) {
            World world = store.getExternalData().getWorld();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.getTown(binding.getTownId());
            if (town != null) {
                long dawn = VillagerReputationService.currentGameEpochDay(store);
                if (FeastService.isHearthglassDecayActive(town, dawn)) {
                    rate *= cfg.getFeastNeedsDecayScalePermille() / 1000f;
                }
            }
        }
        needs.applyDecay(nowMs, rate);
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        commandBuffer.putComponent(entityRef, VillagerNeeds.getComponentType(), needs);
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
