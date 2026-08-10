package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.monument.FounderMonumentStatueSkin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticAppearanceService;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.system.ModelSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleBuilderSystem;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * After NPC spawn/load, the client can briefly render the wrong attachment textures when many player-model NPCs appear
 * at once or when appearance is swapped immediately after spawn. A deferred rebuild from {@link PersistentModel} matches
 * what a full world reload does and clears those glitches.
 */
public final class NpcPersistentModelResyncSystem extends HolderSystem<EntityStore> {
    private final ComponentType<EntityStore, PersistentModel> persistentModelType = PersistentModel.getComponentType();
    private final ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(this.persistentModelType, this.npcType, Query.not(FounderMonumentStatueSkin.getComponentType()));
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.AFTER, ModelSystems.SetRenderedModel.class),
            new SystemDependency<>(Order.AFTER, RoleBuilderSystem.class)
        );
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
        if (reason != AddReason.SPAWN && reason != AddReason.LOAD) {
            return;
        }
        UUIDComponent uuidComponent = holder.getComponent(UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        var entityId = uuidComponent.getUuid();
        world.execute(() -> {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityId);
            if (ref == null || !ref.isValid()) {
                return;
            }
            NpcModelSpawnUtil.resyncFromPersistentModel(ref, store);
            applyTownCosmeticsIfPresent(ref, store, world);
        });
    }

    private static void applyTownCosmeticsIfPresent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world
    ) {
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (binding == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        String residentKey = com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticKeys.resolve(ref, store);
        if (residentKey == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null || !VillagerCosmeticAppearanceService.hasAnyOverride(town, residentKey)) {
            return;
        }
        VillagerCosmeticAppearanceService.applySavedCosmetics(ref, store, town);
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) {}
}
