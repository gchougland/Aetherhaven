package com.hexvane.aetherhaven.alchemy;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.*;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Clears native poison debuffs while protected, including poison applied after drinking. */
public final class AlchemyPoisonWardSystem extends EntityTickingSystem<EntityStore> {
    @Override public Query<EntityStore> getQuery() { return EffectControllerComponent.getComponentType(); }
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        var controller=chunk.getComponent(index,EffectControllerComponent.getComponentType());
        if(controller==null||!controller.getActiveEffects().containsKey(EntityEffect.getAssetMap().getIndex("Aetherhaven_Alchemy_PoisonWard1")))return;
        // Snapshot indexes because removal mutates the active map.
        for(int id:controller.getActiveEffectIndexes()) {
            var effect=EntityEffect.getAssetMap().getAsset(id);
            if(effect!=null&&isPoison(effect.getId()))controller.removeEffect(chunk.getReferenceTo(index),id,RemovalBehavior.COMPLETE,buffer);
        }
    }
    public static boolean isPoison(String id) { return "Poison".equals(id)||"Poison_T1".equals(id)||"Poison_T2".equals(id)||"Poison_T3".equals(id); }
}
