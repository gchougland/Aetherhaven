package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.builtin.adventure.teleporter.interaction.server.UsedTeleporter;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;
import javax.annotation.Nonnull;

/**
 * After a blocked teleporter pad touch, retarget {@link UsedTeleporter} clear-out to the NPC's current feet.
 *
 * <p>Vanilla clear-out measures distance from the <em>warp destination</em>. When we no-op the warp, the NPC is still
 * on the pad (far from that destination), so UsedTeleporter would clear immediately and the pad would re-fire every
 * tick. Anchoring clear-out to the pad keeps the cooldown until they walk away.
 */
public final class AetherhavenNpcUsedTeleporterGuardSystem extends RefChangeSystem<EntityStore, UsedTeleporter> {
    @Nonnull
    private static final ComponentType<EntityStore, UsedTeleporter> USED_TELEPORTER = UsedTeleporter.getComponentType();

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
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, UsedTeleporter> componentType() {
        return USED_TELEPORTER;
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UsedTeleporter used,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        retarget(ref, used, commandBuffer);
    }

    @Override
    public void onComponentSet(
        @Nonnull Ref<EntityStore> ref,
        UsedTeleporter oldComponent,
        @Nonnull UsedTeleporter newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // onComponentAdded may putComponent a retargeted instance; do not retarget again.
    }

    @Override
    public void onComponentRemoved(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UsedTeleporter component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {}

    private static void retarget(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UsedTeleporter used,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d feet = transform.getPosition();
        // Replace so destinationWorldUuid is cleared (cross-world pads would otherwise clear immediately).
        commandBuffer.putComponent(
            ref,
            USED_TELEPORTER,
            new UsedTeleporter(null, new Vector3d(feet), used.getClearOutXZ(), used.getClearOutY())
        );
    }
}
