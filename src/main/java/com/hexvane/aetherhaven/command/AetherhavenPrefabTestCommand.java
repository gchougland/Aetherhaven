package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.poi.PoiDualCellNormalize;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Reads the shipped specialist buildings through the native prefab and hitbox loaders. No world writes. */
final class AetherhavenPrefabTestCommand extends AbstractAsyncCommand {
    AetherhavenPrefabTestCommand() {
        super("prefabs", "Check specialist building furniture in all four rotations");
        setPermissionGroups("hytale:WorldEditor");
    }

    private record Cell(int x, int y, int z) {}

    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var failures = new ArrayList<String>();
        int buildings = 0, rotations = 0, fillers = 0;
        var catalog = AetherhavenPlugin.get().getConstructionCatalog();
        for (String kind : List.of("warehouse", "alchemist", "druid", "doctor", "monster_hunter", "crafter", "leatherworker", "simple_workplace")) {
                String id = "plot_" + kind;
                if (catalog.get("plot_stormwind_" + kind) != null)
                    failures.add(id + ": retired Stormwind version is still registered");
                var definition = catalog.get(id);
                if (definition == null) { failures.add(id + ": missing building definition"); continue; }
                if (!catalog.matchesGameplayConstruction(id, "plot_" + kind))
                    failures.add(id + ": does not satisfy the core construction requirement");
                var path = PrefabResolveUtil.resolvePrefabPath(definition.getPrefabPath());
                if (path == null) { failures.add(id + ": missing prefab"); continue; }
                var prefab = PrefabBufferUtil.getCached(path);
                buildings++;
                for (var yaw : Rotation.NORMAL) {
                    rotations++;
                    var cells = new HashMap<Cell, ConstructionPasteOps.PendingBlock>();
                    for (var block : ConstructionPasteOps.buildSequence(prefab, yaw).pendingBlocks())
                        cells.put(new Cell(block.x(), block.y(), block.z()), block);
                    int[] management = definition.getManagementBlockLocalPos();
                    if (management == null && !definition.isDecorationPlot()) failures.add(id + ": missing records shelf location");
                    else if (management != null) {
                        var p = PrefabLocalOffset.rotate(yaw, management[0], management[1], management[2]);
                        var shelf = cells.get(new Cell(p.x, p.y, p.z));
                        if (shelf == null || shelf.filler() != 0
                            || !"Aetherhaven_Management_Block".equals(BlockType.getAssetMap().getAsset(shelf.blockId()).getId()))
                            failures.add(id + "/" + yaw + ": records shelf location does not point to its origin");
                    }
                    if (kind.equals("monster_hunter")) {
                        var beacon = cells.values().stream().filter(b->b.filler()==0
                            &&"Aetherhaven_Hunt_Beacon".equals(BlockType.getAssetMap().getAsset(b.blockId()).getId())).findFirst().orElse(null);
                        if (beacon == null) {
                            failures.add(id + "/" + yaw + ": missing Hunt Beacon");
                            continue;
                        }
                        var center = new Cell(beacon.x(),beacon.y(),beacon.z());
                        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                            var floor = cells.get(new Cell(center.x + dx, center.y, center.z + dz));
                            if (floor == null || floor.blockId() == 0) failures.add(id + "/" + yaw + ": incomplete arena floor");
                            for (int dy = 1; dy <= 2; dy++) {
                                var air = cells.get(new Cell(center.x + dx, center.y + dy, center.z + dz));
                                if (air != null && air.blockId() != 0) failures.add(id + "/" + yaw + ": blocked arena");
                            }
                        }
                    }
                    for (var poi : definition.getPois()) {
                        if (PoiDualCellNormalize.keepsFurnitureLocal(poi.getInteractionKind(), poi.getBlockTypeId())) continue;
                        var p = PrefabLocalOffset.rotate(yaw, poi.getLocalX(), poi.getLocalY(), poi.getLocalZ());
                        for (int dy = 0; dy < 2; dy++) {
                            var space = cells.get(new Cell(p.x, p.y + dy, p.z));
                            if (space == null || space.blockId() != 0)
                                failures.add(id + "/" + yaw + ": work position is not clear at " + p + " + " + dy);
                        }
                        var floor = cells.get(new Cell(p.x, p.y - 1, p.z));
                        if (floor == null || floor.blockId() == 0)
                            failures.add(id + "/" + yaw + ": work position has no floor at " + p);
                    }
                    var expected = new HashSet<Cell>();
                    for (var block : cells.values()) {
                        if (block.filler() != 0 || block.blockId() == 0) continue;
                        var type = BlockType.getAssetMap().getAsset(block.blockId());
                        var boxes = type == null ? null : BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
                        if (boxes == null) { failures.add(id + "/" + yaw + ": missing hitbox at " + block.x() + "," + block.y() + "," + block.z()); continue; }
                        FillerBlockUtil.forEachFillerBlock(boxes.get(block.blockRotation()), (dx, dy, dz) -> {
                            if (dx == 0 && dy == 0 && dz == 0) return;
                            var point = new Cell(block.x() + dx, block.y() + dy, block.z() + dz);
                            expected.add(point);
                            var actual = cells.get(point);
                            int packed = FillerBlockUtil.pack(dx, dy, dz);
                            if (actual == null || actual.blockId() != block.blockId()
                                || actual.blockRotation() != block.blockRotation() || actual.filler() != packed) {
                                failures.add(id + "/" + yaw + ": " + type.getId() + " requires filler at " + point
                                    + ", found " + (actual == null ? "missing" : BlockType.getAssetMap().getAsset(actual.blockId()).getId() + "/" + actual.filler()));
                                if(yaw==Rotation.None)com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Prefab filler detail: %s",
                                    new com.google.gson.Gson().toJson(Map.of("prefab",definition.getPrefabPath(),"x",point.x(),"y",point.y(),"z",point.z(),
                                        "name",type.getId(),"rotation",block.blockRotation(),"filler",packed,
                                        "previous",actual==null?"missing":BlockType.getAssetMap().getAsset(actual.blockId()).getId(),
                                        "previousFiller",actual==null?0:actual.filler())));
                            }
                        });
                    }
                    for (var block : cells.values()) if (block.filler() != 0) {
                        fillers++;
                        if (!expected.contains(new Cell(block.x(), block.y(), block.z())))
                            failures.add(id + "/" + yaw + ": unexpected filler at " + block.x() + "," + block.y() + "," + block.z());
                    }
                }
        }
        var logger = com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
        failures.stream().limit(800).forEach(f -> logger.atWarning().log("Prefab check: " + f));
        String result = "Specialist prefab check: " + (failures.isEmpty() ? "PASSED" : "FAILED") + ". "
            + buildings + " buildings, " + rotations + " rotations, " + fillers + " filler cells, " + failures.size() + " problems.";
        logger.atInfo().log(result);
        ctx.sendMessage(Message.raw(result));
        return CompletableFuture.completedFuture(null);
    }
}
