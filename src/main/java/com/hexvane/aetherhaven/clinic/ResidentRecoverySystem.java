package com.hexvane.aetherhaven.clinic;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.town.TownAdmission;
import com.hexvane.aetherhaven.guild.GuardRecoveryProgress;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.HealthRegenState;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;

/** Slow, loaded passive recovery for admitted residents, without changing load health or resurrection. */
public final class ResidentRecoverySystem extends EntityTickingSystem<EntityStore> {
    private static final class Rest {
        final GuardRecoveryProgress progress = new GuardRecoveryProgress();
        HealthRegenState nativeState;
        boolean originallyEnabled;
        long lastTick;
    }
    // Values do not hold the weak key or its world. Unloaded entities cannot accrue rest.
    private final Map<Ref<EntityStore>, Rest> resting = Collections.synchronizedMap(new WeakHashMap<>());
    @Override public Query<EntityStore> getQuery() {
        return Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType(), EntityStatMap.getComponentType(), UUIDComponent.getComponentType());
    }
    @Override public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        var ref = chunk.getReferenceTo(index);
        buffer.run(s -> { if (ref.isValid()) update(ref, dt, s); });
    }
    private void update(Ref<EntityStore> ref, float dt, Store<EntityStore> store) {
        var npc = store.getComponent(ref, NPCEntity.getComponentType());
        var binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        var nativeState = store.getComponent(ref, HealthRegenState.getComponentType());
        var plugin = AetherhavenPlugin.get();
        var town = plugin==null||binding==null?null:AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin).getTown(binding.getTownId());
        var id=store.getComponent(ref,UUIDComponent.getComponentType());
        boolean resident=npc!=null&&town!=null&&id!=null&&TownAdmission.isMember(town,id.getUuid());
        if (!resident) {
            var previous = resting.remove(ref);
            if (previous != null && previous.nativeState == nativeState && nativeState != null)
                nativeState.setRegenEnabled(previous.originallyEnabled);
            return;
        }
        if (npc.getRole() == null || npc.getRole().isRoleChangeRequested() || nativeState == null) return;
        var rest = resting.computeIfAbsent(ref, ignored -> new Rest());
        if (rest.nativeState != nativeState) {
            rest.nativeState = nativeState;
            rest.originallyEnabled = nativeState.isRegenEnabled();
        }
        nativeState.setRegenEnabled(false);
        var map = store.getComponent(ref, EntityStatMap.getComponentType());
        int key = DefaultEntityStatTypes.getHealth();
        var health = map == null ? null : map.get(key);
        if (health == null) return;
        String state = NpcSupportUtil.stateName(store, ref);
        long now=System.nanoTime();
        boolean gap=rest.lastTick!=0&&now-rest.lastTick>2_000_000_000L;
        rest.lastTick=now;
        boolean eligible = !gap && rest.originallyEnabled && store.getComponent(ref, DeathComponent.getComponentType()) == null
            && !com.hexvane.aetherhaven.hunter.HuntService.owns(ref,store)
            && (state == null || !state.contains("Combat"))
            && Universe.get().getPlayers().stream().anyMatch(p -> town.hasMemberOrOwner(p.getUuid()));
        float amount = rest.progress.advance(dt, health.get(), health.getMax(), eligible);
        if (amount > 0) map.setStatValue(key, Math.min(health.getMax(), health.get() + amount));
    }
}
