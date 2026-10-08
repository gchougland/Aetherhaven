package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/** Eligibility is captured at the kill, not later when a delayed corpse drops its items. */
public final class GuardKillClaim implements Component<EntityStore> {
    static ComponentType<EntityStore,GuardKillClaim> type;
    String owner="";
    public static final BuilderCodec<GuardKillClaim> CODEC=BuilderCodec.builder(GuardKillClaim.class,GuardKillClaim::new)
        .append(new KeyedCodec<>("Owner",Codec.STRING),(c,v)->c.owner=v,c->c.owner).add().build();
    public static void register(ComponentRegistryProxy<EntityStore> registry){type=registry.registerComponent(GuardKillClaim.class,"AetherhavenGuardKillClaim",CODEC);registry.registerSystem(new Deaths());}
    @Override public GuardKillClaim clone(){var c=new GuardKillClaim();c.owner=owner;return c;}
    static final class Deaths extends DeathSystems.OnDeathSystem {
        @Override public Query<EntityStore> getQuery(){return NPCEntity.getComponentType();}
        @Override public void onComponentAdded(Ref<EntityStore> victim,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var info=death.getDeathInfo();if(info==null||!(info.getSource() instanceof Damage.EntitySource source))return;
            var killer=source.getRef();if(killer==null||!killer.isValid())return;
            var town=GuardSpoils.town(killer,store);var uuid=store.getComponent(killer,UUIDComponent.getComponentType());
            var guard=town==null||uuid==null?null:GuardSpoils.guard(town,uuid.getUuid());
            if(guard==null||!guard.collectsMobDrops()||GuardFollowPlayerSystem.shouldSkipPatrol(store.getComponent(killer,GuardFollowPlayerState.getComponentType()))
                ||store.getComponent(killer,com.hexvane.aetherhaven.rts.GuardRtsCommandState.getComponentType())!=null)return;
            if(AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(store.getExternalData().getWorld(),AetherhavenPlugin.get()).routesForGuard(uuid.getUuid()).isEmpty())return;
            var claim=new GuardKillClaim();claim.owner=GuardSpoils.owner(town,guard);buffer.putComponent(victim,type,claim);
        }
    }
}
