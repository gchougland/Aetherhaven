package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.equipment.GuardKitService;
import com.hexvane.aetherhaven.guild.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.*;
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

/** Native equipment/stat/damage pipeline fixture, not a combat AI or projectile flight benchmark. */
final class AetherhavenGuardStatsTestCommand extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    AetherhavenGuardStatsTestCommand(){super("guardstats","Check all guard kit stats and damage in an isolated world");setPermissionGroups("hytale:WorldEditor");}
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if(!RUNNING.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("A guard check is already running"));
        String name="aetherhaven_guard_stats_"+UUID.randomUUID().toString().replace("-","");
        var universe=Universe.get();var path=universe.validateWorldPath(name);
        if(Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture exists"));}
        var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new VoidWorldGenProvider());config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);
        config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
        return universe.makeWorld(name,path,config).thenCompose(world->
            world.getChunkStore().getChunkSectionReferenceAsync(0,4,0,GetChunkFlags.SET_TICKING)
                .thenRunAsync(()->exercise(world,ctx),world).whenCompleteAsync((unused,error)->{
                    universe.removeWorld(name);
                    report(ctx,error==null?"Guard stats isolated check: PASSED. Twenty kits, native armor and weapon application, health and wounds, foreign modifiers, melee and projectile-source damage, protection and speed effects."
                        :"Guard stats isolated check: FAILED: "+error);
                })).whenComplete((unused,error)->RUNNING.set(false));
    }
    private static void exercise(World world,CommandContext ctx) {
        var plugin=AetherhavenPlugin.get();var store=world.getEntityStore().getStore();
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),world.getName(),0,128,0,1,1,0);
        manager.markDirty(town);
        int healthId=DefaultEntityStatTypes.getHealth();
        var dummyStats=new EntityStatMap();dummyStats.update();
        dummyStats.putModifier(healthId,"fixture:health",new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,1000));
        var dummyHolder=EntityStore.REGISTRY.newHolder();
        dummyHolder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(UUID.randomUUID()));
        dummyHolder.addComponent(EntityStatMap.getComponentType(),dummyStats);
        var dummy=store.addEntity(dummyHolder,AddReason.SPAWN);
        try {
            for(String cls:List.of("knight","archer","rogue","mage")) {
                String character="guard_stats_"+cls;
                var base=plugin.getEquipmentProfileCatalog().byId("guard_"+cls);
                require(base!=null,"Missing class "+cls);
                var spawn=NPCPlugin.get().spawnNPC(store,base.getGuardNpcRole(),null,new Vector3d(8.5,128,8.5),Rotation3f.ZERO);
                require(spawn!=null,"Could not spawn "+cls);var ref=spawn.first();
                try {
                    var uuid=store.getComponent(ref,UUIDComponent.getComponentType()).getUuid();
                    var progress=new TownsfolkProgress();town.getTownsfolkProgress().put(character,progress);
                    var guard=new HiredGuardRecord(character,uuid,"guard_"+cls,true);town.getHiredGuardRecords().add(guard);
                    store.putComponent(ref,TownVillagerBinding.getComponentType(),new TownVillagerBinding(town.getTownId(),TownVillagerBinding.KIND_GUARD,null,null));
                    store.putComponent(ref,TownsfolkCharacterBinding.getComponentType(),new TownsfolkCharacterBinding(character,"",TownsfolkAssignmentKinds.GUARD,"Male_Human_01",List.of()));
                    var stats=store.getComponent(ref,EntityStatMap.getComponentType());
                    require(stats!=null&&stats.get(healthId)!=null,"Missing native health");
                    stats.putModifier(healthId,"fixture:foreign",new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,30));
                    float previousHealth=0,previousDamage=0;
                    for(String tier:List.of("copper","iron","thorium","cobalt","adamantite")) {
                        String profile="guard_"+cls+"_"+tier;
                        var equipment=plugin.getEquipmentProfileCatalog().byId(profile);require(equipment!=null,"Missing "+profile);
                        var expected=GuardCombatStats.derive(progress,equipment);
                        var health=stats.get(healthId);stats.setStatValue(healthId,health.getMax()*.5f);
                        GuardKitService.apply(store,town,character,plugin,profile);
                        close(expected.health()+30,health.getMax(),profile+" native max health");
                        close(.5f,health.get()/health.getMax(),profile+" wounded fraction");
                        require(health.getModifiers().containsKey("fixture:foreign"),"Lost foreign modifier");
                        require(expected.health()>previousHealth&&expected.damageMultiplier()>previousDamage,"Tier is not an improvement: "+profile);
                        previousHealth=expected.health();previousDamage=expected.damageMultiplier();
                        var armor=store.getComponent(ref,InventoryComponent.Armor.getComponentType());
                        require(armor!=null,"Missing armor inventory");
                        var equipped=new HashSet<String>();
                        for(short slot=0;slot<armor.getInventory().getCapacity();slot++) {
                            var item=armor.getInventory().getItemStack(slot);if(item!=null)equipped.add(item.getItemId());
                        }
                        require(equipped.containsAll(equipment.getArmorItemIds()),"Incomplete armor: "+profile);
                        var hotbar=store.getComponent(ref,InventoryComponent.Hotbar.getComponentType());
                        require(hotbar!=null&&equipment.getHotbarSlots().getFirst().getItemId().equals(hotbar.getInventory().getItemStack((short)0).getItemId()),"Wrong weapon: "+profile);
                        var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());
                        int speed=EntityEffect.getAssetMap().getIndex("Aetherhaven_Guard_Speed_"+expected.speed());
                        require(effects!=null&&effects.getActiveEffects().containsKey(speed),"Missing speed effect: "+profile);
                        for(int s=1;s<=40;s++)if(s!=expected.speed())require(!effects.getActiveEffects().containsKey(EntityEffect.getAssetMap().getIndex("Aetherhaven_Guard_Speed_"+s)),"Stacked speed effect");
                        float before=health.get();
                        var hit=new Damage(new Damage.EnvironmentSource("guard_stats_fixture"),DamageCause.PHYSICAL,10);
                        DamageSystems.executeDamage(ref,store,hit);
                        require(!hit.isCancelled(),"Incoming probe cancelled: "+profile);
                        // Native ApplyDamage rounds once after all damage filters.
                        close(Math.round(10*(1-expected.damageReduction())),before-health.get(),profile+" protection");
                        for(boolean projectile:List.of(false,true)) {
                            var target=dummyStats.get(healthId);dummyStats.setStatValue(healthId,target.getMax());float start=target.get();
                            var attack=new Damage(projectile?new Damage.ProjectileSource(ref,dummy):new Damage.EntitySource(ref),DamageCause.PHYSICAL,10);
                            DamageSystems.executeDamage(dummy,store,attack);
                            require(!attack.isCancelled(),"Outgoing probe cancelled: "+profile);
                            close(Math.round(10*expected.damageMultiplier()),start-target.get(),profile+" outgoing "+projectile);
                        }
                        report(ctx,String.format(Locale.ROOT,"%s: %.0f health, %.1f%% protection, %.0f%% attack power; native probes passed.",profile,expected.health(),100*expected.damageReduction(),100*expected.damageMultiplier()));
                    }
                    town.getHiredGuardRecords().remove(guard);
                } finally {if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);}
            }
        } finally {if(dummy.isValid())store.removeEntity(dummy,RemoveReason.REMOVE);manager.removeTown(town.getTownId());}
    }
    private static void close(float expected,float actual,String label){require(Math.abs(expected-actual)<.01f,label+": expected "+expected+", got "+actual);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void report(CommandContext ctx,String message){ctx.sendMessage(Message.raw(message));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);}
}
