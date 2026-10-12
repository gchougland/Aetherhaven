package com.hexvane.aetherhaven.hunter;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.*;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.rts.RtsGuardCombatSupport;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.*;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;
import java.lang.ref.WeakReference;
import java.util.*;

/** Player started survival runs. Real combat, one opponent, no offline progress or automatic restart. */
public final class HuntService extends TickingSystem<EntityStore> {
    public static final String KIND="monster_hunter",CONSTRUCTION="plot_monster_hunter",BEACON="Aetherhaven_Hunt_Beacon";
    private static final Map<World,Map<UUID,Run>> RUNS=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<World,Double> polls=new WeakHashMap<>();
    static final class Run {
        WeakReference<Ref<EntityStore>> hunter=new WeakReference<>(null),foe=new WeakReference<>(null),portal=new WeakReference<>(null);
        UUID id,plot;
        HuntFamily family;
        Map<String,Integer> spoils=Map.of();
        Vector3d center,loot;
        /** How the lodge was turned when it was placed. The arena's pieces turn with it. */
        com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation turn=com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.None;
        boolean collecting,down,secondWind;
        boolean standard,brazier,sigil;
        Set<Integer> previousEffects=Set.of();
        double elapsed,collection,interval;
        float lastFoeHealth,lastHunterHealth;
        long lastTick,searchCursor,planningUntil;
        String status="Choose creatures, then start a run.";
        HuntArenaLayout layout() {return HuntArenaLayout.of(center,turn);}
    }
    private static Map<UUID,Run> runs(World world) { synchronized(RUNS){return RUNS.computeIfAbsent(world,w->new HashMap<>());} }
    private static Run run(Store<EntityStore> store,UUID town) { return runs(store.getExternalData().getWorld()).get(town); }
    public static String status(Store<EntityStore> store,UUID town) {var r=run(store,town);return r==null?"The Hunter must be nearby in a loaded lodge.":r.status;}
    public static boolean owns(Ref<EntityStore> ref,Store<EntityStore> store) {
        var b=store.getComponent(ref,TownVillagerBinding.getComponentType());var r=b==null?null:run(store,b.getTownId());
        return r!=null&&(r.id!=null||System.nanoTime()<r.planningUntil)&&ref.equals(r.hunter.get());
    }
    /** Refreshed only by a valid nearby planning page. Expires after dismissal or disconnect. */
    public static void holdForPlanning(UUID town,Ref<EntityStore> hunter,Store<EntityStore> store) {
        if(!alive(hunter,store)||VillagerFollowPlayerSystem.shouldSkipAutonomy(store.getComponent(hunter,VillagerFollowPlayerState.getComponentType())))return;
        var plugin=AetherhavenPlugin.get();
        var record=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(town);
        var plot=record==null?null:record.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),CONSTRUCTION);
        // A planning page must not pin the Hunter outside the lodge on his way home.
        if(plot==null||!inside(hunter,plot,store))return;
        var r=runs(store.getExternalData().getWorld()).computeIfAbsent(town,id->new Run());
        r.hunter=new WeakReference<>(hunter);r.planningUntil=System.nanoTime()+3_000_000_000L;
        if(r.id==null)NpcSupportUtil.setState(hunter,"AetherhavenStandStill",null,store);
    }
    /** Cheap shared preflight for the live page and transaction. Arena geometry is checked on Start. */
    public static String preparationIssue(TownRecord town,Ref<EntityStore> hunter,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();var state=town.getHunts();
        if(state.run()!=null)return "A run is already underway.";
        if(!alive(hunter,store))return "The Hunter is unavailable.";
        var binding=store.getComponent(hunter,TownVillagerBinding.getComponentType());
        var uuid=store.getComponent(hunter,UUIDComponent.getComponentType());
        if(binding==null||!KIND.equals(binding.getKind())||!town.getTownId().equals(binding.getTownId())||uuid==null||!TownAdmission.isMember(town,uuid.getUuid()))return "Recruit the Monster Hunter first.";
        if(state.unlocked().isEmpty())return "Unlock a creature before starting a run.";
        var plot=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),CONSTRUCTION);
        if(plot==null)return "Complete the Hunter's Lodge first.";
        if(VillagerFollowPlayerSystem.shouldSkipAutonomy(store.getComponent(hunter,VillagerFollowPlayerState.getComponentType())))return "End Follow before starting a run.";
        if(!inside(hunter,plot,store))return "The Hunter must return to the lodge before a run can start.";
        return null;
    }
    /** Called only by the authorized screen, on the owning world thread. */
    public static String start(TownRecord town,Ref<EntityStore> hunter,Store<EntityStore> store,UUID player,com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer inventory) {
        String issue=preparationIssue(town,hunter,store);if(issue!=null)return issue;
        var plugin=AetherhavenPlugin.get();var world=store.getExternalData().getWorld();var state=town.getHunts();
        var plot=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),CONSTRUCTION);
        var r=runs(world).computeIfAbsent(town.getTownId(),id->new Run());
        r.hunter=new WeakReference<>(hunter);r.plot=plot.getPlotId();r.turn=plot.resolvePrefabYaw();
        if(r.center==null||!beacon(world,r.center)||!insidePoint(r.center,plot))r.center=findBeacon(world,plot,r);
        if(r.center==null)return "Finding the Hunt Beacon. Try again in a moment.";
        if(!clearArena(world,plot,r.center))return "Keep a clear 9 by 9 floor around the Hunt Beacon.";
        for(var id:state.chosen()) {var family=HuntFamily.get(id);if(family==null||NPCPlugin.get().getIndex(family.role())<0)return "A chosen creature is unavailable.";}
        if(NPCPlugin.get().getIndex("Aetherhaven_Hunt_Portal")<0)return "The hunt portal is unavailable.";
        var furnishing=furnishings(world,plot);
        r.standard=furnishing.contains("Aetherhaven_Hunt_Practice_Standard");
        r.brazier=furnishing.contains("Aetherhaven_Hunt_Recovery_Brazier");
        r.sigil=furnishing.contains("Aetherhaven_Hunt_Warding_Sigil");
        String purchaseIssue=HuntPurchases.begin(town,player,inventory);if(purchaseIssue!=null)return purchaseIssue;
        r.id=state.run();
        var effects=store.getComponent(hunter,com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
        r.previousEffects=effects==null?Set.of():new HashSet<>(effects.getActiveEffects().keySet());
        r.elapsed=0;r.interval=3;r.collection=0;r.collecting=false;r.down=false;r.secondWind=false;r.lastTick=System.nanoTime();
        applyMaxHealth(hunter,state.maxHealth(),store);heal(hunter,Float.MAX_VALUE,store);
        save(town,store);r.status="Walking into the arena.";return null;
    }
    public static void retire(TownRecord town,Store<EntityStore> store) {
        var r=run(store,town.getTownId());if(r!=null)finish(r,town,store,"Run retired.",false);
        else {town.getHunts().end("Run retired.");save(town,store);}
    }
    @Override public void tick(float dt,int index,Store<EntityStore> store) {
        var world=store.getExternalData().getWorld();double step;
        synchronized(polls) {step=polls.getOrDefault(world,0d)+(Float.isFinite(dt)?Math.clamp(dt,0,.25):0);polls.put(world,step>=.2?0:step);}
        if(step<.2)return;
        double elapsed=Math.min(.25,step);world.execute(()->{if(!store.isShutdown())update(elapsed,store);});
    }
    private static void update(double dt,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var world=store.getExternalData().getWorld();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var hunters=new LinkedHashMap<UUID,Ref<EntityStore>>();var summons=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(),NPCEntity.getComponentType(),UUIDComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var b=chunk.getComponent(i,TownVillagerBinding.getComponentType());if(!KIND.equals(b.getKind()))continue;
                var town=manager.getTown(b.getTownId());
                if(town!=null&&TownAdmission.isMember(town,chunk.getComponent(i,UUIDComponent.getComponentType()).getUuid()))hunters.putIfAbsent(b.getTownId(),chunk.getReferenceTo(i));
            }
        });
        store.forEachChunk(HuntOpponent.getComponentType(),(chunk,buffer)->{for(int i=0;i<chunk.size();i++)summons.add(chunk.getReferenceTo(i));});
        for(var summon:summons) {
            var tag=store.getComponent(summon,HuntOpponent.getComponentType());var r=run(store,tag.town);
            if(r==null||r.id==null||!r.id.equals(tag.encounter)||!summon.equals((tag.portal?r.portal:r.foe).get()))store.removeEntity(summon,RemoveReason.REMOVE);
        }
        var map=runs(world);
        for(var entry:new ArrayList<>(map.entrySet()))if(!hunters.containsKey(entry.getKey())) {
            finish(entry.getValue(),manager.getTown(entry.getKey()),store,"Run interrupted while the Hunter was away.",false);map.remove(entry.getKey());
        }
        long now=System.nanoTime();
        for(var entry:hunters.entrySet()) {
            var town=manager.getTown(entry.getKey());var hunter=entry.getValue();var state=town.getHunts();
            var r=map.computeIfAbsent(entry.getKey(),id->new Run());r.hunter=new WeakReference<>(hunter);
            if(r.id==null&&state.run()!=null) {state.end("Run interrupted by a restart.");save(town,store);}
            var plot=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),CONSTRUCTION);
            if(plot!=null)r.turn=plot.resolvePrefabYaw();
            if(r.id==null) {
                if(now<r.planningUntil)NpcSupportUtil.setState(hunter,"AetherhavenStandStill",null,store);
                if(plot!=null&&(r.center==null||!beacon(world,r.center)))r.center=findBeacon(world,plot,r);
                continue;
            }
            boolean online=Universe.get().getPlayers().stream().anyMatch(p->town.hasMemberOrOwner(p.getUuid()));
            if(now-r.lastTick>2_000_000_000L||!online||plot==null||!alive(hunter,store)||!inside(hunter,plot,store)
                ||VillagerFollowPlayerSystem.shouldSkipAutonomy(store.getComponent(hunter,VillagerFollowPlayerState.getComponentType()))) {
                finish(r,town,store,"Run interrupted. Collected spoils were kept.",false);continue;
            }
            r.lastTick=now;if(r.down)continue;
            equipSword(hunter,store);
            if(!beacon(world,r.center)) {finish(r,town,store,"The Hunt Beacon was removed.",false);continue;}
            r.elapsed+=dt;
            var portal=r.portal.get();var foe=r.foe.get();
            // The Hunter's mark, the portal and the creature's first step turn with the lodge.
            var layout=r.layout();
            if(portal==null) {
                var stand=layout.hunterStand();
                if(position(hunter,store).distanceSquared(stand)>2.25) {
                    if(r.elapsed>90)finish(r,town,store,"The Hunter could not reach the arena.",false);else travel(hunter,stand,store);
                    continue;
                }
                var pair=NPCPlugin.get().spawnNPC(store,"Aetherhaven_Hunt_Portal",null,layout.portal(),layout.facing());
                if(pair==null) {finish(r,town,store,"The portal could not open.",false);continue;}
                portal=pair.first();var tag=new HuntOpponent(town.getTownId(),r.id,store.getComponent(hunter,UUIDComponent.getComponentType()).getUuid());tag.portal=true;
                store.putComponent(portal,HuntOpponent.getComponentType(),tag);r.portal=new WeakReference<>(portal);
            } else if(!portal.isValid()) {finish(r,town,store,"The portal was lost.",false);continue;}
            if(r.collecting) {
                r.status="Collecting spoils from wave "+(state.defeated()+1)+".";
                if(position(hunter,store).distanceSquared(r.loot)>2.25)travel(hunter,r.loot,store);
                else {
                    NpcSupportUtil.setState(hunter,"AetherhavenStandStill",null,store);r.collection+=dt;
                    if(r.collection>=3&&state.collect(r.id,state.defeated(),r.family,r.spoils)) {
                        heal(hunter,state.recoveryPerWave()+(r.brazier?4:0),store);remove(r.foe,store);r.collecting=false;r.interval=5;r.elapsed=0;save(town,store);
                    }
                }
                if(r.elapsed>90)finish(r,town,store,"The Hunter could not collect the spoils.",false);
                continue;
            }
            if(foe!=null) {
                if(alive(foe,store)) {
                    float foeHp=health(foe,store).get(),hunterHp=health(hunter,store).get();
                    if(foeHp<r.lastFoeHealth||hunterHp<r.lastHunterHealth)r.elapsed=0;
                    r.lastFoeHealth=foeHp;r.lastHunterHealth=hunterHp;
                }
                if(!alive(foe,store)||!inside(foe,plot,store)||r.elapsed>120) {finish(r,town,store,"The encounter was interrupted.",false);continue;}
                engage(hunter,foe,store);engage(foe,hunter,store);r.status="Wave "+(state.defeated()+1)+"   "+r.family.name();continue;
            }
            if(!state.room(state.next())) {r.status="Spoils storage is full. Collect items to continue this run.";NpcSupportUtil.setState(hunter,"AetherhavenStandStill",null,store);continue;}
            r.interval-=dt;if(r.interval>0) {r.status="Next wave in "+(int)Math.ceil(r.interval)+"s.";continue;}
            if(!clearArena(world,plot,r.center)) {r.status="Clear the arena floor to continue.";continue;}
            r.family=state.next();if(r.family==null) {finish(r,town,store,"The next creature is unavailable.",false);continue;}
            var spawn=layout.creatureSpawn();
            var pair=NPCPlugin.get().spawnNPC(store,r.family.role(),null,spawn,layout.facing());
            if(pair==null) {finish(r,town,store,"The creature could not enter the arena.",false);continue;}
            foe=pair.first();store.putComponent(foe,HuntOpponent.getComponentType(),new HuntOpponent(town.getTownId(),r.id,store.getComponent(hunter,UUIDComponent.getComponentType()).getUuid()));
            var hp=health(foe,store);if(hp!=null) {applyMaxHealth(foe,(float)(hp.getMax()*TownHunts.enemyHealthScale(state.defeated())),store);heal(foe,Float.MAX_VALUE,store);}
            r.foe=new WeakReference<>(foe);r.elapsed=0;r.collection=0;engage(hunter,foe,store);engage(foe,hunter,store);
        }
    }
    private static void save(TownRecord town,Store<EntityStore> store) {AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),AetherhavenPlugin.get()).updateTown(town);}
    private static boolean beacon(World world,Vector3d p) {var b=ChunkSectionBlockUtil.blockType(world,(int)Math.floor(p.x),(int)Math.floor(p.y)-1,(int)Math.floor(p.z));return b!=null&&BEACON.equals(b.getId());}
    private static Vector3d findBeacon(World world,PlotInstance plot,Run r) {
        var b=plot.toFootprint();long w=(long)b.getMaxX()-b.getMinX()+1,d=(long)b.getMaxZ()-b.getMinZ()+1,h=(long)b.getMaxY()-b.getMinY()+1;
        for(int i=0;i<2048;i++) {
            if(r.searchCursor>=w*d*h){r.searchCursor=0;return null;}
            long n=r.searchCursor++;int x=b.getMinX()+(int)(n%w),z=b.getMinZ()+(int)(n/w%d),y=b.getMinY()+(int)(n/(w*d));
            var p=new Vector3d(x+.5,y+1,z+.5);if(beacon(world,p))return p;
        }
        return null;
    }
    private static boolean clearArena(World world,PlotInstance plot,Vector3d p) {
        int x=(int)Math.floor(p.x),y=(int)Math.floor(p.y),z=(int)Math.floor(p.z);
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)if(!plot.containsWorldBlock(x+dx,y,z+dz)||!VillagerBlockUtil.isNpcStandColumn(world,x+dx,y,z+dz))return false;
        return true;
    }
    private static Set<String> furnishings(World world,PlotInstance plot) {
        var found=new HashSet<String>();var b=plot.toFootprint();
        // Only the occupied ground band is relevant. Every lookup uses loaded chunks.
        for(int x=b.getMinX();x<=b.getMaxX();x++)for(int z=b.getMinZ();z<=b.getMaxZ();z++)for(int y=b.getMinY();y<=Math.min(b.getMaxY(),b.getMinY()+5);y++) {
            var block=ChunkSectionBlockUtil.blockType(world,x,y,z);
            if(block!=null&&block.getId().startsWith("Aetherhaven_Hunt_"))found.add(block.getId());
        }
        return found;
    }
    private static void travel(Ref<EntityStore> ref,Vector3d to,Store<EntityStore> store) {
        var npc=store.getComponent(ref,NPCEntity.getComponentType());npc.setLeashPoint(new Vector3d(to));NpcSupportUtil.setState(ref,"AetherhavenAutonomy",null,store);
        VillagerDoorUtil.tryOpenDoorsTowardLeash(store.getExternalData().getWorld(),position(ref,store),to,null);
    }
    private static void engage(Ref<EntityStore> ref,Ref<EntityStore> target,Store<EntityStore> store) {
        if(!alive(ref,store)||!alive(target,store))return;
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        RtsGuardCombatSupport.lockCombatTarget(npc,target,store);
        var support=NpcSupportUtil.stateSupport(store,ref);if(support==null)return;
        // Native creatures use different top-level combat states. Never force a guard-only state.
        for(String state:List.of("Combat","Attack","Chase"))if(support.getStateHelper().getStateIndex(state)>=0) {
            String current=support.getStateName();
            if(!current.equals(state)&&!current.startsWith(state+"."))support.setState(ref,state,null,store);
            return;
        }
    }
    private static void equipSword(Ref<EntityStore> hunter,Store<EntityStore> store) {
        var hotbar=store.getComponent(hunter,com.hypixel.hytale.server.core.inventory.InventoryComponent.Hotbar.getComponentType());
        if(hotbar==null||hotbar.getInventory().getCapacity()==0)return;
        var held=hotbar.getActiveItem();
        if(held!=null&&"Weapon_Sword_Iron".equals(held.getItemId()))return;
        // Ordinary work and leisure clear held props. Restore the run's weapon after that cleanup.
        hotbar.getInventory().setItemStackForSlot((short)0,new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Sword_Iron",1));
        com.hexvane.aetherhaven.equipment.VillagerEquipmentService.markHotbarEquipmentDirty(hotbar,(byte)0,hunter,store);
    }
    private static Vector3d position(Ref<EntityStore> ref,Store<EntityStore> store) {return store.getComponent(ref,TransformComponent.getComponentType()).getPosition();}
    private static boolean insidePoint(Vector3d p,PlotInstance plot) {return plot.containsWorldBlock((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z));}
    private static boolean inside(Ref<EntityStore> ref,PlotInstance plot,Store<EntityStore> store) {return insidePoint(position(ref,store),plot);}
    private static EntityStatValue health(Ref<EntityStore> ref,Store<EntityStore> store) {var map=store.getComponent(ref,EntityStatMap.getComponentType());return map==null?null:map.get(DefaultEntityStatTypes.getHealth());}
    private static boolean alive(Ref<EntityStore> ref,Store<EntityStore> store) {return ref!=null&&ref.isValid()&&store.getComponent(ref,DeathComponent.getComponentType())==null&&health(ref,store)!=null&&health(ref,store).get()>0;}
    private static void heal(Ref<EntityStore> ref,float amount,Store<EntityStore> store) {var hp=health(ref,store);if(hp!=null&&hp.get()>0)store.getComponent(ref,EntityStatMap.getComponentType()).setStatValue(DefaultEntityStatTypes.getHealth(),Math.min(hp.getMax(),hp.get()+amount));}
    private static void applyMaxHealth(Ref<EntityStore> ref,float max,Store<EntityStore> store) {
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        store.getComponent(ref,EntityStatMap.getComponentType()).putModifier(DefaultEntityStatTypes.getHealth(),"aetherhaven:hunter_training",new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,max-npc.getRole().getInitialMaxHealth()));
    }
    private static void remove(WeakReference<Ref<EntityStore>> reference,Store<EntityStore> store) {var ref=reference.get();if(ref!=null&&ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);reference.clear();}
    private static void finish(Run r,TownRecord town,Store<EntityStore> store,String reason,boolean restore) {
        remove(r.foe,store);remove(r.portal,store);boolean active=r.id!=null;r.id=null;r.collecting=false;r.down=false;r.elapsed=0;r.status=reason;
        if(town!=null) {town.getHunts().end(reason);save(town,store);}
        var hunter=r.hunter.get();if(active&&alive(hunter,store)) {
            var effects=store.getComponent(hunter,com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
            if(effects!=null)for(int effect:effects.getActiveEffectIndexes())if(!r.previousEffects.contains(effect))
                effects.removeEffect(hunter,effect,com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior.COMPLETE,store);
            RtsGuardCombatSupport.clearCombatTarget(store.getComponent(hunter,NPCEntity.getComponentType()),store);NpcSupportUtil.setState(hunter,"Idle",null,store);
            if(restore) {
                heal(hunter,Float.MAX_VALUE,store);
                var layout=r.layout();var to=layout.hunterStand();
                if(VillagerBlockUtil.isNpcStandColumn(store.getExternalData().getWorld(),(int)Math.floor(to.x),(int)Math.floor(to.y),(int)Math.floor(to.z)))
                    AetherhavenNpcTeleport.apply(hunter,store,Teleport.createExact(to,layout.facing()));
            }
        }
    }
    public static final class DamageFilter extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage) {
            if(damage.isCancelled())return;
            var victim=chunk.getReferenceTo(i);var tag=store.getComponent(victim,HuntOpponent.getComponentType());
            var attacker=damage.getSource() instanceof Damage.EntitySource source?source.getRef():null;
            var attacking=attacker!=null&&attacker.isValid()?store.getComponent(attacker,HuntOpponent.getComponentType()):null;
            if(tag!=null&&(tag.portal||!authorized(tag,attacker,store))||attacking!=null&&(attacking.portal||!authorized(attacking,victim,store))) {damage.setCancelled(true);return;}
            var plugin=AetherhavenPlugin.get();
            if(tag!=null) {
                var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(tag.town);
                var r=run(store,tag.town);
                if(town!=null)damage.setAmount((float)(damage.getAmount()*(town.getHunts().damageMultiplier()+(r!=null&&r.standard?.05:0))));
            }
            if(attacking!=null) {
                var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(attacking.town);
                if(town!=null)damage.setAmount((float)(damage.getAmount()*TownHunts.enemyDamageScale(town.getHunts().defeated())));
            }
        }
        private static boolean authorized(HuntOpponent tag,Ref<EntityStore> other,Store<EntityStore> store) {
            if(other==null||!other.isValid())return false;
            var id=store.getComponent(other,UUIDComponent.getComponentType());var r=run(store,tag.town);
            return id!=null&&id.getUuid().equals(tag.hunter)&&r!=null&&tag.encounter.equals(r.id)&&!r.collecting&&!r.down;
        }
    }
    /** The arena's knockout is deliberately local; Gaia, resident deaths and revival rules are untouched. */
    public static final class Knockouts extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return TownVillagerBinding.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies() {return Set.of(new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage) {
            if(damage.isCancelled())return;
            var hunter=chunk.getReferenceTo(i);var b=chunk.getComponent(i,TownVillagerBinding.getComponentType());
            if(!KIND.equals(b.getKind()))return;var r=run(store,b.getTownId());
            if(r==null||r.id==null||!hunter.equals(r.hunter.get()))return;
            var hp=health(hunter,store);if(r.down) {damage.setCancelled(true);return;}
            if(hp==null||damage.getAmount()<hp.get())return;
            damage.setCancelled(true);r.down=true;
            buffer.run(s->{
                var town=AetherhavenWorldRegistries.getOrCreateTownManager(s.getExternalData().getWorld(),AetherhavenPlugin.get()).getTown(b.getTownId());
                if(town==null||r.id==null||!hunter.isValid())return;
                if(town.getHunts().level("second_wind")>0&&!r.secondWind) {
                    r.secondWind=true;r.down=false;
                    s.getComponent(hunter,EntityStatMap.getComponentType()).setStatValue(DefaultEntityStatTypes.getHealth(),health(hunter,s).getMax()*(r.sigil?.45f:.3f));
                    r.status="Second Wind! The Hunter rallies.";
                } else finish(r,town,s,"Defeated after "+town.getHunts().defeated()
                    +(town.getHunts().defeated()==1?" wave. Collected spoils are safe.":" waves. Collected spoils are safe."),true);
            });
        }
    }
    public static final class Deaths extends DeathSystems.OnDeathSystem {
        @Override public Query<EntityStore> getQuery(){return HuntOpponent.getComponentType();}
        @Override public void onComponentAdded(Ref<EntityStore> victim,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
            var tag=store.getComponent(victim,HuntOpponent.getComponentType());var r=run(store,tag.town);
            if(tag.portal||r==null||!tag.encounter.equals(r.id)||r.collecting||r.down||!victim.equals(r.foe.get()))return;
            var info=death.getDeathInfo();if(info==null||!(info.getSource() instanceof Damage.EntitySource source)||!source.getRef().equals(r.hunter.get()))return;
            r.spoils=HuntSpoils.roll(r.family);
            r.loot=new Vector3d(position(victim,store));r.collecting=true;r.collection=0;r.elapsed=0;
        }
    }
}
