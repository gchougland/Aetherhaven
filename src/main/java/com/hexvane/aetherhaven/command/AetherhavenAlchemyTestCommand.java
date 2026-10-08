package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.alchemy.*;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector3d;

/** Real effect ticks and damage filters on a temporary entity, never on a player or resident. */
final class AetherhavenAlchemyTestCommand extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    AetherhavenAlchemyTestCommand(){super("alchemy","Exercise charged flasks, restoration, boosts and wards in an isolated world");setPermissionGroups("hytale:WorldEditor");}
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if(!RUNNING.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("An alchemy check is already running"));
        var universe=Universe.get();String name="aetherhaven_alchemy_"+UUID.randomUUID().toString().replace("-","");var path=universe.validateWorldPath(name);
        if(Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture exists"));}
        var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new VoidWorldGenProvider());config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);
        config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
        return universe.makeWorld(name,path,config).thenCompose(world -> world.getChunkStore().getChunkSectionReferenceAsync(0,4,0,GetChunkFlags.SET_TICKING)
            .thenApplyAsync(unused->spawn(world),world)
            .thenCompose(ref->delay(world).thenApplyAsync(unused->start(world,ref),world))
            .thenCompose(fixture->delay(world).thenRunAsync(()->restorationAndWards(world,fixture),world)
                .thenCompose(unused->delay(world)).thenRunAsync(()->finish(world,fixture),world))
            .whenCompleteAsync((unused,error)->{universe.removeWorld(name);report(ctx,error==null?
                "Alchemy isolated check PASSED: combined restoration, five real sips, no sixth sip, stronger boost protection, health and mana maxima, explosion and poison immunity, ordinary damage, poison cleansing and expiry.":"Alchemy isolated check FAILED: "+error);},world))
            .whenComplete((unused,error)->RUNNING.set(false));
    }
    private static CompletableFuture<Void> delay(World world) { return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(500,TimeUnit.MILLISECONDS,world)); }
    private record Fixture(Ref<EntityStore> ref,EntityStatMap stats,EffectControllerComponent effects,SimpleItemContainer bag,float healthMax,float manaMax) {}
    private static Ref<EntityStore> spawn(World world) {
        var profile=AetherhavenPlugin.get().getEquipmentProfileCatalog().byId("guard_mage");
        require(profile!=null,"Missing native fixture role");
        var spawned=NPCPlugin.get().spawnNPC(world.getEntityStore().getStore(),profile.getGuardNpcRole(),null,new Vector3d(8.5,128,8.5),Rotation3f.ZERO);
        require(spawned!=null,"Could not spawn native fixture entity");
        var stats=world.getEntityStore().getStore().getComponent(spawned.first(),EntityStatMap.getComponentType());
        require(stats!=null&&stats.get(DefaultEntityStatTypes.getMana())!=null,"Native fixture has no mana stat");
        // NPC mana defaults to zero. Give the test target a usable pool before
        // sampling its baseline; potion boosts must still come from effect ticks.
        stats.putModifier(DefaultEntityStatTypes.getMana(),"fixture:alchemy_mana",
            new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,100));
        return spawned.first();
    }
    private static Fixture start(World world,Ref<EntityStore> ref) {
        var store=world.getEntityStore().getStore();
        // Native max-stat recalculation only ticks legacy living entities. A bare ECS
        // dummy would tick instant effects but silently skip raw boost modifiers.
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());
        require(stats!=null,"Native fixture has no stat map");
        int health=DefaultEntityStatTypes.getHealth(),mana=DefaultEntityStatTypes.getMana();
        require(stats.get(health)!=null&&stats.get(mana)!=null,"Native health and mana must exist");
        float healthMax=stats.get(health).getMax(),manaMax=stats.get(mana).getMax();
        require(healthMax>0&&manaMax>0,"Native fixture needs positive health and mana: health="+healthMax+", mana="+manaMax);
        stats.setStatValue(health,healthMax*.25f);stats.setStatValue(mana,manaMax*.25f);
        var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());
        require(effects!=null,"Native fixture has no effect controller");
        var bag=new SimpleItemContainer((short)1);
        put(bag,1,List.of("Plant_Crop_Health1","Plant_Crop_Mana1"));
        require(AlchemyFlaskService.drink(ref,store,bag,(short)0),"Initial mixed sip failed");
        require(!AlchemyFlaskService.drink(ref,store,bag,(short)0),"Empty flask drank twice");
        return new Fixture(ref,stats,effects,bag,healthMax,manaMax);
    }
    private static void restorationAndWards(World world,Fixture f) {
        var store=world.getEntityStore().getStore();int health=DefaultEntityStatTypes.getHealth(),mana=DefaultEntityStatTypes.getMana();
        require(f.stats.get(health).get()>=f.healthMax*.395f,"Health ingredient did not restore 15 percent");
        require(f.stats.get(mana).get()>=f.manaMax*.395f,"Mana ingredient did not restore 15 percent");
        put(f.bag,5,List.of("Plant_Crop_Mushroom_Boomshroom_Small","Plant_Crop_Mushroom_Common_Lime","Plant_Crop_Mushroom_Glowing_Red"));
        for(int remaining=4;remaining>=0;remaining--) {
            require(AlchemyFlaskService.drink(f.ref,store,f.bag,(short)0),"Charged sip failed");
            require(AlchemyFlask.charges(f.bag.getItemStack((short)0))==remaining,"Sip consumed the wrong number of charges");
        }
        require(!AlchemyFlaskService.drink(f.ref,store,f.bag,(short)0),"Sixth sip duplicated charges");
        for(String cause:List.of("Environment","Poison")) {
            var damage=new Damage(new Damage.EnvironmentSource(cause.equals("Environment")?"explosion":"alchemy_fixture"),DamageCause.getAssetMap().getAsset(cause),10);
            DamageSystems.executeDamage(f.ref,store,damage);require(damage.isCancelled(),cause+" ward failed");
        }
        var projectile=new Damage(new Damage.ProjectileSource(f.ref,f.ref),DamageCause.getAssetMap().getAsset("Environment"),10);
        DamageSystems.executeDamage(f.ref,store,projectile);require(projectile.isCancelled(),"Projectile blast ward failed");
        var ordinary=new Damage(new Damage.EnvironmentSource("alchemy_fixture"),DamageCause.getAssetMap().getIndex("Physical"),1);
        DamageSystems.executeDamage(f.ref,store,ordinary);require(!ordinary.isCancelled(),"Ward incorrectly grants ordinary damage immunity");
        f.effects.addEffect(f.ref,EntityEffect.getAssetMap().getAsset("Poison_T3"),store);
        put(f.bag,1,List.of("Plant_Crop_Mushroom_Cap_Red","Plant_Crop_Mushroom_Glowing_Blue"));
        require(AlchemyFlaskService.drink(f.ref,store,f.bag,(short)0),"Lower boost mixture failed");
        require(!f.effects.getActiveEffects().containsKey(EntityEffect.getAssetMap().getIndex("Aetherhaven_Alchemy_HealthBoost1")),"Weaker boost stacks with stronger boost");
    }
    private static void finish(World world,Fixture f) {
        var store=world.getEntityStore().getStore();
        require(f.stats.get(DefaultEntityStatTypes.getHealth()).getMax()>=f.healthMax+19.9f,"Health Boost 2 maximum did not apply");
        require(f.stats.get(DefaultEntityStatTypes.getMana()).getMax()>=f.manaMax+19.9f,"Mana Boost 2 maximum did not apply");
        require(!f.effects.getActiveEffects().containsKey(EntityEffect.getAssetMap().getIndex("Poison_T3")),"Poison ward did not cleanse poison");
        for(String id:List.of("Aetherhaven_Alchemy_BlastWard1","Aetherhaven_Alchemy_PoisonWard1"))f.effects.removeEffect(f.ref,EntityEffect.getAssetMap().getIndex(id),store);
        var damage=new Damage(new Damage.EnvironmentSource("explosion"),DamageCause.getAssetMap().getAsset("Environment"),1);
        DamageSystems.executeDamage(f.ref,store,damage);require(!damage.isCancelled(),"Expired blast ward still blocks damage");
        store.removeEntity(f.ref,RemoveReason.REMOVE);
    }
    private static void put(SimpleItemContainer bag,int capacity,List<String> mixture) {
        var flask=new ItemStack(AlchemyFlask.ITEM,1);for(int n=1;n<capacity;n++)flask=AlchemyFlask.upgrade(flask);
        bag.setItemStackForSlot((short)0,AlchemyFlask.fill(flask,mixture),false);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void report(CommandContext ctx,String text){ctx.sendMessage(Message.raw(text));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);}
}
