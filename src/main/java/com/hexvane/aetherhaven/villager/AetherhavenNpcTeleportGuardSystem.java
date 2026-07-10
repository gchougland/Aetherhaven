package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Blocks vanilla teleporter warps for Aetherhaven town NPCs. Teleporter blocks fire on {@link
 * com.hypixel.hytale.protocol.InteractionType#Collision}; without this guard, villagers and tourists can accidentally
 * warp when pathing across pads.
 *
 * <p>Must not {@code removeComponent(Teleport)} here: {@link TeleportSystems.MoveSystem} also removes {@link Teleport}
 * after applying it. Queuing a second remove crashes the world thread with {@code Archetype doesn't contain
 * ComponentType}. Instead rewrite the pending teleport to the NPC's current pose (no-op) before MoveSystem runs, and
 * leave {@code UsedTeleporter} alone so {@link AetherhavenNpcUsedTeleporterGuardSystem} can retarget its clear-out.
 */
public final class AetherhavenNpcTeleportGuardSystem extends RefChangeSystem<EntityStore, Teleport> {
    @Nonnull
    private static final ComponentType<EntityStore, Teleport> TELEPORT = Teleport.getComponentType();

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies =
        Set.of(new SystemDependency<>(Order.BEFORE, TeleportSystems.MoveSystem.class));

    @Nonnull
    private final Query<EntityStore> query =
        Query.and(
            NPCEntity.getComponentType(),
            Query.not(Player.getComponentType()),
            Query.or(
                TownVillagerBinding.getComponentType(),
                TownsfolkCharacterBinding.getComponentType(),
                AetherhavenVillagerHandle.getComponentType()
            )
        );

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

    @Nonnull
    @Override
    public ComponentType<EntityStore, Teleport> componentType() {
        return TELEPORT;
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Teleport teleport,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        guard(ref, teleport, commandBuffer);
    }

    @Override
    public void onComponentSet(
        @Nonnull Ref<EntityStore> ref,
        Teleport oldComponent,
        @Nonnull Teleport newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        guard(ref, newComponent, commandBuffer);
    }

    @Override
    public void onComponentRemoved(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Teleport component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {}

    private static void guard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Teleport teleport,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer.getComponent(ref, AetherhavenAllowedTeleport.getComponentType()) != null) {
            commandBuffer.removeComponent(ref, AetherhavenAllowedTeleport.getComponentType());
            return;
        }
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        // Mutate in place before MoveSystem applies the component. Do not remove Teleport — MoveSystem owns that.
        teleport.setPosition(transform.getPosition());
        teleport.setRotation(transform.getRotation());
        teleport.withoutVelocityReset();
    }
}
