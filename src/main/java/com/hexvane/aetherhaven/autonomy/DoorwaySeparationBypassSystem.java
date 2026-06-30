package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.AvoidanceSystem;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import com.hypixel.hytale.server.npc.systems.SteppableTickingSystem;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Disables NPC separation push between travelers sharing a doorway. Townsfolk roles use a 2.5 m separation radius —
 * wider than most door frames — so two NPCs entering at once push each other apart and jam in the opening.
 */
public final class DoorwaySeparationBypassSystem extends SteppableTickingSystem {
    @Nonnull
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, RoleSystems.BehaviourTickSystem.class),
        new SystemDependency<>(Order.BEFORE, AvoidanceSystem.class)
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            NPCEntity.getComponentType(),
            TransformComponent.getComponentType(),
            Query.or(VillagerAutonomyState.getComponentType(), TouristAutonomyState.getComponentType())
        );
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return EntityTickingSystem.maybeUseParallel(archetypeChunkSize, taskCount);
    }

    @Override
    public void steppedTick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!isTraveling(archetypeChunk, index)) {
            return;
        }
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (npc == null || transform == null) {
            return;
        }
        Role role = npc.getRole();
        if (role == null) {
            return;
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        World world = commandBuffer.getExternalData().getWorld();
        VillagerDoorUtil.applyDoorwaySeparationBypass(world, store, ref, role, transform.getPosition());
    }

    private static boolean isTraveling(@Nonnull ArchetypeChunk<EntityStore> chunk, int index) {
        VillagerAutonomyState villager = chunk.getComponent(index, VillagerAutonomyState.getComponentType());
        if (villager != null && villager.getPhase() == VillagerAutonomyState.PHASE_TRAVEL) {
            return true;
        }
        TouristAutonomyState tourist = chunk.getComponent(index, TouristAutonomyState.getComponentType());
        if (tourist == null) {
            return false;
        }
        int phase = tourist.getPhase();
        return phase == TouristAutonomyState.PHASE_TRAVEL || phase == TouristAutonomyState.PHASE_RETURNING;
    }
}
