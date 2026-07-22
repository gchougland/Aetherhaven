package com.hexvane.aetherhaven.heartberry;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerBefriendableResolver;
import com.hexvane.aetherhaven.villager.gift.VillagerGiftService;
import com.hexvane.aetherhaven.worldnpc.WorldNpcBinding;
import com.hexvane.aetherhaven.worldnpc.WorldNpcReputationService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class HeartberryService {
    public static final int REPUTATION_DELTA = 10;

    /** Same local eat SFX as vanilla fruit/berries ({@code Template_Fruit} {@code ConsumedSFX}). */
    private static final String EAT_SOUND_EVENT_ID = "SFX_Consume_Bread_Local";

    private HeartberryService() {}

    public static boolean wouldGainReputation(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (!targetRef.isValid()) {
            return false;
        }
        if (store.getComponent(targetRef, NPCEntity.getComponentType()) == null) {
            return false;
        }
        if (!VillagerBefriendableResolver.isBefriendable(store, targetRef, plugin)) {
            return false;
        }
        if (VillagerGiftService.isVisitor(store, targetRef)) {
            return false;
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        UUIDComponent nu = store.getComponent(targetRef, UUIDComponent.getComponentType());
        if (pu == null || nu == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();

        WorldNpcBinding worldBinding = store.getComponent(targetRef, WorldNpcBinding.getComponentType());
        if (worldBinding != null) {
            VillagerReputationEntry entry =
                WorldNpcReputationService.getOrCreate(world, plugin, pu.getUuid(), worldBinding.getPlacementId());
            return entry.getReputation() < VillagerReputationService.MAX_REPUTATION;
        }

        TownRecord town = resolveTownForHeartberry(playerRef, store, targetRef, plugin, world);
        if (town == null) {
            return false;
        }
        VillagerReputationEntry e = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), nu.getUuid());
        return e.getReputation() < VillagerReputationService.MAX_REPUTATION;
    }

    /**
     * @return reputation gained, or null if the berry should not be consumed
     */
    @Nullable
    public static Integer tryApply(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (!targetRef.isValid()) {
            return null;
        }
        if (store.getComponent(targetRef, NPCEntity.getComponentType()) == null) {
            return null;
        }
        if (!VillagerBefriendableResolver.isBefriendable(store, targetRef, plugin)) {
            return null;
        }
        if (VillagerGiftService.isVisitor(store, targetRef)) {
            return null;
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        UUIDComponent nu = store.getComponent(targetRef, UUIDComponent.getComponentType());
        if (pu == null || nu == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();

        WorldNpcBinding worldBinding = store.getComponent(targetRef, WorldNpcBinding.getComponentType());
        if (worldBinding != null) {
            int gained =
                WorldNpcReputationService.addReputationDelta(
                    world,
                    plugin,
                    pu.getUuid(),
                    worldBinding.getPlacementId(),
                    REPUTATION_DELTA
                );
            if (gained <= 0) {
                return null;
            }
            playEatSound(playerRef, store);
            VillagerGiftService.playLoveGiftParticles(targetRef, store);
            VillagerGiftService.notifyReputationChange(playerRef, store, gained);
            return gained;
        }

        TownRecord town = resolveTownForHeartberry(playerRef, store, targetRef, plugin, world);
        if (town == null) {
            return null;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        VillagerReputationEntry e = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), nu.getUuid());
        int before = e.getReputation();
        if (!VillagerReputationService.addReputationInternal(
            town,
            world,
            pu.getUuid(),
            nu.getUuid(),
            e,
            REPUTATION_DELTA,
            tm
        )) {
            return null;
        }
        int gained = e.getReputation() - before;
        if (gained <= 0) {
            return null;
        }
        playEatSound(playerRef, store);
        VillagerGiftService.playLoveGiftParticles(targetRef, store);
        VillagerGiftService.notifyReputationChange(playerRef, store, gained);
        return gained;
    }

    private static void playEatSound(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        int idx = SoundEvent.getAssetMap().getIndex(EAT_SOUND_EVENT_ID);
        if (idx != Integer.MIN_VALUE && idx != SoundEvent.EMPTY_ID) {
            SoundUtil.playSoundEvent2d(playerRef, idx, SoundCategory.SFX, store);
        }
    }

    @Nullable
    private static TownRecord resolveTownForHeartberry(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return null;
        }
        TownVillagerBinding b = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (b != null) {
            TownRecord town = AetherhavenWorldRegistries.getTownAcrossWorlds(b.getTownId(), tm);
            if (town == null || !town.hasMemberOrOwner(pu.getUuid())) {
                return null;
            }
            return town;
        }
        return AetherhavenWorldRegistries.findTownForPlayerAcrossWorlds(pu.getUuid(), tm);
    }
}
