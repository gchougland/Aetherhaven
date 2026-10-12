package com.hexvane.aetherhaven.alchemy;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.ChangeStatBehaviour;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import java.util.ArrayList;
import java.util.Map;

/** Shared end-of-animation operation, also exercised by the isolated native fixture. */
public final class AlchemyFlaskService {
    private AlchemyFlaskService() {}
    /** An effect that lasts under a second is a restore: it gives its amount once and is gone. */
    public static boolean instant(float assetSeconds){return assetSeconds<1f;}
    /** How long a lasting effect runs for this brew quality. A Good brew runs exactly as long as the asset says. */
    public static float lastingSeconds(float assetSeconds,AlchemyBrew.Rules rules,AlchemyBrew.Quality quality) {
        return instant(assetSeconds)?assetSeconds:assetSeconds*rules.duration(quality);
    }
    /** The share of an instant restore added on top for this brew quality: 0 for Good, 0.1 for ten percent more. */
    public static float restoreBonus(float assetSeconds,AlchemyBrew.Rules rules,AlchemyBrew.Quality quality) {
        return instant(assetSeconds)?rules.restore(quality)-1f:0f;
    }
    public static boolean drink(Ref<EntityStore> ref,ComponentAccessor<EntityStore> accessor,ItemContainer inventory,short slot) {
        var controller=accessor.getComponent(ref,EffectControllerComponent.getComponentType());
        if(controller==null||inventory==null||slot<0||slot>=inventory.getCapacity())return false;
        var flask=inventory.getItemStack(slot);
        if(!AlchemyFlask.isFlask(flask)||AlchemyFlask.charges(flask)<=0)return false;
        var effects=AlchemyIngredients.effects(AlchemyFlask.ingredients(flask));
        // Read before the charge is spent: the quality leaves the flask with its last charge.
        var quality=AlchemyFlask.quality(flask);var rules=AlchemyBrew.rules();
        if(effects.isEmpty()||effects.stream().anyMatch(i->EntityEffect.getAssetMap().getAsset(i.effect())==null)
            ||!AlchemyInventory.exchange(inventory,slot,flask,AlchemyFlask.consume(flask),Map.of()))return false;
        for(var ingredient:effects) {
            // Boosts and regeneration last for a while, so another strength of the same family may still be running.
            var active=new ArrayList<Integer>();
            for(int strength:AlchemyIngredients.strengths(ingredient.family()))
                if(strength!=ingredient.strength()&&controller.getActiveEffects().containsKey(index(ingredient.family(),strength)))active.add(strength);
            var overlap=AlchemyIngredients.overlap(ingredient.strength(),active);
            if(!overlap.apply())continue;
            for(int weaker:overlap.replace())controller.removeEffect(ref,index(ingredient.family(),weaker),accessor);
            var effect=EntityEffect.getAssetMap().getAsset(ingredient.effect());
            // The engine's own call with a length: the asset stays the single place the base length is set.
            controller.addEffect(ref,effect,lastingSeconds(effect.getDuration(),rules,quality),effect.getOverlapBehavior(),accessor);
            float bonus=restoreBonus(effect.getDuration(),rules,quality);
            var amounts=effect.getEntityStats();
            var stats=bonus>0&&amounts!=null?accessor.getComponent(ref,EntityStatMap.getComponentType()):null;
            if(stats!=null) {
                // The same change the effect makes, scaled to the extra share and applied the way the effect applies it.
                var extra=new Int2FloatOpenHashMap();
                for(var amount:amounts.int2FloatEntrySet())extra.put(amount.getIntKey(),amount.getFloatValue()*bonus);
                stats.processStatChanges(EntityStatMap.Predictable.ALL,extra,effect.getValueType(),ChangeStatBehaviour.Add);
            }
        }
        return true;
    }
    private static int index(String family,int strength) {
        return EntityEffect.getAssetMap().getIndex(AlchemyIngredients.effectId(family,strength));
    }
}
