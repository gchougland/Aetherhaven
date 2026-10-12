package com.hexvane.aetherhaven.alchemy;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ExplodeInteraction;

/** Native explosions use Environment, which bypasses ordinary damage resistance assets. */
public final class AlchemyBlastWardSystem extends DamageEventSystem {
    private static final String ALCHEMY_EFFECT_PREFIX="Aetherhaven_Alchemy_";
    @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getFilterDamageGroup(); }
    @Override public Query<EntityStore> getQuery() { return EffectControllerComponent.getComponentType(); }
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage) {
        var controller=chunk.getComponent(index,EffectControllerComponent.getComponentType());
        if(controller==null)return;
        var cause=DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        String causeId=cause==null?"":cause.getId();
        boolean blast=controller.getActiveEffects().containsKey(EntityEffect.getAssetMap().getIndex("Aetherhaven_Alchemy_BlastWard1"))&&blocks(damage.getSource(),causeId);
        boolean poison=controller.getActiveEffects().containsKey(EntityEffect.getAssetMap().getIndex("Aetherhaven_Alchemy_PoisonWard1"))&&"Poison".equals(causeId);
        if(blast||poison||immune(controller,causeId))damage.setCancelled(true);
    }
    public static boolean blocks(Damage.Source source,String cause) {
        return source == ExplodeInteraction.DAMAGE_SOURCE_EXPLOSION
            || source instanceof Damage.ProjectileSource && "Environment".equals(cause);
    }
    /**
     * A ward whose own asset resists a damage cause completely stops that damage outright, so the drinker is not
     * left flinching at hits worth nothing. A partial resistance is left to the native armour step.
     */
    private static boolean immune(EffectControllerComponent controller,String causeId) {
        if(causeId.isEmpty())return false;
        for(int id:controller.getActiveEffects().keySet()) {
            var effect=EntityEffect.getAssetMap().getAsset(id);
            if(effect==null||!effect.getId().startsWith(ALCHEMY_EFFECT_PREFIX))continue;
            var resistances=effect.getDamageResistanceValues();
            if(resistances==null)continue;
            for(var entry:resistances.entrySet())
                if(entry.getKey()!=null&&causeId.equals(entry.getKey().getId())&&fullyResists(entry.getValue()))return true;
        }
        return false;
    }
    public static boolean fullyResists(ResistanceModifier[] modifiers) {
        if(modifiers==null)return false;
        float percent=0;
        for(var modifier:modifiers)
            if(modifier!=null&&modifier.getCalculationType()==ResistanceModifier.ResistanceCalculationType.PERCENT)percent+=modifier.getAmount();
        return percent>=1f;
    }
}
