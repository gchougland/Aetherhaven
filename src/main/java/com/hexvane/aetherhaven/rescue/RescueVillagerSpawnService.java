package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Spawns one-shot rescue NPCs after trigger blocks are broken. */
public final class RescueVillagerSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private RescueVillagerSpawnService() {}

    @Nullable
    public static UUID trySpawnBesideOpenedCoffin(World world, Store<EntityStore> store, TownRecord town,
                                                  Vector3i coffin, UUID player) {
        return trySpawnBesideOpenedCoffin(world,store,town,coffin,player,java.time.Clock.systemDefaultZone());
    }

    @Nullable
    static UUID trySpawnBesideOpenedCoffin(World world, Store<EntityStore> store, TownRecord town,
                                         Vector3i coffin, UUID player, java.time.Clock calendar) {
        var trigger = RescueVillagerTriggers.HALLOWEEN;
        if (!com.hexvane.aetherhaven.halloween.HalloweenResident.rescueSeason(calendar)
            || !HalloweenCoffinUseSystem.isOpenedCoffin(ChunkSectionBlockUtil.blockType(world,coffin))
            || anyAffiliatedTownAlreadyHasRescue(List.of(town), trigger)) return null;
        if(!HalloweenRescueEncounters.mayOpen(world,store,town,coffin,player,calendar))return null;
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                var stand = resolveRescueStandPosition(world, coffin.x + dx, coffin.y, coffin.z + dz);
                if (stand != null) return spawnRescueAt(world, store, town, stand, player, trigger,
                    "RESCUE_COFFIN", "Opened coffin at " + coffin);
            }
        }
        return null;
    }

    /**
     * Town that should receive a field rescue for this player. Prefers an owned town (then any
     * membership) across all loaded worlds. Returns null when the player has no town, or when any
     * affiliated town already completed this rescue / has the permanent character.
     */
    @Nullable
    public static TownRecord resolveTownForFieldRescue(
        @Nonnull UUID playerUuid,
        @Nullable TownManager prefer,
        @Nonnull RescueVillagerTrigger trigger
    ) {
        List<TownRecord> affiliated = AetherhavenWorldRegistries.listTownsForPlayerAcrossWorlds(playerUuid);
        if (affiliated.isEmpty()) {
            return null;
        }
        if (anyAffiliatedTownAlreadyHasRescue(affiliated, trigger)) {
            return null;
        }
        return AetherhavenWorldRegistries.findTownForPlayerAcrossWorlds(playerUuid, prefer);
    }

    /** True when any of the player's towns already finished this rescue or keeps that villager. */
    public static boolean anyAffiliatedTownAlreadyHasRescue(
        @Nonnull List<TownRecord> affiliated,
        @Nonnull RescueVillagerTrigger trigger
    ) {
        String permanentKind = permanentKindForRescue(trigger.rescueBindingKind());
        for (TownRecord t : affiliated) {
            if (t.hasQuestCompleted(trigger.rescueQuestId())) {
                return true;
            }
            if (permanentKind != null && townHasResidentKind(t, permanentKind)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String permanentKindForRescue(@Nonnull String rescueBindingKind) {
        if (!TownVillagerBinding.isRescueKind(rescueBindingKind)) {
            return null;
        }
        return rescueBindingKind.substring("rescue_".length());
    }

    private static boolean townHasResidentKind(@Nonnull TownRecord town, @Nonnull String kind) {
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (r != null && kind.equals(r.getKind())) {
                return true;
            }
        }
        return false;
    }

    public static boolean townHasActiveRescueNpc(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull String rescueBindingKind
    ) {
        return findActiveRescueNpc(store,townId,rescueBindingKind)!=null;
    }

    @Nullable
    private static Ref<EntityStore> findActiveRescueNpc(Store<EntityStore> store,UUID townId,String rescueBindingKind) {
        var found=new java.util.ArrayList<Ref<EntityStore>>(1);
        store.forEachChunk(TownVillagerBinding.getComponentType(),
            (java.util.function.BiPredicate<com.hypixel.hytale.component.ArchetypeChunk<EntityStore>,com.hypixel.hytale.component.CommandBuffer<EntityStore>>)(chunk,buffer)->{
            for(int index=0;index<chunk.size();index++) {
                var binding=chunk.getComponent(index,TownVillagerBinding.getComponentType());
                if(binding==null||!townId.equals(binding.getTownId())||!rescueBindingKind.equals(binding.getKind()))continue;
                var ref=chunk.getReferenceTo(index);
                if(ref!=null&&ref.isValid()){found.add(ref);return true;}
            }
            return false;
        });
        return found.isEmpty()?null:found.getFirst();
    }

    /**
     * Spawns the rescue NPC once every trigger block in the column scan range is broken. Returns null when blocks
     * remain or spawn fails.
     */
    @Nullable
    public static UUID trySpawnAfterBlockBroken(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Vector3i brokenBlock,
        @Nullable UUID breakerPlayerUuid,
        @Nonnull RescueVillagerTrigger trigger
    ) {
        if (columnStillHasTriggerBlock(world, brokenBlock.x, brokenBlock.y, brokenBlock.z, trigger)) {
            return null;
        }
        if (townHasActiveRescueNpc(store, town.getTownId(), trigger.rescueBindingKind())) {
            return null;
        }
        Vector3d stand = resolveRescueStandPosition(world, brokenBlock.x, brokenBlock.y, brokenBlock.z);
        if (stand == null) {
            LOGGER.atWarning().log(
                "No stand position for %s rescue at %s,%s,%s",
                trigger.logLabel(),
                brokenBlock.x,
                brokenBlock.y,
                brokenBlock.z
            );
            return null;
        }
        return spawnRescueAt(
            world,
            store,
            town,
            stand,
            breakerPlayerUuid,
            trigger,
            "RESCUE_BLOCK",
            "kind=" + trigger.rescueBindingKind() + ",block=" + brokenBlock.x + "," + brokenBlock.y + "," + brokenBlock.z
        );
    }

    /**
     * Spawns a rescue NPC at an explicit stand position (e.g. beside the carnival wheel). Returns null when an active
     * rescue of that kind already exists or spawn fails.
     */
    @Nullable
    public static UUID spawnRescueAt(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Vector3d stand,
        @Nullable UUID faceTowardPlayerUuid,
        @Nonnull RescueVillagerTrigger trigger
    ) {
        return spawnRescueAt(
            world,
            store,
            town,
            stand,
            faceTowardPlayerUuid,
            trigger,
            "RESCUE_WHEEL",
            "kind=" + trigger.rescueBindingKind()
        );
    }

    @Nullable
    private static UUID spawnRescueAt(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Vector3d stand,
        @Nullable UUID faceTowardPlayerUuid,
        @Nonnull RescueVillagerTrigger trigger,
        @Nonnull String originKind,
        @Nonnull String originDetail
    ) {
        boolean halloween=com.hexvane.aetherhaven.halloween.HalloweenResident.RESCUE_KIND.equals(trigger.rescueBindingKind());
        if(halloween&&anyAffiliatedTownAlreadyHasRescue(List.of(town),trigger))return null;
        var active=findActiveRescueNpc(store,town.getTownId(),trigger.rescueBindingKind());
        if(active!=null) {
            if(halloween)HalloweenRescueEncounters.observe(active,store);
            return null;
        }
        var reservation=halloween?town.halloweenRescue().reserve(world.getName(),(int)Math.floor(stand.x),(int)Math.floor(stand.y),(int)Math.floor(stand.z)):null;
        if(halloween&&reservation==null)return null;
        if(halloween)HalloweenRescueEncounters.changed(town);
        boolean succeeded=false;
        try {
            UUID result=spawnRescueEntity(world,store,town,stand,faceTowardPlayerUuid,trigger,originKind,originDetail,reservation);
            succeeded=result!=null;return result;
        }finally {
            if(halloween&&!succeeded) {
                var current=town.halloweenRescue().current();
                if(current!=null&&current.token().equals(reservation.token())&&current.entity()!=null)
                    com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalService.schedule(world,current.entity(),"halloween_failed_spawn");
                if(town.halloweenRescue().rollback(reservation.token()))HalloweenRescueEncounters.changed(town);
            }
        }
    }

    @Nullable
    private static UUID spawnRescueEntity(World world,Store<EntityStore> store,TownRecord town,Vector3d stand,
            UUID faceTowardPlayerUuid,RescueVillagerTrigger trigger,String originKind,String originDetail,
            HalloweenRescueReceipt.Encounter reservation) {
        Rotation3f rotation = rotationTowardPlayer(store, stand, faceTowardPlayerUuid);
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return null;
        }
        var pair = npc.spawnNPC(store, trigger.rescueNpcRoleId(), null, stand, rotation);
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn %s rescue NPC for town %s", trigger.logLabel(), town.getTownId());
            return null;
        }
        Ref<EntityStore> ref = pair.first();
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID spawned = uuidComp != null ? uuidComp.getUuid() : null;
        if(reservation!=null&&!town.halloweenRescue().bind(reservation.token(),spawned)) {
            if(spawned!=null)com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalService.schedule(world,spawned,"halloween_cancelled_spawn");
            return null;
        }
        store.putComponent(ref, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        String handle = "Villager_" + trigger.rescueBindingKind() + "_" + shortHex(town.getTownId());
        store.putComponent(ref, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(handle));
        store.putComponent(
            ref,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), trigger.rescueBindingKind(), null)
        );
        NpcSpawnOriginUtil.attach(store, ref, originKind, originDetail, world, stand);
        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity != null) {
            npcEntity.getLeashPoint().set(stand);
            npcEntity.setLeashHeading(rotation.yaw());
            npcEntity.setLeashPitch(rotation.pitch());
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            tc.getPosition().set(stand);
            tc.getRotation().set(rotation.pitch(), rotation.yaw(), rotation.roll());
            store.putComponent(ref, TransformComponent.getComponentType(), tc);
        }
        if(reservation!=null){HalloweenRescueEncounters.changed(town);HalloweenRescueEncounters.observe(ref,store);}
        LOGGER.atInfo().log(
            "Spawned %s rescue NPC %s for town %s at %s,%s,%s",
            trigger.logLabel(),
            spawned,
            town.getTownId(),
            stand.x,
            stand.y,
            stand.z
        );
        return spawned;
    }

    static boolean columnStillHasTriggerBlock(
        @Nonnull World world,
        int bx,
        int by,
        int bz,
        @Nonnull RescueVillagerTrigger trigger
    ) {
        for (int dy = trigger.columnScanMinDy(); dy <= trigger.columnScanMaxDy(); dy++) {
            if (isTriggerBlock(world, bx, by + dy, bz, trigger.triggerBlockTypeId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static Vector3d resolveRescueStandPosition(@Nonnull World world, int bx, int brokenY, int bz) {
        int searchTop = Math.min(319, brokenY + 3);
        int feetY = VillagerBlockUtil.findStandY(world, bx, bz, searchTop);
        if (feetY == Integer.MIN_VALUE) {
            feetY = Math.min(319, brokenY + 2);
            if (!VillagerBlockUtil.isNpcStandColumn(world, bx, feetY, bz)) {
                return null;
            }
        }
        return new Vector3d(bx + 0.5, feetY, bz + 0.5);
    }

    @Nonnull
    private static Rotation3f rotationTowardPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d stand,
        @Nullable UUID breakerPlayerUuid
    ) {
        if (breakerPlayerUuid == null) {
            return new Rotation3f();
        }
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(breakerPlayerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return new Rotation3f();
        }
        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTc == null) {
            return new Rotation3f();
        }
        Vector3d playerPos = playerTc.getPosition();
        double dx = playerPos.x - stand.x;
        double dz = playerPos.z - stand.z;
        if (Math.abs(dx) < 1.0e-4 && Math.abs(dz) < 1.0e-4) {
            return new Rotation3f();
        }
        float yaw = (float) (Math.atan2(dx, dz) + Math.PI);
        return new Rotation3f(0f, yaw, 0f);
    }

    private static boolean isTriggerBlock(@Nonnull World world, int x, int y, int z, @Nonnull String blockTypeId) {
        if (y < 0 || y >= 320) {
            return false;
        }
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }
        BlockType bt = ChunkSectionBlockUtil.blockType(world, x, y, z);
        return bt != null && blockTypeId.equals(bt.getId());
    }

    @Nonnull
    private static String shortHex(@Nonnull UUID townId) {
        String s = townId.toString().replace("-", "");
        return s.substring(0, Math.min(8, s.length()));
    }
}
