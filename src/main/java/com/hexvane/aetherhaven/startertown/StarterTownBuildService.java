package com.hexvane.aetherhaven.startertown;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCompleter;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Ordered world-thread orchestration for instant starter-town construction. */
public final class StarterTownBuildService {
    public record Result(int buildings, int paths, int completedQuests, int villagers, int skippedVillagers) {}

    private StarterTownBuildService() {}

    public static void build(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID ownerUuid,
        @Nonnull UUID townId,
        @Nonnull StarterTownLayoutPlan plan,
        @Nonnull Consumer<Result> onComplete,
        @Nonnull Consumer<String> onFailure
    ) {
        world.execute(
            () -> buildNext(world, plugin, ownerUuid, townId, plan, 0, onComplete, onFailure)
        );
    }

    private static void buildNext(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID ownerUuid,
        @Nonnull UUID townId,
        @Nonnull StarterTownLayoutPlan plan,
        int index,
        @Nonnull Consumer<Result> onComplete,
        @Nonnull Consumer<String> onFailure
    ) {
        if (!world.isAlive()) {
            onFailure.accept("The world unloaded before starter-town construction completed.");
            return;
        }
        TownManager townManager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townManager.getTown(townId);
        if (town == null) {
            onFailure.accept("The target town no longer exists.");
            return;
        }
        if (index >= plan.buildings().size()) {
            int paths = StarterTownPathService.build(world, plugin, town, plan);
            int completedQuests =
                StarterTownQuestService.completeBuildingQuests(plugin, town, townManager, plan);
            StarterTownVillagerProvisioner.Result villagers =
                StarterTownVillagerProvisioner.provision(world, plugin, town, townManager, plan);
            onComplete.accept(
                new Result(
                    plan.buildings().size(),
                    paths,
                    completedQuests,
                    villagers.spawned(),
                    villagers.skipped()
                )
            );
            return;
        }

        StarterTownLayoutPlan.Building building = plan.buildings().get(index);
        ConstructionDefinition definition = plugin.getConstructionCatalog().get(building.constructionId());
        IPrefabBuffer buffer = definition != null
            ? PrefabResolveUtil.resolvePrefabBuffer(definition.getPrefabPath())
            : null;
        if (definition == null || buffer == null) {
            onFailure.accept("Prefab became unavailable for " + building.constructionId() + ".");
            return;
        }
        UUID plotId = UUID.randomUUID();
        Vector3i sign = definition.resolvePreviewSignAnchorWorld(building.prefabAnchor(), building.yaw());
        PlotInstance plot = new PlotInstance(
            plotId,
            building.constructionId(),
            PlotInstanceState.ASSEMBLING,
            building.footprint(),
            sign.x,
            sign.y,
            sign.z,
            System.currentTimeMillis()
        );
        plot.setPrefabWorldPlacement(
            building.prefabAnchor().x,
            building.prefabAnchor().y,
            building.prefabAnchor().z,
            building.yaw()
        );
        town.addPlotInstance(plot);
        townManager.updateTown(town);

        ConstructionAnimator.start(
            plugin,
            world,
            building.prefabAnchor(),
            building.yaw(),
            true,
            buffer,
            world.getEntityStore().getStore(),
            Integer.MAX_VALUE,
            1L,
            () -> {
                ConstructionCompleter.finishBuild(
                    world,
                    plugin,
                    ownerUuid,
                    plotId,
                    building.prefabAnchor(),
                    building.yaw()
                );
                world.execute(
                    () -> buildNext(
                        world,
                        plugin,
                        ownerUuid,
                        townId,
                        plan,
                        index + 1,
                        onComplete,
                        onFailure
                    )
                );
            }
        );
    }
}
