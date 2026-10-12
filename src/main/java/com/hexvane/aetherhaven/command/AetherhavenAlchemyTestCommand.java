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
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ExplodeInteraction;

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
                .thenCompose(unused->delay(world)).thenRunAsync(()->finish(world,fixture),world)
                .thenCompose(unused->brewed(world,fixture,AlchemyBrew.Quality.GOOD))
                .thenCompose(unused->brewed(world,fixture,AlchemyBrew.Quality.GREAT))
                .thenCompose(unused->brewed(world,fixture,AlchemyBrew.Quality.PERFECT))
                .thenRunAsync(()->cleanUp(world,fixture),world))
            .whenCompleteAsync((unused,error)->{universe.removeWorld(name);report(ctx,error==null?
                "Alchemy isolated check PASSED: combined restoration, five real sips, no sixth sip, stronger boost protection, health and mana maxima, explosion and poison immunity, ordinary damage, poison cleansing and expiry. "+brewReport()+" "+fillReport:"Alchemy isolated check FAILED: "+error);},world))
            .whenComplete((unused,error)->RUNNING.set(false));
    }
    private static CompletableFuture<Void> delay(World world) { return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(500,TimeUnit.MILLISECONDS,world)); }
    private static volatile String fillReport="";
    /** What each scripted brew did, in the order Good, Great, Perfect. Only one check runs at a time. */
    private static final float[] wardSeconds=new float[3],restored=new float[3];
    private static final int[] beatsInside=new int[3];
    private static volatile float healthBeforeSip;
    private record Fixture(Ref<EntityStore> ref,EntityStatMap stats,EffectControllerComponent effects,SimpleItemContainer bag,float healthMax,float manaMax) {}
    private static Ref<EntityStore> spawn(World world) {
        var profile=AetherhavenPlugin.get().getEquipmentProfileCatalog().byId("guard_mage");
        require(profile!=null,"Missing native fixture role");
        // The void has no ground, and only this one section is awake: a fixture left to fall drops into the sleeping
        // section below within half a second, where the engine puts it into storage and its reference dies.
        for(int x=7;x<=9;x++)for(int z=7;z<=9;z++)
            require(com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.setBlockByKey(world,x,129,z,"Rock_Stone",0),"Floor placement failed");
        var spawned=NPCPlugin.get().spawnNPC(world.getEntityStore().getStore(),profile.getGuardNpcRole(),null,new Vector3d(8.5,130,8.5),Rotation3f.ZERO);
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
            var damage=new Damage(cause.equals("Environment")?ExplodeInteraction.DAMAGE_SOURCE_EXPLOSION:new Damage.EnvironmentSource("alchemy_fixture"),DamageCause.getAssetMap().getAsset(cause),10);
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
        var damage=new Damage(ExplodeInteraction.DAMAGE_SOURCE_EXPLOSION,DamageCause.getAssetMap().getAsset("Environment"),1);
        DamageSystems.executeDamage(f.ref,store,damage);require(!damage.isCancelled(),"Expired blast ward still blocks damage");
    }
    private static void cleanUp(World world,Fixture f) {
        world.getEntityStore().getStore().removeEntity(f.ref,RemoveReason.REMOVE);
        fillReport=cauldronFill();
    }
    /** Someone paying attention: aims at where the page says the band is heading. */
    private static AlchemyBrew.Move attentive(AlchemyBrew brew) {
        var best=AlchemyBrew.Move.WAIT;
        for(var move:AlchemyBrew.Move.values())if(Math.abs(brew.after(move)-brew.expectedCentre())<Math.abs(brew.after(best)-brew.expectedCentre()))best=move;
        return best;
    }
    private static CompletableFuture<Void> brewed(World world,Fixture f,AlchemyBrew.Quality wanted) {
        return CompletableFuture.runAsync(()->brewBottleAndDrink(world,f,wanted),world).thenCompose(unused->delay(world))
            .thenRunAsync(()->measureSip(f,wanted),world);
    }
    /**
     * A scripted brew with the loaded rules, bottled through the cauldron's own fill into a real flask and drunk by the
     * fixture. Good is the skip path: the flask is filled without brewing at all.
     */
    private static void brewBottleAndDrink(World world,Fixture f,AlchemyBrew.Quality wanted) {
        var store=world.getEntityStore().getStore();var rules=AlchemyBrew.rules();
        String rose="Plant_Crop_Health1",fireFlower="Plant_Flower_Flax_Orange";var mix=List.of(rose,fireFlower);
        AlchemyBrew brew=null;
        if(wanted!=AlchemyBrew.Quality.GOOD)for(long seed=1;seed<=5000&&brew==null;seed++) {
            var candidate=new AlchemyBrew(rules,seed,mix.size(),3);
            while(!candidate.finished())candidate.play(attentive(candidate));
            if(candidate.quality()==wanted)brew=candidate;
        }
        require(wanted==AlchemyBrew.Quality.GOOD||brew!=null,"No scripted brew reached "+wanted);
        var quality=brew==null?AlchemyBrew.Quality.GOOD:brew.quality();
        beatsInside[wanted.ordinal()]=brew==null?-1:brew.inside();
        var bag=new SimpleItemContainer((short)3);var flask=new ItemStack(AlchemyFlask.ITEM,1);
        bag.setItemStackForSlot((short)0,flask,false);bag.setItemStackForSlot((short)1,new ItemStack(rose,1),false);bag.setItemStackForSlot((short)2,new ItemStack(fireFlower,1),false);
        require(AlchemyBottling.fill(bag,(short)0,flask,mix,null,quality).filled(),"Could not bottle the "+wanted+" brew");
        require(AlchemyFlask.quality(bag.getItemStack((short)0))==wanted&&ItemStack.isEmpty(bag.getItemStack((short)1)),"The flask does not carry "+wanted);
        // The flask is saved and loaded before it is drunk, as it would be over a relog.
        var codec=com.hypixel.hytale.server.core.inventory.ItemStack.CODEC;
        bag.setItemStackForSlot((short)0,codec.decode(codec.encode(bag.getItemStack((short)0),new com.hypixel.hytale.codec.ExtraInfo()),new com.hypixel.hytale.codec.ExtraInfo()),false);
        require(AlchemyFlask.quality(bag.getItemStack((short)0))==wanted,"The native item codec lost the brew quality");
        int health=DefaultEntityStatTypes.getHealth();
        f.stats.setStatValue(health,f.stats.get(health).getMax()*.25f);healthBeforeSip=f.stats.get(health).get();
        require(AlchemyFlaskService.drink(f.ref,store,bag,(short)0),"The "+wanted+" potion could not be drunk");
        var ward=f.effects.getActiveEffects().get(EntityEffect.getAssetMap().getIndex("Aetherhaven_Alchemy_FireWard1"));
        require(ward!=null,"Fire Ward was not applied");
        wardSeconds[wanted.ordinal()]=ward.getRemainingDuration();
        float expected=EntityEffect.getAssetMap().getAsset("Aetherhaven_Alchemy_FireWard1").getDuration()*rules.duration(wanted);
        require(Math.abs(ward.getRemainingDuration()-expected)<.5f,wanted+" Fire Ward runs "+ward.getRemainingDuration()+" seconds, expected "+expected);
    }
    private static void measureSip(Fixture f,AlchemyBrew.Quality wanted) {
        int health=DefaultEntityStatTypes.getHealth();var rules=AlchemyBrew.rules();
        float max=f.stats.get(health).getMax(),gained=(f.stats.get(health).get()-healthBeforeSip)/max*100f;
        restored[wanted.ordinal()]=gained;
        float expected=15f*rules.restore(wanted);
        require(gained>=expected-.3f&&gained<=expected+1f,wanted+" Blood Rose restored "+gained+"% of health, expected "+expected+"%");
    }
    private static String brewReport() {
        var rules=AlchemyBrew.rules();
        return String.format(java.util.Locale.ROOT,"Brew check: scripted brews scored %d and %d of %d beats for Great and Perfect, Good was bottled without brewing. "
            +"Drunk by a real entity, Fire Ward ran %.1f / %.1f / %.1f seconds and Blood Rose restored %.1f%% / %.1f%% / %.1f%% of health for Good / Great / Perfect.",
            beatsInside[1],beatsInside[2],rules.beats(),wardSeconds[0],wardSeconds[1],wardSeconds[2],restored[0],restored[1],restored[2]);
    }
    /**
     * The cauldron's Fill flask button on a real town record with the plugin's real catalogs. The page opens, the town
     * changes behind it (storage, finished goods, a courier's reservation, a Mycologist's harvest) and the flask is
     * moved to another slot. The call below is the one the page makes; only the click itself is not exercised.
     */
    private static String cauldronFill() {
        var plugin=AetherhavenPlugin.get();var owner=UUID.randomUUID();
        String rose="Plant_Crop_Health1",cap="Plant_Crop_Mushroom_Cap_Red";
        var town=new com.hexvane.aetherhaven.town.TownRecord();town.setTownId(UUID.randomUUID());town.setOwner(owner,"Fixture");
        town.getClaimedTerritoryChunks().add(com.hexvane.aetherhaven.town.ClaimedTerritoryChunkRecord.of(0,0));
        town.getResidentNpcRecords().add(new com.hexvane.aetherhaven.town.ResidentNpcRecord("druid","druid",null,UUID.randomUUID()));
        require(town.getAlchemyLaboratory().upgrade(0),"Could not open a second ingredient slot");
        var type=com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.MYCOLOGY;var sites=town.getApprenticeWorkplaces();
        require(sites.unlock(town,type)==null&&sites.save(town,null,type,new com.hexvane.aetherhaven.workplace.WorkplaceArea(0,100,0,4,4),"Fixture logs")==null,"Could not mark a mushroom site");
        var plan=plugin.getGrowerPlans().find(type);require(plan!=null,"Mycology plan did not load");
        var yard=sites.sites().getFirst().grower();
        yard.sync(plan,List.of(new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(0,100,0)),null);
        require(yard.choose(plan,"cap_red",10,yard.revision())==null,"Could not choose Red Cap");
        java.util.function.Supplier<List<AlchemyTownStock.Source>> sources=()->AlchemyTownStock.sources(town,plugin);
        require(sources.get().size()==1,"The grower site is not town stock for the cauldron");
        var warehouse=town.getWarehouse();store(warehouse,new ItemStack(rose,1));
        var bag=new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(new SimpleItemContainer((short)4),new SimpleItemContainer((short)4));
        var flask=AlchemyFlask.upgrade(new ItemStack(AlchemyFlask.ITEM,1));
        bag.setItemStackForSlot((short)0,flask,false);bag.setItemStackForSlot((short)1,new ItemStack(rose,1),false);
        var mix=List.of(rose,cap);
        // The page opens and draws this.
        var opened=AlchemyTownStock.snapshot(warehouse,sources.get());long revision=warehouse.revision();
        require(opened.count(rose)==1&&opened.count(cap)==0,"Opening stock differs");
        // Behind the open page: a craft is finished, a player stores fibre, a courier is promised some of it, the flask is moved.
        require(warehouse.acceptCompletedOutput(new ItemStack("Ingredient_Bar_Iron",3)),"Finished goods refused");
        store(warehouse,new ItemStack("Ingredient_Fibre",9));
        require(warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"alchemy_fill_fixture",
            List.of(new com.hypixel.hytale.server.core.inventory.MaterialQuantity("Ingredient_Fibre",null,null,4,null)),List.of(new ItemStack("Ingredient_Bar_Iron",1)))!=null,"Could not reserve courier supplies");
        bag.setItemStackForSlot((short)0,null,false);bag.setItemStackForSlot((short)6,flask,false);
        // Truly short: no Red Cap exists anywhere yet. The answer names it and nothing is spent.
        var early=AlchemyBottling.bottle(town,owner,sources,bag,(short)0,flask,mix);
        require(early.outcome()==AlchemyBottling.Outcome.MISSING&&early.missing().equals(List.of(new AlchemyBottling.Missing(cap,2))),"A missing ingredient was not named: "+early);
        require(flask.equals(bag.getItemStack((short)6))&&bag.getItemStack((short)1).getQuantity()==1&&warehouse.craftingStock().count(rose)==1,"A refused fill spent something");
        // The Mycologist's harvest lands in the site's own store.
        for(int i=0;i<40&&yard.stored()==0;i++) {
            var task=yard.next(plan,yard.chosen(plan),bed->true,null);
            if(task==null){yard.advance(plan,plan.stageMinutes(),true,true);continue;}
            for(int tick=0;tick<200&&!yard.work(plan,task.bed(),task.job(),.25,true,1);tick++){}
            yard.complete(plan,yard.chosen(plan),task.bed(),task.job(),false,()->true);
        }
        long grown=yard.amount(cap);require(grown>=2,"The fixture site grew "+grown+" Red Caps");
        warehouse.markProductionWithdrawal();
        boolean unchanged=opened.equals(AlchemyTownStock.snapshot(warehouse,sources.get()));
        require(!unchanged&&warehouse.revision()>revision,"The fixture town did not change behind the page");
        var filled=AlchemyBottling.bottle(town,owner,sources,bag,(short)0,flask,mix);
        var now=bag.getItemStack((short)6);
        require(filled.filled()&&filled.flaskSlot()==6&&AlchemyFlask.charges(now)==2&&AlchemyFlask.ingredients(now).equals(mix),"Fill refused after the town changed: "+filled);
        require(com.hexvane.aetherhaven.inventory.InventoryMaterials.count(bag,rose)==0&&warehouse.craftingStock().count(rose)==0&&yard.amount(cap)==grown-2,"Fill took the wrong amounts");
        require(warehouse.count(com.hexvane.aetherhaven.warehouse.TownWarehouse.Page.GENERAL,"Ingredient_Fibre")==9,"Fill touched unrelated storage");
        require(town.getAlchemyLaboratory().discovered(rose)&&town.getAlchemyLaboratory().discovered(cap),"Fill did not record the discovery");
        return "Cauldron fill check: with no Red Cap anywhere the fill answered "+early.outcome()+" "+early.missing()+" and spent nothing; after the harvest it answered "+filled.outcome()
            +" with "+AlchemyFlask.charges(now)+" of "+AlchemyFlask.capacity(now)+" charges in slot "+filled.flaskSlot()+" (the page had it in slot 0), although storage went from revision "
            +revision+" to "+warehouse.revision()+" and the view the page opened with no longer matched. Taken: 1 Blood Rose from the bag, 1 from storage, 2 Red Cap from the mushroom site.";
    }
    private static void store(com.hexvane.aetherhaven.warehouse.TownWarehouse warehouse,ItemStack stack) {
        var supply=new SimpleItemContainer((short)1);supply.setItemStackForSlot((short)0,stack,false);
        require(warehouse.deposit(com.hexvane.aetherhaven.warehouse.TownWarehouse.Page.GENERAL,supply,(short)0,stack,
            com.hexvane.aetherhaven.warehouse.TownWarehouse.Amount.STACK).moved()==stack.getQuantity(),"Could not store "+stack.getItemId());
    }
    private static void put(SimpleItemContainer bag,int capacity,List<String> mixture) {
        var flask=new ItemStack(AlchemyFlask.ITEM,1);for(int n=1;n<capacity;n++)flask=AlchemyFlask.upgrade(flask);
        bag.setItemStackForSlot((short)0,AlchemyFlask.fill(flask,mixture),false);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void report(CommandContext ctx,String text){ctx.sendMessage(Message.raw(text));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);}
}
