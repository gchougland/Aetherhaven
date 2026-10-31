package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.tool.PoiDebugLineHelper;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.workplace.GrovePage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.Matrix4d;
import org.joml.Vector3f;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/** Player-only, short-lived packets: turning this off never clears another tool's overlays. */
public final class RootstoneGrowingSpaceOverlay extends TickingSystem<EntityStore> {
    private record Selection(UUID town, UUID plot, TimberPlantingReceipt tree) {}
    private static final Map<World, Map<UUID, Selection>> SELECTIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<World, Long> next = Collections.synchronizedMap(new WeakHashMap<>());
    private static final float LIFETIME = 1.25f;

    private static Map<UUID, Selection> selections(World world) {
        synchronized (SELECTIONS) { return SELECTIONS.computeIfAbsent(world, ignored -> new HashMap<>()); }
    }
    public static boolean enabled(World world, UUID player) { return selections(world).containsKey(player); }
    public static void show(World world, UUID player, UUID town, UUID plot, TimberPlantingReceipt tree) {
        selections(world).put(player, new Selection(town, plot, tree));
    }
    public static void hide(World world, UUID player) { selections(world).remove(player); }
    public static TimberPlantingReceipt selected(World world, UUID player, UUID plot, Position stone) {
        var selection = selections(world).get(player);
        if (selection == null || stone == null || !selection.plot().equals(plot)) return null;
        var root = selection.tree().root();
        return root.x() == stone.x() && root.y() == stone.y()+1 && root.z() == stone.z() ? selection.tree() : null;
    }

    @Override public void tick(float dt, int index, Store<EntityStore> store) {
        var world = store.getExternalData().getWorld();
        if (selections(world).isEmpty()) return;
        long now = System.nanoTime();
        synchronized (next) { if (now < next.getOrDefault(world, 0L)) return; next.put(world, now + 1_000_000_000L); }
        world.execute(() -> {
            if (!world.isAlive() || store.isShutdown()) return;
            var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
            var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            var absent = new HashSet<>(selections(world).keySet());
            store.forEachChunk(Query.and(PlayerRef.getComponentType(), TransformComponent.getComponentType()), (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    var player = chunk.getComponent(i, PlayerRef.getComponentType());
                    var selected = selections(world).get(player.getUuid());
                    if (selected == null) continue;
                    absent.remove(player.getUuid());
                    var town = manager.getTown(selected.town());
                    var pos = chunk.getComponent(i, TransformComponent.getComponentType()).getPosition();
                    var root = selected.tree().root();
                    if (town == null || !town.playerCanManageConstructions(player.getUuid())
                            || town.findPlotById(selected.plot()) == null
                            || Math.abs(pos.x-root.x()) > 64 || Math.abs(pos.y-root.y()) > 64 || Math.abs(pos.z-root.z()) > 64) {
                        hide(world, player.getUuid()); continue;
                    }
                    var production = town.peekPlotProduction(selected.plot());
                    var below = new Position(root.x(), root.y()-1, root.z());
                    int stone = production == null ? -1 : production.timber().stoneAt(below);
                    var tree = stone >= 0 ? production.timber().groves().get(stone) : selected.tree();
                    if (tree == null) tree = selected.tree();
                    boolean planted = stone >= 0 && production.timber().trees().containsKey(stone);
                    var reserved = new HashSet<Position>();
                    for (var plot : town.getPlotInstances()) {
                        var other = town.peekPlotProduction(plot.getPlotId()); if (other == null) continue;
                        for (var bound : other.timber().groves().entrySet()) {
                            if (plot.getPlotId().equals(selected.plot()) && bound.getKey() == stone) continue;
                            reserved.addAll(TimberGrowingSpace.cells(bound.getValue()));
                        }
                    }
                    box(player, TimberGrowingSpace.bounds(tree), DebugUtils.COLOR_CYAN);
                    box(player, TimberGrowingSpace.marker(below), DebugUtils.COLOR_YELLOW);
                    var blocked = TimberGrowingSpace.obstructions(tree, planted, GrovePage.bounds(town), p -> NativeTimberPlanting.observe(world, p), reserved);
                    // Bound packet volume for a tree placed inside a mountain; clearing reveals the next nearest blockers.
                    blocked.stream().sorted(Comparator.comparingDouble(p -> Math.pow(p.x()-pos.x,2)+Math.pow(p.y()-pos.y,2)+Math.pow(p.z()-pos.z,2)))
                        .limit(256).forEach(p -> tint(player, TimberGrowingSpace.marker(p)));
                }
            });
            absent.forEach(selections(world)::remove);
        });
    }
    private static void tint(PlayerRef player, TimberGrowingSpace.Box b) {
        var matrix = new Matrix4d().translate((b.minX()+b.maxX())/2, (b.minY()+b.maxY())/2, (b.minZ()+b.maxZ())/2)
            .scale(b.maxX()-b.minX(), b.maxY()-b.minY(), b.maxZ()-b.minZ());
        player.getPacketHandler().write(new DisplayDebug(DebugShape.Cube, Matrix4dUtil.asFloatData(matrix),
            DebugUtils.COLOR_RED, LIFETIME, (byte)DebugUtils.FLAG_NO_WIREFRAME, null, .38f));
    }
    private static void box(PlayerRef player, TimberGrowingSpace.Box b, Vector3f color) {
        double[] x = {b.minX(), b.maxX()}, y = {b.minY(), b.maxY()}, z = {b.minZ(), b.maxZ()};
        for (int a = 0; a < 2; a++) for (int c = 0; c < 2; c++) {
            line(player, x[0], y[a], z[c], x[1], y[a], z[c], color);
            line(player, x[a], y[0], z[c], x[a], y[1], z[c], color);
            line(player, x[a], y[c], z[0], x[a], y[c], z[1], color);
        }
    }
    private static void line(PlayerRef player, double x, double y, double z, double ex, double ey, double ez, Vector3f color) {
        PoiDebugLineHelper.addLineToPlayer(player, x, y, z, ex, ey, ez, color, .045, LIFETIME, DebugUtils.FLAG_NONE);
    }
}
