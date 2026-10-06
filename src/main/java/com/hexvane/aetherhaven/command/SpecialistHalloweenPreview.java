package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.halloween.HalloweenResident;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.joml.Vector3d;

/** Opt-in scenery only. Rescue, game progress, inventory and rewards use ordinary player actions. */
final class SpecialistHalloweenPreview {
    private static final String COFFIN = "Furniture_Village_Coffin";
    private final UUID townId;
    private final int x, floorY, z;

    private SpecialistHalloweenPreview(UUID townId, int x, int floorY, int z) {
        this.townId = townId;
        this.x = x;
        this.floorY = floorY;
        this.z = z;
    }

    static CompletableFuture<SpecialistHalloweenPreview> create(World world, UUID owner, int buildingIndex,
                                                                 BooleanSupplier stillActive) {
        require(isPreview(world.getName(), PersistentWorldSupport.shouldPersistWorldData(world)), "Use the unsaved specialist preview.");
        require(HalloweenResident.rescueSeason(), "The real calendar must be in October for the normal coffin rescue.");
        int x = 32 + (buildingIndex % 4) * 64, z = 32 + (buildingIndex / 4) * 64;
        // Matches the standard preview's supporting platform. The coffin stays outside the shelter footprint.
        int floorY = 126;
        var loads = new ArrayList<CompletableFuture<?>>();
        for (int cx = Math.floorDiv(x - 17, 32); cx <= Math.floorDiv(x + 17, 32); cx++)
            for (int cz = Math.floorDiv(z - 17, 32); cz <= Math.floorDiv(z + 17, 32); cz++)
                for (int cy = Math.floorDiv(floorY, 32); cy <= Math.floorDiv(floorY + 8, 32); cy++)
                    loads.add(world.getChunkStore().getChunkSectionReferenceAsync(cx, cy, cz, GetChunkFlags.SET_TICKING));
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApplyAsync(unused -> {
            require(stillActive.getAsBoolean() && world.isAlive(), "The preview is closing.");
            require(isPreview(world.getName(), PersistentWorldSupport.shouldPersistWorldData(world)), "Use the unsaved specialist preview.");
            var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, AetherhavenPlugin.get());
            var town = manager.findTownForOwnerInWorld(owner);
            require(town != null, "Preview town missing.");
            require(!town.hasQuestCompleted(HalloweenResident.RESCUE_QUEST), "This preview already accepted Jack's rescue.");
            for (int px = x - 17; px <= x + 17; px++)
                for (int pz = z - 17; pz <= z + 17; pz++)
                    for (int py = floorY; py <= floorY + 7; py++)
                        require(ChunkSectionBlockUtil.blockId(world, px, py, pz) == 0,
                            "The Halloween preview site is occupied. Start a fresh preview.");
            for (int px = x - 17; px <= x + 17; px++)
                for (int pz = z - 17; pz <= z + 17; pz++)
                    require(ChunkSectionBlockUtil.setBlockByKey(world, px, floorY, pz, "Rock_Stone", 0), "Preview platform failed.");
            require(ChunkSectionBlockUtil.setBlockByKey(world, x, floorY + 1, z + 12, COFFIN, 0), "Preview coffin placement failed.");
            return new SpecialistHalloweenPreview(town.getTownId(), x, floorY, z);
        }, world);
    }

    static boolean isPreview(String name, boolean persists) {
        return name != null && name.startsWith("aetherhaven_specialist_preview_") && !persists;
    }

    Vector3d viewpoint() { return new Vector3d(x + .5, floorY + 1, z + 15.5); }
    Vector3d shelterViewpoint() { return new Vector3d(x + 5.5, floorY + 1, z + 9.5); }

    /** Checked again on the owning world thread immediately before the shared paste path mutates blocks. */
    void requireShelter(World world, UUID owner) {
        require(isPreview(world.getName(), PersistentWorldSupport.shouldPersistWorldData(world)), "Use the unsaved specialist preview.");
        var town = town(world, owner);
        String issue = shelterIssue(town);
        require(issue == null, issue);
    }

    static String shelterIssue(TownRecord town) {
        if (town == null) return "Preview town missing.";
        if (!town.hasQuestCompleted(HalloweenResident.RESCUE_QUEST)) return "Open the coffin and accept Jack's rescue through normal dialogue first.";
        if (town.getPlotInstances().stream().anyMatch(plot -> "plot_halloween".equals(plot.getConstructionId()))
            || town.getResidentNpcRecords().stream().anyMatch(resident -> HalloweenResident.KIND.equals(resident.getKind())))
            return "Jack's fixture shelter or resident already exists. Use halloweenstatus or visit.";
        return null;
    }

    String status(World world, UUID owner) {
        require(isPreview(world.getName(), PersistentWorldSupport.shouldPersistWorldData(world)), "Use the unsaved specialist preview.");
        var town = town(world, owner);
        var store = world.getEntityStore().getStore();
        var coffin = ChunkSectionBlockUtil.blockType(world, x, floorY + 1, z + 12);
        var occupants = new ArrayList<String>();
        store.forEachChunk(TownVillagerBinding.getComponentType(), (chunk, buffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                var binding = chunk.getComponent(index, TownVillagerBinding.getComponentType());
                if (binding == null || !townId.equals(binding.getTownId()) || !(HalloweenResident.KIND.equals(binding.getKind())
                    || HalloweenResident.RESCUE_KIND.equals(binding.getKind()))) continue;
                var position = chunk.getComponent(index, TransformComponent.getComponentType());
                var model = chunk.getComponent(index, ModelComponent.getComponentType());
                var id = chunk.getComponent(index, UUIDComponent.getComponentType());
                occupants.add(binding.getKind() + " " + (id == null ? "unknown" : id.getUuid())
                    + " at " + (position == null ? "unknown" : position.getPosition())
                    + " model " + (model == null || model.getModel() == null ? "unknown" : model.getModel().getModelAssetId()));
            }
        });
        var game = town.halloweenGame().active();
        String head = game == null ? "none" : game.spot().x() + ", " + game.spot().y() + ", " + game.spot().z()
            + "; picked up " + game.found() + "; owner " + game.player();
        return "Halloween preview: coffin " + x + ", " + (floorY + 1) + ", " + (z + 12)
            + " is " + (coffin == null ? "unavailable" : coffin.getId())
            + "; rescue accepted " + town.hasQuestCompleted(HalloweenResident.RESCUE_QUEST)
            + "; hidden head " + head + "; reward today "
            + town.halloweenGame().rewarded(owner, VillagerReputationService.currentGameEpochDay(store))
            + "; cleanup pending " + town.halloweenGame().cleanup().size()
            + ". Jack: " + (occupants.isEmpty() ? "not loaded" : String.join("; ", new TreeSet<>(occupants))) + ".";
    }

    private TownRecord town(World world, UUID owner) {
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, AetherhavenPlugin.get());
        var town = manager.getTown(townId);
        require(town != null && owner.equals(town.getOwnerUuid()), "The original preview town is unavailable.");
        return town;
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
