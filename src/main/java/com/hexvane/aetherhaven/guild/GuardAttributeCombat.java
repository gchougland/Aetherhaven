package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.*;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;

/** Only opted in equipment profiles participate. Other mods retain their named stat modifiers. */
public final class GuardAttributeCombat {
    private static final String HEALTH_KEY="aetherhaven:guard_attributes";
    private static final String SPEED_PREFIX="Aetherhaven_Guard_Speed_";
    private GuardAttributeCombat() {}
    public static GuardCombatStats resolve(Ref<EntityStore> ref,Store<EntityStore> store) {
        if(ref==null||!ref.isValid())return null;
        var binding=store.getComponent(ref,TownVillagerBinding.getComponentType());
        var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());
        var plugin=AetherhavenPlugin.get();
        if(plugin==null||binding==null||identity==null||!TownVillagerBinding.KIND_GUARD.equals(binding.getKind()))return null;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(binding.getTownId());if(town==null)return null;
        var guard=town.getHiredGuardRecords().stream().filter(g->identity.getCharacterId().equals(g.getCharacterId())).findFirst().orElse(null);
        var equipment=guard==null?null:plugin.getEquipmentProfileCatalog().byId(guard.getEquipmentProfileId());
        if(equipment==null||!equipment.usesAttributeCombat())return null;
        return GuardCombatStats.derive(TownsfolkProgressService.getOrCreate(town,manager,plugin,identity.getCharacterId()),equipment);
    }
    public static void clear(Ref<EntityStore> ref,Store<EntityStore> store) {
        var map=store.getComponent(ref,EntityStatMap.getComponentType());
        if(map!=null)map.removeModifier(DefaultEntityStatTypes.getHealth(),HEALTH_KEY);
        var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());
        if(effects!=null)for(int i=1;i<=40;i++) {
            int id=EntityEffect.getAssetMap().getIndex(SPEED_PREFIX+i);
            if(id>=0&&effects.getActiveEffects().containsKey(id))effects.removeEffect(ref,id,store);
        }
    }
    /** Refresh immediately after equipment changes, on the owning world thread. */
    public static void refresh(Ref<EntityStore> ref,Store<EntityStore> store) {
        if(ref==null||!ref.isValid())return;
        var stats=resolve(ref,store);
        var map=store.getComponent(ref,EntityStatMap.getComponentType());
        if(map==null)return;
        int h=DefaultEntityStatTypes.getHealth();var health=map.get(h);
        if(stats==null){if(health!=null&&health.getModifiers()!=null&&health.getModifiers().containsKey(HEALTH_KEY))clear(ref,store);return;}
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(npc==null||npc.getRole()==null||npc.getRole().isRoleChangeRequested()||health==null||health.get()<=0)return;
        float amount=stats.health()-npc.getRole().getInitialMaxHealth();
        var modifier=new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,amount);
        var current=health.getModifiers()==null?null:health.getModifiers().get(HEALTH_KEY);
        if(!modifier.equals(current)) {
            // Attribute training and gear changes preserve wounds, never refill health.
            float fraction=health.getMax()>0?Math.clamp(health.get()/health.getMax(),0,1):0;
            map.putModifier(h,HEALTH_KEY,modifier);
            map.setStatValue(h,health.getMax()*fraction);
        }
        var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());
        if(effects!=null) {
            int wanted=EntityEffect.getAssetMap().getIndex(SPEED_PREFIX+stats.speed());
            for(int i=1;i<=40;i++) {
                int id=EntityEffect.getAssetMap().getIndex(SPEED_PREFIX+i);
                if(id>=0&&id!=wanted&&effects.getActiveEffects().containsKey(id))effects.removeEffect(ref,id,store);
            }
            var effect=EntityEffect.getAssetMap().getAsset(wanted);
            if(effect!=null)effects.addEffect(ref,effect,store);
        }
    }
    public static final class Refresh extends TickingSystem<EntityStore> {
        private final Map<World,Long> next=new WeakHashMap<>();
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            var world=store.getExternalData().getWorld();long now=System.nanoTime();
            synchronized(next){if(now<next.getOrDefault(world,0L))return;next.put(world,now+1_000_000_000L);}
            world.execute(()->{
                if(!world.isAlive()||store.isShutdown())return;
                var refs=new ArrayList<Ref<EntityStore>>();
                store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(),NPCEntity.getComponentType(),EntityStatMap.getComponentType()),(chunk,buffer)->{
                    for(int i=0;i<chunk.size();i++)refs.add(chunk.getReferenceTo(i));
                });
                for(var ref:refs) refresh(ref,store);
            });
        }
    }
    public static final class CombatDamage extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage) {
            if(damage.isCancelled()||damage.getAmount()<=0)return;
            if(damage.getSource() instanceof Damage.EntitySource source) {
                var attacking=resolve(source.getRef(),store);
                if(attacking!=null)damage.setAmount(damage.getAmount()*attacking.damageMultiplier());
            }
            var cause = com.hypixel.hytale.server.core.modules.entity.damage.DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
            if(cause != null && !cause.doesBypassResistances()) {
                var defending=resolve(chunk.getReferenceTo(index),store);
                if(defending!=null)damage.setAmount(damage.getAmount()*(1-defending.damageReduction()));
            }
        }
    }
}
