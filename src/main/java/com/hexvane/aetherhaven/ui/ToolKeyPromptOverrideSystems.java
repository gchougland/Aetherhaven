package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventorySetActiveSlotEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventorySystems;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The systems behind {@link ToolKeyPromptOverrides}. */
public final class ToolKeyPromptOverrideSystems {
    private ToolKeyPromptOverrideSystems() {}

    /**
     * Checks every player each tick, which costs one map lookup for a player who holds no tool. The check only reads;
     * the edit runs after the tick, where the store may be changed.
     */
    public static final class Tick extends EntityTickingSystem<EntityStore> {
        @Nonnull
        private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(Player.getComponentType());
        }

        @Override
        public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            if (ToolKeyPromptOverrides.pending(ref, store).isEmpty()) {
                return;
            }
            commandBuffer.run(s -> ToolKeyPromptOverrides.reconcile(ref, s));
        }
    }

    /**
     * Switching hotbar slot is the moment a stale entry would matter: the client would run the old tool's input on
     * the new item until told otherwise. So the entries are corrected in the same tick as the switch.
     */
    public static final class SlotChange extends EntityEventSystem<EntityStore, InventorySetActiveSlotEvent> {
        @Nonnull
        private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, InventorySystems.ActiveSlotChangedEntityEventSystem.class)
        );

        public SlotChange() {
            super(InventorySetActiveSlotEvent.class);
        }

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }

        @Override
        public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InventorySetActiveSlotEvent event
        ) {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            commandBuffer.run(s -> ToolKeyPromptOverrides.reconcile(ref, s));
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(Player.getComponentType(), InventoryComponent.Hotbar.getComponentType());
        }
    }

    /**
     * Takes the entries off a player who is leaving a world, before the player is saved, and off one who is entering
     * (left over from a server that stopped without saving cleanly). The tick puts back what the item in hand needs.
     */
    public static final class StripOnWorldChange extends HolderSystem<EntityStore> {
        @Nonnull
        private final Query<EntityStore> query = Query.and(Player.getComponentType(), Interactions.getComponentType());

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }

        @Override
        public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
            ToolKeyPromptOverrides.strip(holder);
        }

        @Override
        public void onEntityRemoved(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store
        ) {
            ToolKeyPromptOverrides.strip(holder);
        }
    }

    /**
     * "Use entity" runs the interaction the target entity lists for the input. A player holding a tool now lists the
     * tool's inputs, so using that player must still do what it did before: nothing.
     */
    public static final class UseEntityGuard extends EntityEventSystem<EntityStore, UseEntityEvent.Pre> {
        public UseEntityGuard() {
            super(UseEntityEvent.Pre.class);
        }

        @Override
        public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseEntityEvent.Pre event
        ) {
            if (event.isCancelled()) {
                return;
            }
            Ref<EntityStore> target = event.getTargetEntity();
            if (!target.isValid()) {
                return;
            }
            Interactions interactions = commandBuffer.getComponent(target, Interactions.getComponentType());
            if (interactions != null && ToolKeyPromptOverrides.isPromptRoot(interactions.getInteractionId(event.getInteractionType()))) {
                event.setCancelled(true);
            }
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }
    }
}
