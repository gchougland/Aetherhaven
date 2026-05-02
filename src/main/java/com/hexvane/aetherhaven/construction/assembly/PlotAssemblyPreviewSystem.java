package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.pathtool.PathDebugPreviewUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Per-player {@link PathDebugPreviewUtil} ghost cubes for the next assembly cell (same approach as the path tool’s
 * planned voxels — no world blocks).
 */
public final class PlotAssemblyPreviewSystem extends EntityTickingSystem<EntityStore> {
    private static final double VIZ_RANGE = 96.0;
    private static final double VIZ_RANGE_SQ = VIZ_RANGE * VIZ_RANGE;
    /** Same family as path-tool “replaceable” tint, shifted green. */
    private static final Vector3f NEXT_CELL_COLOR = new Vector3f(0.22f, 0.92f, 0.48f);

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public PlotAssemblyPreviewSystem(@Nonnull AetherhavenPlugin plugin) {
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
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        World world = store.getExternalData().getWorld();
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return;
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, ref);
        if (hand != null
            && !hand.isEmpty()
            && AetherhavenConstants.PATH_TOOL_ITEM_ID.equals(hand.getItemId())) {
            PathDebugPreviewUtil.clear(pr);
            return;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d ppos = tc.getPosition();
        List<PlotAssemblyJob> jobs = new ArrayList<>(AssemblyWorldRegistry.jobs(world));
        jobs.sort(Comparator.comparing(PlotAssemblyJob::plotId));
        List<Vector3i> cellsInRange = new ArrayList<>();
        for (PlotAssemblyJob job : jobs) {
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, p).findTownOwningPlot(job.plotId());
            if (town == null) {
                continue;
            }
            PlotInstance plot = town.findPlotById(job.plotId());
            if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            Vector3i cell = PlotAssemblyService.previewCellWorld(job, plot);
            if (cell == null) {
                continue;
            }
            double cx = cell.x + 0.5;
            double cy = cell.y + 0.5;
            double cz = cell.z + 0.5;
            double dx = cx - ppos.getX();
            double dy = cy - ppos.getY();
            double dz = cz - ppos.getZ();
            if (dx * dx + dy * dy + dz * dz > VIZ_RANGE_SQ) {
                continue;
            }
            boolean duplicate = false;
            for (Vector3i c : cellsInRange) {
                if (c.equals(cell)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                continue;
            }
            cellsInRange.add(cell);
        }
        if (cellsInRange.isEmpty()) {
            PathDebugPreviewUtil.clear(pr);
            return;
        }
        double pulse01 = (world.getTick() % 72) / 72.0;
        PathDebugPreviewUtil.clear(pr);
        for (Vector3i cell : cellsInRange) {
            PathDebugPreviewUtil.drawAssemblyNextCellCube(pr, cell.x, cell.y, cell.z, NEXT_CELL_COLOR, world, pulse01);
        }
    }
}
