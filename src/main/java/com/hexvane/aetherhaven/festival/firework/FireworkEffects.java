package com.hexvane.aetherhaven.festival.firework;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Trail and burst VFX for firework rockets. */
public final class FireworkEffects {
    private static final float EXPLODE_VOLUME = 2.0F;

    private FireworkEffects() {}

    public static void playTrail(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Vector3d pos) {
        playParticles(accessor, pos, FireworkIds.PARTICLE_TRAIL);
    }

    private static void playParticles(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Vector3d pos,
        @Nonnull String particleSystemId
    ) {
        playParticles(accessor, pos, particleSystemId, 1.0F);
    }

    private static void playParticles(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Vector3d pos,
        @Nonnull String particleSystemId,
        float scale
    ) {
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            accessor.getResource(EntityModule.get().getPlayerSpatialResourceType());
        if (spatial == null) {
            return;
        }
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        spatial.getSpatialStructure().collect(pos, ParticleUtil.DEFAULT_PARTICLE_DISTANCE, nearby);
        ParticleUtil.spawnParticleEffect(
            particleSystemId,
            pos.x,
            pos.y,
            pos.z,
            0.0F,
            0.0F,
            0.0F,
            scale,
            null,
            null,
            nearby,
            accessor
        );
    }

    public static void playBurst(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Vector3d pos) {
        String[] pool = FireworkIds.BURST_PARTICLE_SYSTEMS;
        String systemId = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        playSound(accessor, pos, FireworkIds.SOUND_EXPLODE, FireworkIds.SOUND_EXPLODE_FALLBACK, EXPLODE_VOLUME);
        playParticles(accessor, pos, systemId, FireworkIds.BURST_SCALE);
    }

    private static void playSound(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Vector3d pos,
        @Nonnull String soundEventId,
        @Nullable String fallbackSoundEventId,
        float volumeModifier
    ) {
        int soundIdx = resolveSoundIndex(soundEventId);
        if (soundIdx == SoundEvent.EMPTY_ID && fallbackSoundEventId != null) {
            soundIdx = resolveSoundIndex(fallbackSoundEventId);
        }
        if (soundIdx == SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent3d(
            soundIdx,
            SoundCategory.SFX,
            pos.x,
            pos.y,
            pos.z,
            volumeModifier,
            1.0F,
            accessor
        );
    }

    private static int resolveSoundIndex(@Nonnull String soundEventId) {
        int soundIdx = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (soundIdx == Integer.MIN_VALUE || soundIdx == SoundEvent.EMPTY_ID) {
            return SoundEvent.EMPTY_ID;
        }
        if (SoundEvent.getAssetMap().getAsset(soundIdx) == null) {
            return SoundEvent.EMPTY_ID;
        }
        return soundIdx;
    }
}
