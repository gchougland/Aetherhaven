package com.hexvane.aetherhaven.alchemy;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;

/** Shared end-of-animation operation, also exercised by the isolated native fixture. */
public final class AlchemyFlaskService {
    private AlchemyFlaskService() {}
    public static boolean drink(Ref<EntityStore> ref,ComponentAccessor<EntityStore> accessor,ItemContainer inventory,short slot) {
        var controller=accessor.getComponent(ref,EffectControllerComponent.getComponentType());
        if(controller==null||inventory==null||slot<0||slot>=inventory.getCapacity())return false;
        var flask=inventory.getItemStack(slot);
        if(!AlchemyFlask.isFlask(flask)||AlchemyFlask.charges(flask)<=0)return false;
        var effects=AlchemyIngredients.effects(AlchemyFlask.ingredients(flask));
        if(effects.isEmpty()||effects.stream().anyMatch(i->EntityEffect.getAssetMap().getAsset(i.effect())==null)
            ||!AlchemyInventory.exchange(inventory,slot,flask,AlchemyFlask.consume(flask),Map.of()))return false;
        for(var ingredient:effects) {
            if(ingredient.family().endsWith("Boost")) {
                String prefix="Aetherhaven_Alchemy_"+ingredient.family();
                int stronger=EntityEffect.getAssetMap().getIndex(prefix+"2");
                if(ingredient.strength()==1&&controller.getActiveEffects().containsKey(stronger))continue;
                int other=EntityEffect.getAssetMap().getIndex(prefix+(ingredient.strength()==1?2:1));
                if(controller.getActiveEffects().containsKey(other))controller.removeEffect(ref,other,accessor);
            }
            controller.addEffect(ref,EntityEffect.getAssetMap().getAsset(ingredient.effect()),accessor);
        }
        return true;
    }
}
