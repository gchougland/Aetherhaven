package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.patrol.GuardPatrolSystem;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Safety net: when a dead hired guard's corpse is removed, free the town hire slot if death handling missed it.
 */
public final class HiredGuardDeathRemoveSystem extends RefSystem<EntityStore> {
    @Nonnull
    private final AetherhavenPlugin plugin;

    public HiredGuardDeathRemoveSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            TownVillagerBinding.getComponentType(),
            UUIDComponent.getComponentType(),
            DeathComponent.getComponentType()
        );
    }

    @Override
    public void onEntityAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {}

    @Override
    public void onEntityRemove(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (reason == RemoveReason.UNLOAD) {
            return;
        }
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (binding == null || uc == null) {
            return;
        }
        UUID entityUuid = uc.getUuid();
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            return;
        }
        if (!GuardHireService.isHiredGuard(town, entityUuid)) {
            return;
        }
        TownsfolkCharacterBinding tb = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        String characterId = tb != null ? tb.getCharacterId() : null;
        characterId = GuardHireService.removeHiredGuardFromTown(town, entityUuid, characterId);
        tm.updateTown(town);
        if (characterId != null && !characterId.isBlank()) {
            TownsfolkExistenceService.releaseByEntity(world, plugin, entityUuid);
        }
        GuardPatrolSystem.clearAssignmentsForGuard(world, plugin, entityUuid);
    }
}
