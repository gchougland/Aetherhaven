package com.hexvane.aetherhaven.npc;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;

/** Transient, cooperative pause for rescue teleports and player conversations. Never persisted or hard frozen. */
public final class NpcServicePause implements Component<EntityStore> {
    private static ComponentType<EntityStore, NpcServicePause> type;
    private long teleportUntil;
    private boolean holding;
    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        type = registry.registerComponent(NpcServicePause.class, NpcServicePause::new);
        registry.registerSystem(new Tick());
    }
    public static void begin(Ref<EntityStore> ref, Store<EntityStore> store, boolean teleport) {
        var pause = store.getComponent(ref, type);
        if (pause == null) pause = new NpcServicePause();
        if (teleport) pause.teleportUntil = System.currentTimeMillis() + 5000L;
        store.putComponent(ref, type, pause);
    }
    public static boolean isHeld(NPCEntity npc) {
        var ref = npc.getReference();
        return type != null && ref != null && ref.isValid() && ref.getStore().getComponent(ref, type) != null;
    }
    static boolean keepHolding(long now, long teleportUntil, boolean talking) {
        return talking || now < teleportUntil;
    }
    @Override @Nonnull public NpcServicePause clone() {
        var copy = new NpcServicePause(); copy.teleportUntil = teleportUntil; copy.holding = holding; return copy;
    }
    public static final class Tick extends EntityTickingSystem<EntityStore> {
        @Override @Nonnull public Query<EntityStore> getQuery() { return Query.and(type, NPCEntity.getComponentType()); }
        @Override public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commands) {
            var ref = chunk.getReferenceTo(index);
            var pause = chunk.getComponent(index, type);
            var npc = chunk.getComponent(index, NPCEntity.getComponentType());
            if (pause == null || npc == null || npc.getRole() == null) return;
            boolean talking = NpcDialogueCleanup.hasTalkers(ref, store);
            if (keepHolding(System.currentTimeMillis(), pause.teleportUntil, talking)) {
                String state = talking ? "$Interaction" : com.hexvane.aetherhaven.AetherhavenConstants.NPC_STATE_STAND_STILL;
                if (!state.equals(NpcSupportUtil.stateName(store, ref))) NpcSupportUtil.setState(ref, state, null, commands);
                NpcStandStill.clearResidualMotion(store, ref, commands);
                pause.holding = true;
            } else {
                String state = NpcSupportUtil.stateName(store, ref);
                if (pause.holding && (state.contains("Interaction") || state.equals(com.hexvane.aetherhaven.AetherhavenConstants.NPC_STATE_STAND_STILL)))
                    NpcSupportUtil.setState(ref, "Idle", null, commands);
                commands.tryRemoveComponent(ref, type);
            }
        }
    }
}
