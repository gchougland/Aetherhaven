package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.audit.VillagerAuditContext;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.NPCPreTickSystem;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Keeps town villagers out of the base game's NPC despawn checks and clears stray spawn linkage on load or tick. */
public final class TownVillagerNpcWorldSpawnSanitizeSystems {
    private TownVillagerNpcWorldSpawnSanitizeSystems() {}

    private static boolean removeIfTownWasDissolved(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (binding == null || plugin == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        if (tm.getTown(binding.getTownId()) != null) {
            return false;
        }
        VillagerAuditContext.runWithSource("dissolved_town_orphan", () -> commandBuffer.removeEntity(ref, RemoveReason.REMOVE));
        return true;
    }

    /**
     * Removes NPCs whose uuid was replaced by revive/reset/respawn. On chunk {@link AddReason#LOAD}, also removes
     * Gaia-eligible story villagers whose uuid is no longer the town's canonical uuid for that role.
     */
    private static boolean removeIfStaleUuidAfterRespawn(
        @Nonnull Ref<EntityStore> ref,
        @Nullable AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (binding == null || uc == null || plugin == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            return false;
        }
        UUID entityUuid = uc.getUuid();
        if (town.isEntityUuidSuperseded(entityUuid)) {
            VillagerAuditContext.runWithSource(
                "stale_uuid_after_respawn",
                () -> commandBuffer.removeEntity(ref, RemoveReason.REMOVE)
            );
            return true;
        }
        if (reason != AddReason.LOAD) {
            return false;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        String roleId = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
        if (roleId.isEmpty() || !ResidentRegistryService.isGaiaRevivalEligible(binding.getKind(), roleId)) {
            return false;
        }
        UUID canonical = ResidentRegistryService.findCanonicalEntityUuidForGaiaRole(town, roleId);
        if (canonical == null || canonical.equals(entityUuid)) {
            return false;
        }
        VillagerAuditContext.runWithSource(
            "stale_uuid_after_respawn",
            () -> commandBuffer.removeEntity(ref, RemoveReason.REMOVE)
        );
        return true;
    }

    public static final class OnAdd extends RefSystem<EntityStore> {
        @Nonnull
        private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.BEFORE, NPCPreTickSystem.class));
        @Nonnull
        private final Query<EntityStore> query = Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType());

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }

        @Override
        public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            if (removeIfTownWasDissolved(ref, store, commandBuffer)) {
                return;
            }
            if (removeIfStaleUuidAfterRespawn(ref, reason, store, commandBuffer)) {
                return;
            }
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc == null) {
                return;
            }
            if (!TownVillagerNpcWorldSpawnSanitizeUtil.needsSanitize(ref, npc, store)) {
                return;
            }
            TownVillagerNpcWorldSpawnSanitizeUtil.sanitize(ref, npc, store, commandBuffer);
        }

        @Override
        public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {}
    }

    public static final class EachTick extends EntityTickingSystem<EntityStore> {
        @Nonnull
        private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.BEFORE, NPCPreTickSystem.class));
        @Nonnull
        private final Query<EntityStore> query = Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType());

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return query;
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
            if (removeIfTownWasDissolved(ref, store, commandBuffer)) {
                return;
            }
            // Tick path only uses the superseded-uuid list (reason null skips canonical LOAD check) so a fresh SPAWN
            // is not removed before UUID migration finishes.
            if (removeIfStaleUuidAfterRespawn(ref, null, store, commandBuffer)) {
                return;
            }
            NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
            if (npc == null) {
                return;
            }
            if (!TownVillagerNpcWorldSpawnSanitizeUtil.needsSanitize(ref, npc, store)) {
                return;
            }
            TownVillagerNpcWorldSpawnSanitizeUtil.sanitize(ref, npc, store, commandBuffer);
        }
    }
}
