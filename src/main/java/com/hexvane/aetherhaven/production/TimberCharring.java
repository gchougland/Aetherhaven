package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;

/** Attended conversion of local, unreserved logs. No timer or catch-up output. */
public final class TimberCharring {
    public static final String OUTPUT = "Wood_Burnt_Trunk";
    public static final int LOG_COST = 5;
    public static final double WORK_SECONDS = 15;
    private TimberCharring() {}

    /** Charring is a simple choice at the lumbermill, off by default. */
    public static boolean enabled(PlotProductionState production) {
        return production.usesPhysicalTimber() && production.timber().charWood() != null;
    }

    public static String input(PlotProductionState production, ProductionCatalog.Entry entry,
                               TownWarehouse stock, UUID plot) {
        if (!ready(production, entry)) return null;
        // Only the wood the player chose is ever charred. Other stock and rare logs are never swept.
        String chosen = production.timber().charWood();
        return available(production, entry, stock, plot, chosen) ? chosen : null;
    }

    private static boolean ready(PlotProductionState production, ProductionCatalog.Entry entry) {
        return enabled(production) && production.timber().cargo() == null
            && production.getAmount(OUTPUT) < WorkplaceProductionUpgrades.effectiveMaxStorage(production, entry, OUTPUT);
    }

    public static boolean available(PlotProductionState production, ProductionCatalog.Entry entry,
                                    TownWarehouse stock, UUID plot, String item) {
        return item != null && !OUTPUT.equals(item) && item.equals(production.timber().charWood())
            && NativeTimberGrowth.species(item) != null
            && production.getAmount(item) - stock.reservedProduction(plot, item) >= LOG_COST;
    }

    /** Called on the world thread after rechecking worker attendance, heat and Mastery. */
    public static boolean process(PlotProductionState production, ProductionCatalog.Entry entry,
                                  TownWarehouse stock, UUID plot, String expectedInput, TimberHarvestPlan.Position source) {
        if (!ready(production, entry) || !available(production, entry, stock, plot, expectedInput)) return false;
        return production.timber().charLogs(source,
            () -> production.withdrawAll(java.util.Map.of(expectedInput, (long) LOG_COST)));
    }

    /** A nearby player-fueled native campfire provides heat without touching its inventories. */
    public static WarehouseBatch.Point findHeat(World world, PlotInstance plot, WarehouseBatch.Point storage) {
        if (storage == null) return null;
        for (int y = storage.y() - 1; y <= storage.y() + 1; y++)
            for (int x = storage.x() - 2; x <= storage.x() + 2; x++)
                for (int z = storage.z() - 2; z <= storage.z() + 2; z++) {
                    var point = new WarehouseBatch.Point(x, y, z);
                    if (heatAvailable(world, plot, storage, point)) return point;
                }
        return null;
    }

    public static boolean heatAvailable(World world, PlotInstance plot, WarehouseBatch.Point storage, WarehouseBatch.Point heat) {
        if (storage == null || heat == null || Math.abs(storage.x() - heat.x()) > 2
            || Math.abs(storage.z() - heat.z()) > 2 || Math.abs(storage.y() - heat.y()) > 1
            || !plot.containsWorldBlock(heat.x(), heat.y(), heat.z())
            || ChunkSectionBlockUtil.worldChunkIfTicking(world, ChunkUtil.indexChunk(
                ChunkUtil.chunkCoordinate(heat.x()), ChunkUtil.chunkCoordinate(heat.z()))) == null) return false;
        var ref = ChunkSectionBlockUtil.blockEntityRefAt(world, heat.x(), heat.y(), heat.z());
        if (ref == null || !ref.isValid()) return false;
        var bench = world.getChunkStore().getStore().getComponent(ref, ProcessingBenchBlock.getComponentType());
        return bench != null && bench.isActive() && bench.getFuelTime() > 0 && "Campfire".equals(bench.getProcessingBench().getId());
    }
}
