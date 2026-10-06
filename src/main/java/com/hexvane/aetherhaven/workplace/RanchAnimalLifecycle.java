package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/** Release confirmed losses only. An unloaded animal still belongs to its saved herd. */
public final class RanchAnimalLifecycle {
    private RanchAnimalLifecycle() {}
    private static Query<EntityStore> animals() {
        return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType());
    }
    private static void lost(Ref<EntityStore> ref,Store<EntityStore> store,AetherhavenPlugin plugin) {
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        var identity=store.getComponent(ref,UUIDComponent.getComponentType());
        if(npc==null||identity==null||!RanchWorkService.managedRole(npc.getRoleName()))return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        for(var town:manager.allTowns()) {
            boolean changed=false;
            for(var site:town.getApprenticeWorkplaces().sites())
                if(site.food().ranch().lost(identity.getUuid()))changed=true;
            for(var plot:town.getPlotInstances()) {
                var state=town.peekPlotProduction(plot.getPlotId());
                if(state!=null&&state.lostHerdAnimal(identity.getUuid()))changed=true;
            }
            if(changed)manager.updateTown(town);
        }
    }
    public static final class Deaths extends DeathSystems.OnDeathSystem {
        private final AetherhavenPlugin plugin;
        public Deaths(AetherhavenPlugin plugin) { this.plugin=plugin; }
        @Override public Query<EntityStore> getQuery() { return animals(); }
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
            lost(ref,store,plugin);
        }
    }
    public static final class Removals extends RefSystem<EntityStore> {
        private final AetherhavenPlugin plugin;
        public Removals(AetherhavenPlugin plugin) { this.plugin=plugin; }
        @Override public Query<EntityStore> getQuery() { return animals(); }
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
            if(reason==RemoveReason.REMOVE)lost(ref,store,plugin);
        }
    }
}
