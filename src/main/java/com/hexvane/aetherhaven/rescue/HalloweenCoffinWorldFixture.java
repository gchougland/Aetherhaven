package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.halloween.HalloweenResident;
import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Uses an NPC interaction driver, not a player or network session, in a disposable command world. */
public final class HalloweenCoffinWorldFixture {
    private static final Clock OCTOBER=Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),ZoneOffset.UTC);
    private static final Clock NOVEMBER=Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"),ZoneOffset.UTC);
    private record Scene(World world,TownManager manager,TownRecord town,Ref<EntityStore> operator) {}
    private HalloweenCoffinWorldFixture() {}

    public static CompletableFuture<Void> run(World world) {
        return world.getChunkStore().getChunkSectionReferenceAsync(0,4,0,GetChunkFlags.SET_TICKING).thenComposeAsync(unused -> {
            var plugin=AetherhavenPlugin.get();var store=world.getEntityStore().getStore();
            var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
            var town=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),world.getName(),1,129,1,1,1,0);
            manager.markDirty(town);
            for(int x=1;x<=22;x++)for(int z=1;z<=22;z++)put(world,x,128,z,"Wood_Hardwood_Planks");
            var spawned=NPCPlugin.get().spawnNPC(store,HalloweenResident.MODEL,null,new Vector3d(18.5,129,18.5),Rotation3f.ZERO);
            require(spawned!=null,"Coffin interaction driver did not spawn");
            var scene=new Scene(world,manager,town,spawned.first());
            CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
            var families=List.of("Ancient","Human_Ruins","Temple_Dark","Village");
            for(int i=0;i<families.size();i++) {
                String family=families.get(i);int index=i;
                chain=chain.thenComposeAsync(done -> checkFamily(scene,family,index),world);
            }
            chain=chain.thenComposeAsync(done -> checkParking(scene),world);
            chain=chain.thenComposeAsync(done -> checkDeparture(scene,new Position(14,129,14)),world);
            return chain.whenCompleteAsync((done,error) -> {
                if(scene.operator().isValid())store.removeEntity(scene.operator(),RemoveReason.REMOVE);
                manager.removeTown(town.getTownId());
            },world);
        },world);
    }

    private static CompletableFuture<Void> checkFamily(Scene scene,String family,int index) {
        var world=scene.world();var store=world.getEntityStore().getStore();
        var at=new Position(6+index%2*8,129,6+index/2*8);
        String closed="Furniture_"+family+"_Coffin";
        put(world,at.x(),at.y(),at.z(),closed);
        var type=BlockType.getAssetMap().getAsset(closed);
        require(type!=null&&type.getInteractionHint()!=null&&!type.getInteractionHint().isBlank(),"Coffin key prompt missing: "+closed);
        String opened=type.getBlockKeyForState("Open");require(opened!=null,"Native Open state missing: "+closed);
        require(RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,scene.town(),vector(at),scene.town().getOwnerUuid(),OCTOBER)==null,"Closed coffin spawned Jack");
        var queue=new HalloweenCoffinOpeningQueue();long now=System.nanoTime();
        require(queue.offer(scene.town().getOwnerUuid(),at,closed,opened,now),"Opening not queued");
        queue.poll(now+1,true,p -> block(world,p),request -> {throw new IllegalStateException("Rescue spawned before native opening");});
        require(queue.size()==1,"Closed native coffin discarded too early");
        var interactions=store.getComponent(scene.operator(),com.hypixel.hytale.server.core.modules.interaction.InteractionModule.get().getInteractionManagerComponent());
        require(interactions!=null,"NPC interaction manager missing");
        var context=InteractionContext.forInteraction(interactions,scene.operator(),InteractionType.Use,store);
        var target=new BlockPosition(at.x(),at.y(),at.z());
        context.getMetaStore().putMetaObject(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.TARGET_BLOCK_RAW,target);
        context.getMetaStore().putMetaObject(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.TARGET_BLOCK,target);
        var root=RootInteraction.getAssetMap().getAsset(type.getInteractions().get(InteractionType.Use));
        require(root!=null,"Native coffin Use root missing");
        interactions.queueExecuteChain(interactions.initChain(InteractionType.Use,context,root,Entity.UNASSIGNED_ID,
            target,false));
        return awaitOpening(scene,queue,at,opened,System.nanoTime()+HalloweenCoffinOpeningQueue.WAIT_NANOS);
    }

    private static CompletableFuture<Void> awaitOpening(Scene scene,HalloweenCoffinOpeningQueue queue,Position at,String opened,long deadline) {
        return CompletableFuture.runAsync(() -> {},CompletableFuture.delayedExecutor(50,TimeUnit.MILLISECONDS))
            .thenComposeAsync(unused -> {
                require(System.nanoTime()-deadline<0,"Native coffin interaction did not open: "+block(scene.world(),at));
                if(!opened.equals(block(scene.world(),at)))return awaitOpening(scene,queue,at,opened,deadline);
                var delivered=new java.util.concurrent.atomic.AtomicBoolean();
                queue.poll(System.nanoTime(),true,p -> block(scene.world(),p),request -> {
                    delivered.set(true);verifySpawn(scene,at);
                });
                require(delivered.get()&&queue.isEmpty(),"Observed native opening did not deliver rescue");
                queue.poll(System.nanoTime(),true,p -> block(scene.world(),p),request -> {throw new IllegalStateException("Opening delivered twice");});
                return CompletableFuture.<Void>completedFuture(null);
            },scene.world());
    }
    private static void verifySpawn(Scene scene,Position at) {
        var world=scene.world();var store=world.getEntityStore().getStore();var town=scene.town();var player=town.getOwnerUuid();
        require(RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,town,vector(at),player,NOVEMBER)==null,"November spawned Jack");
        UUID id=RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,town,vector(at),player,OCTOBER);
        require(id!=null,"Opened October coffin failed to spawn Jack");var rescue=store.getExternalData().getRefFromUUID(id);
        try {
            require(rescue!=null&&rescue.isValid(),"Rescue entity missing");
            require(town.halloweenRescue().current()!=null&&id.equals(town.halloweenRescue().current().entity()),"Successful rescue did not bind its town receipt");
            require(!com.hexvane.aetherhaven.npc.NpcFaceVisuals.supportsFaceExpressions(rescue,store),"Pumpkin model incorrectly allows human facial animations");
            com.hexvane.aetherhaven.autonomy.VillagerLifeVisuals.dialogue(scene.operator(),rescue,"Talk",0,store);
            var speech=store.getComponent(scene.operator(),com.hexvane.aetherhaven.ui.PlayerTownJournalState.getComponentType());
            require(speech!=null&&speech.getLastDialogueClip()!=null&&speech.getLastDialogueClip().startsWith("GravelyMale_Talk_"),
                "Current dialogue did not select Jack's low recorded voice");
            require(RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,town,vector(at),player,OCTOBER)==null,"Repeated opening created another active Jack");
        } finally {if(rescue!=null&&rescue.isValid())store.removeEntity(rescue,RemoveReason.REMOVE);}
        require(town.halloweenRescue().current()==null,"Permanent fixture removal retained its encounter receipt");
        var completed=new TownRecord();completed.completeQuest(HalloweenResident.RESCUE_QUEST);
        require(RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,completed,vector(at),player,OCTOBER)==null,"Completed rescue spawned Jack again");
        var resident=new TownRecord();resident.getResidentNpcRecords().add(new ResidentNpcRecord(HalloweenResident.MODEL,HalloweenResident.KIND,null,UUID.randomUUID()));
        require(RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,resident,vector(at),player,OCTOBER)==null,"Resident Jack allowed another rescue");
    }
    private static CompletableFuture<Void> checkParking(Scene scene) {
        var world=scene.world();var store=world.getEntityStore().getStore();
        // Keep the native interaction driver in its original section while Jack is parked separately.
        var at=new Position(42,129,14);
        return world.getChunkStore().getChunkSectionReferenceAsync(1,4,0,GetChunkFlags.SET_TICKING).thenComposeAsync(unused -> {
            for(int x=38;x<=48;x++)for(int z=10;z<=19;z++)put(world,x,128,z,"Wood_Hardwood_Planks");
            var type=BlockType.getAssetMap().getAsset("Furniture_Ancient_Coffin");
            require(type!=null&&type.getBlockKeyForState("Open")!=null,"Parking fixture coffin Open state missing");
            put(world,at.x(),at.y(),at.z(),type.getBlockKeyForState("Open"));
            UUID id=RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,scene.town(),vector(at),scene.town().getOwnerUuid(),OCTOBER);
            require(id!=null,"Parking fixture rescue missing");
            return awaitSectionMembership(scene,id,System.nanoTime()+TimeUnit.SECONDS.toNanos(5))
                .thenRunAsync(() -> verifyParking(scene,id,at),world)
                .whenCompleteAsync((done,error) -> {
                    var rescue=store.getExternalData().getRefFromUUID(id);
                    if(rescue!=null&&rescue.isValid())store.removeEntity(rescue,RemoveReason.REMOVE);
                },world);
        },world);
    }
    private static CompletableFuture<Void> awaitSectionMembership(Scene scene,UUID id,long deadline) {
        var world=scene.world();var store=world.getEntityStore().getStore();
        var rescue=store.getExternalData().getRefFromUUID(id);
        require(rescue!=null&&rescue.isValid(),"Parking fixture Jack vanished before section registration");
        var transform=store.getComponent(rescue,TransformComponent.getComponentType());
        var sectionRef=transform==null?null:transform.getSectionRef();
        var section=sectionRef==null||!sectionRef.isValid()?null:
            world.getChunkStore().getStore().getComponent(sectionRef,EntitySection.getComponentType());
        if(section!=null&&section.getEntityReferences().contains(rescue))return CompletableFuture.completedFuture(null);
        require(System.nanoTime()-deadline<0,"Parking fixture Jack did not join a native entity section");
        return CompletableFuture.runAsync(() -> {},CompletableFuture.delayedExecutor(50,TimeUnit.MILLISECONDS))
            .thenComposeAsync(unused -> awaitSectionMembership(scene,id,deadline),world);
    }
    private static void verifyParking(Scene scene,UUID id,Position at) {
        var world=scene.world();var store=world.getEntityStore().getStore();var chunks=world.getChunkStore().getStore();
        var rescue=store.getExternalData().getRefFromUUID(id);
        require(rescue!=null&&rescue.isValid(),"Parking fixture Jack disappeared");
        var transform=store.getComponent(rescue,TransformComponent.getComponentType());
        var sectionRef=transform.getSectionRef();
        var section=chunks.getComponent(sectionRef,EntitySection.getComponentType());
        var receipt=scene.town().halloweenRescue();var before=receipt.current();
        require(before!=null&&id.equals(before.entity()),"Parking fixture receipt was not bound");
        require(chunks.getComponent(sectionRef,ChunkStore.REGISTRY.getNonTickingComponentType())==null,"Parking fixture section already stopped ticking");
        try {
            // The real native loading system must remove live entities with UNLOAD and retain holders.
            // Keep this transition and its assertions in one world task so automatic ticking cannot intervene.
            chunks.ensureComponent(sectionRef,ChunkStore.REGISTRY.getNonTickingComponentType());
            require(!rescue.isValid()&&store.getExternalData().getRefFromUUID(id)==null,"Native parking kept Jack in the live entity store");
            require(parkedCount(section,id)==1,"Native parking did not retain exactly one Jack holder");
            require(receipt.current()!=null&&before.token().equals(receipt.current().token())&&id.equals(receipt.current().entity()),"UNLOAD cleared or replaced the rescue receipt");
            require(RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,scene.town(),vector(at),scene.town().getOwnerUuid(),OCTOBER)==null,"A parked Jack allowed a duplicate rescue");
            require(receipt.current()!=null&&before.token().equals(receipt.current().token())&&parkedCount(section,id)==1,"Opening changed the parked rescue or its receipt");
        } finally {
            // Restore ticking even when an assertion fails, before the command's disposable-world cleanup.
            if(sectionRef.isValid())chunks.tryRemoveComponent(sectionRef,ChunkStore.REGISTRY.getNonTickingComponentType());
        }
        var resumed=store.getExternalData().getRefFromUUID(id);
        require(resumed!=null&&resumed.isValid()&&section.getEntityReferences().contains(resumed),"Resuming the section did not restore the same Jack UUID");
        require(parkedCount(section,id)==0,"Resumed Jack left a duplicate parked holder");
        require(receipt.current()!=null&&before.token().equals(receipt.current().token())&&id.equals(receipt.current().entity()),"Resuming Jack changed the authoritative receipt");
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(
            "Halloween native parking: PASSED. Jack %s left the live UUID registry, remained in one section holder, blocked another rescue and resumed with the same receipt.",id);
    }
    private static long parkedCount(EntitySection section,UUID id) {
        return section.getEntityHolders().stream().filter(holder -> {
            var uuid=holder.getComponent(UUIDComponent.getComponentType());return uuid!=null&&id.equals(uuid.getUuid());
        }).count();
    }
    private static CompletableFuture<Void> checkDeparture(Scene scene,Position at) {
        var world=scene.world();var store=world.getEntityStore().getStore();var town=scene.town();
        UUID id=RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,town,vector(at),town.getOwnerUuid(),OCTOBER);
        require(id!=null,"Departure fixture rescue missing");var rescue=store.getExternalData().getRefFromUUID(id);
        var member=UUID.randomUUID();town.putMember(member,TownMemberRole.BUILD);
        var memberHolder=EntityStore.REGISTRY.newHolder();memberHolder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(member));
        var memberRef=store.addEntity(memberHolder,AddReason.SPAWN);
        var ownerHolder=EntityStore.REGISTRY.newHolder();ownerHolder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(town.getOwnerUuid()));
        var ownerRef=store.addEntity(ownerHolder,AddReason.SPAWN);
        var actions=new ArrayList<com.google.gson.JsonObject>();
        try(var input=HalloweenCoffinWorldFixture.class.getResourceAsStream("/Server/Aetherhaven/Dialogue/aetherhaven_halloween_rescue.json")) {
            require(input!=null,"Rescue dialogue missing from packaged jar");
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(var action:json.getAsJsonObject("nodes").getAsJsonObject("root").getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("actions"))actions.add(action.getAsJsonObject());
        } catch(java.io.IOException error) {throw new java.io.UncheckedIOException(error);}
        var executor=new com.hexvane.aetherhaven.dialogue.DialogueActionExecutor();
        require(!HalloweenResident.canAcceptRescue(memberRef,store,rescue),"Member without quest permission can accept rescue");
        executor.runBatch(actions,memberRef,store,new com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult(),rescue);
        return CompletableFuture.runAsync(() -> {},CompletableFuture.delayedExecutor(100,TimeUnit.MILLISECONDS)).thenRunAsync(() -> {
            require(rescue.isValid()&&!town.hasQuestCompleted(HalloweenResident.RESCUE_QUEST),"Failed completion removed Jack or completed rescue");
            if(HalloweenResident.rescueSeason()) {
                require(HalloweenResident.canAcceptRescue(ownerRef,store,rescue),"Owner cannot accept October rescue");
                executor.runBatch(actions,ownerRef,store,new com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult(),rescue);
                require(town.hasQuestCompleted(HalloweenResident.RESCUE_QUEST),"Owner's rescue completion was not saved");
            } else {
                // Keep the native departure assertion usable outside October without changing the real calendar.
                town.completeQuest(HalloweenResident.RESCUE_QUEST);
                executor.runBatch(List.of(actions.get(1)),ownerRef,store,new com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult(),rescue);
            }
        },world).thenCompose(unused -> CompletableFuture.runAsync(() -> {},CompletableFuture.delayedExecutor(100,TimeUnit.MILLISECONDS)))
            .thenRunAsync(() -> require(!rescue.isValid(),"Completed rescue did not depart"),world)
            .whenCompleteAsync((unused,error) -> {
                if(rescue.isValid())store.removeEntity(rescue,RemoveReason.REMOVE);
                if(memberRef.isValid())store.removeEntity(memberRef,RemoveReason.REMOVE);
                if(ownerRef.isValid())store.removeEntity(ownerRef,RemoveReason.REMOVE);
            },world);
    }
    private static Vector3i vector(Position at){return new Vector3i(at.x(),at.y(),at.z());}
    private static String block(World world,Position at){var type=ChunkSectionBlockUtil.blockType(world,at.x(),at.y(),at.z());return type==null?null:type.getId();}
    private static void put(World world,int x,int y,int z,String block){require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,block,0),"Fixture block failed: "+block);}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
