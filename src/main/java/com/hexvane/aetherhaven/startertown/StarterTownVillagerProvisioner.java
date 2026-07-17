package com.hexvane.aetherhaven.startertown;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.inn.InnkeeperSpawnService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.WorkplacePlotAssignment;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Test-only direct workplace provisioning, run after starter-town building quests are completed. */
final class StarterTownVillagerProvisioner {
    record Result(int spawned, int skipped) {}

    private StarterTownVillagerProvisioner() {}

    @Nonnull
    static Result provision(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager townManager,
        @Nonnull StarterTownLayoutPlan plan
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        InnkeeperSpawnService.spawnInnkeeperIfPossible(world, plugin, townManager, town);
        Set<String> existingRoles = new HashSet<>();
        for (ResidentNpcRecord resident : town.getResidentNpcRecords()) {
            existingRoles.add(resident.getNpcRoleId());
        }
        int spawned = 0;
        int skipped = 0;
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            return new Result(0, plan.buildings().size());
        }
        for (StarterTownLayoutPlan.Building building : plan.buildings()) {
            PlotInstance plot = town.findCompletePlotWithConstruction(
                plugin.getConstructionCatalog(),
                building.constructionId()
            );
            if (plot == null) {
                skipped++;
                continue;
            }
            VillagerDefinition definition = findDefinition(plugin, building.constructionId());
            if (definition == null) {
                skipped++;
                continue;
            }
            String roleId = definition.getNpcRoleId();
            if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equals(roleId)
                || AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equals(roleId)
                || existingRoles.contains(roleId)) {
                skipped++;
                continue;
            }
            String kind = InnVisitorShopPromotion.resolveResidentKind(definition);
            if (kind == null || kind.isBlank()) {
                skipped++;
                continue;
            }
            Vector3d position = new Vector3d(
                building.roadPoint().x + 0.5,
                building.roadPoint().y,
                building.roadPoint().z + 0.5
            );
            var pair = npcPlugin.spawnNPC(store, roleId, null, position, Rotation3f.ZERO);
            if (pair == null) {
                skipped++;
                continue;
            }
            Ref<EntityStore> npcRef = pair.first();
            store.putComponent(npcRef, VillagerNeeds.getComponentType(), VillagerNeeds.full());
            store.putComponent(
                npcRef,
                AetherhavenVillagerHandle.getComponentType(),
                new AetherhavenVillagerHandle("StarterTown_" + roleId + "_" + shortId(town))
            );
            store.putComponent(
                npcRef,
                TownVillagerBinding.getComponentType(),
                new TownVillagerBinding(town.getTownId(), kind, null)
            );
            NpcSpawnOriginUtil.attach(
                store,
                npcRef,
                "STARTER_TOWN",
                "plotId=" + plot.getPlotId(),
                world,
                position
            );
            UUIDComponent uuid = store.getComponent(npcRef, UUIDComponent.getComponentType());
            if (uuid == null) {
                skipped++;
                continue;
            }
            String assignmentError = WorkplacePlotAssignment.tryAssignWorker(
                world,
                plugin,
                town,
                townManager,
                plot.getPlotId(),
                uuid.getUuid(),
                store
            );
            if (assignmentError != null) {
                store.putComponent(
                    npcRef,
                    TownVillagerBinding.getComponentType(),
                    new TownVillagerBinding(town.getTownId(), kind, plot.getPlotId(), plot.getPlotId())
                );
                ResidentRegistryService.upsert(
                    town,
                    townManager,
                    roleId,
                    kind,
                    plot.getPlotId(),
                    uuid.getUuid()
                );
            }
            town.addInnVisitorPoolExcludedRoleId(roleId);
            existingRoles.add(roleId);
            spawned++;
        }
        townManager.updateTown(town);
        return new Result(spawned, skipped);
    }

    private static VillagerDefinition findDefinition(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String constructionId
    ) {
        for (VillagerDefinition definition : plugin.getVillagerDefinitionCatalog().allByNpcRoleId().values()) {
            String work = definition.getWorkConstructionId();
            if (work != null && workplaceMatches(plugin.getConstructionCatalog(), constructionId, work)) {
                return definition;
            }
        }
        return null;
    }

    static boolean workplaceMatches(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String constructionId,
        @Nonnull String workConstructionId
    ) {
        return catalog.matchesGameplayConstruction(constructionId, workConstructionId)
            || catalog.matchesGameplayConstruction(workConstructionId, constructionId);
    }

    @Nonnull
    private static String shortId(@Nonnull TownRecord town) {
        String value = town.getTownId().toString().replace("-", "");
        return value.length() >= 8 ? value.substring(0, 8) : value;
    }
}
