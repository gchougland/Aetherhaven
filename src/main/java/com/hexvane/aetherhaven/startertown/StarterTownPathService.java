package com.hexvane.aetherhaven.startertown;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.pathtool.PathCementService;
import com.hexvane.aetherhaven.pathtool.PathCommitRecord;
import com.hexvane.aetherhaven.pathtool.PathNavPolylineUtil;
import com.hexvane.aetherhaven.pathtool.PathPlannedCell;
import com.hexvane.aetherhaven.pathtool.PathSplineUtil;
import com.hexvane.aetherhaven.pathtool.PathToolNode;
import com.hexvane.aetherhaven.pathtool.PathToolPersistence;
import com.hexvane.aetherhaven.pathtool.PathToolRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

final class StarterTownPathService {
    static final int PATH_WIDTH = 6;

    private StarterTownPathService() {}

    static int build(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull StarterTownLayoutPlan plan
    ) {
        if (plan.buildings().size() < 2) {
            return 0;
        }
        AetherhavenPluginConfig config = plugin.getConfig().get();
        PathToolRegistry registry = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
        Vector3i hub = plan.buildings().get(0).roadPoint();
        Random random = new Random(plan.seed() ^ 0x5DEECE66DL);
        int built = 0;
        for (int i = 1; i < plan.buildings().size(); i++) {
            Vector3i from = "line".equals(plan.layout())
                ? plan.buildings().get(i - 1).roadPoint()
                : hub;
            Vector3i to = plan.buildings().get(i).roadPoint();
            Vector3d direction = new Vector3d(to.x - from.x, 0.0, to.z - from.z);
            double yaw = PathSplineUtil.yawDegFromLookDirection(direction);
            List<PathToolNode> nodes = List.of(
                new PathToolNode(UUID.randomUUID(), new Vector3d(from.x + 0.5, from.y, from.z + 0.5), yaw),
                new PathToolNode(UUID.randomUUID(), new Vector3d(to.x + 0.5, to.y, to.z + 0.5), yaw)
            );
            List<PathSplineUtil.PathSample> samples = PathSplineUtil.sample(nodes, 2);
            List<PathPlannedCell.Planned> cells = PathPlannedCell.build(
                world,
                samples,
                PATH_WIDTH,
                config.getPathToolRayStartAboveY(),
                config.getPathToolMaxRayDown()
            );
            cells = cells.stream().filter(cell -> !insideAnyBuilding(cell.pos, plan)).toList();
            PathCommitRecord record = PathCementService.tryCement(
                world,
                config,
                cells,
                0,
                PATH_WIDTH,
                random
            );
            if (record == null) {
                continue;
            }
            record.townId = town.getTownId().toString();
            record.navNodes = PathNavPolylineUtil.resampleCenterline(samples, config.getPathNavNodeSpacing());
            if (record.pathWidthBlocks <= 0) {
                record.pathWidthBlocks = PATH_WIDTH;
            }
            registry.addRecord(record);
            built++;
        }
        if (built > 0) {
            AetherhavenWorldRegistries.getOrCreatePathNavGraphService(world).rebuildAll(registry, config);
            PathToolPersistence.save(world, plugin, registry);
        }
        return built;
    }

    private static boolean insideAnyBuilding(
        @Nonnull Vector3i pos,
        @Nonnull StarterTownLayoutPlan plan
    ) {
        for (StarterTownLayoutPlan.Building building : plan.buildings()) {
            PlotFootprintRecord footprint = building.footprint();
            if (pos.x >= footprint.getMinX()
                && pos.x <= footprint.getMaxX()
                && pos.z >= footprint.getMinZ()
                && pos.z <= footprint.getMaxZ()) {
                return true;
            }
        }
        return false;
    }
}
