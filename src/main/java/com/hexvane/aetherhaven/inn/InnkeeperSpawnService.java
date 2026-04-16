package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class InnkeeperSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private InnkeeperSpawnService() {}

    /** If the inn quest was completed before this build existed, spawn the innkeeper once. */
    public static void reconcileAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (town.getInnkeeperEntityUuid() != null) {
                continue;
            }
            if (!town.hasQuestCompleted(AetherhavenConstants.QUEST_BUILD_INN)) {
                continue;
            }
            if (!town.hasCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_PLOT_INN)) {
                continue;
            }
            world.execute(() -> spawnInnkeeperIfPossible(world, plugin, tm, town));
        }
    }

    public static void trySpawnAfterInnQuestComplete(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        world.execute(
            () -> {
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                spawnInnkeeperIfPossible(world, plugin, tm, town);
            }
        );
    }

    private static void spawnInnkeeperIfPossible(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town
    ) {
        if (town.getInnkeeperEntityUuid() != null) {
            return;
        }
        PlotInstance plot = town.findCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_PLOT_INN);
        if (plot == null) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(AetherhavenConstants.CONSTRUCTION_PLOT_INN);
        if (def == null) {
            LOGGER.atWarning().log("Inn construction definition missing");
            return;
        }
        int[] local = def.getInnkeeperSpawnLocal();
        if (local == null) {
            LOGGER.atWarning().log("innkeeperSpawnLocal not set for inn construction");
            return;
        }
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        var yaw = plot.resolvePrefabYaw();
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;
        Vector3d pos = new Vector3d(wx + 0.5, wy, wz + 0.5);

        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        var pair = npc.spawnNPC(store, AetherhavenConstants.INNKEEPER_NPC_ROLE_ID, null, pos, Vector3f.ZERO);
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn innkeeper for town %s", town.getTownId());
            return;
        }
        Ref<EntityStore> ref = pair.first();
        store.putComponent(ref, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        String handle = "Villager_Innkeep_" + shortHex(town.getTownId());
        store.putComponent(ref, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(handle));
        store.putComponent(
            ref,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_INNKEEPER, plot.getPlotId(), plot.getPlotId())
        );
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp != null) {
            town.setInnkeeperEntityUuid(uuidComp.getUuid());
            ResidentRegistryService.upsert(
                town,
                tm,
                AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
                TownVillagerBinding.KIND_INNKEEPER,
                plot.getPlotId(),
                uuidComp.getUuid()
            );
        }
        town.setInnActive(true);
        town.getInnPoolNpcIds().clear();
        tm.updateTown(town);
        LOGGER.atInfo().log("Spawned innkeeper for town %s at %s,%s,%s", town.getTownId(), wx, wy, wz);
    }

    @Nonnull
    private static String shortHex(@Nonnull UUID townId) {
        String hex = townId.toString().replace("-", "");
        return hex.length() >= 8 ? hex.substring(0, 8) : hex;
    }
}
