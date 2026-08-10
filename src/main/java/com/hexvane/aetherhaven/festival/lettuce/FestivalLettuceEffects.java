package com.hexvane.aetherhaven.festival.lettuce;

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
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Sparkles and sounds for the New Life lettuce. */
public final class FestivalLettuceEffects {
    public static final String PARTICLE_ABSORB = "Aetherhaven_Festival_Absorb";
    public static final String PARTICLE_GROW = "Aetherhaven_Festival_Grow";
    public static final String PARTICLE_BURST = "Aetherhaven_Festival_Burst";

    public static final String SOUND_ABSORB = "Aetherhaven_Festival_Absorb";
    public static final String SOUND_GROW = "Aetherhaven_Festival_Grow";
    public static final String SOUND_BURST = "Aetherhaven_Festival_Burst";
    public static final String SOUND_START = "Aetherhaven_Festival_Start";

    /** Vanilla fallback if the custom absorb event fails to load. */
    private static final String SOUND_ABSORB_FALLBACK = "SFX_MemoryMote";

    /** Absorb needs a little push so it cuts through outdoor ambience. */
    private static final float ABSORB_VOLUME = 1.8F;
    /** Pop needs to carry across the festival square. */
    private static final float BURST_VOLUME = 2.2F;
    private static final String SOUND_BURST_FALLBACK = "SFX_Mushroom_Harvest";

    private FestivalLettuceEffects() {}

    public static void playAbsorb(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Vector3d pos) {
        playSound(accessor, pos, SOUND_ABSORB, SOUND_ABSORB_FALLBACK, ABSORB_VOLUME);
        playParticles(accessor, pos, PARTICLE_ABSORB);
    }

    public static void playGrow(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Vector3d pos) {
        play(accessor, pos, PARTICLE_GROW, SOUND_GROW, 1.0F);
    }

    public static void playBurst(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Vector3d pos) {
        playSound(accessor, pos, SOUND_BURST, SOUND_BURST_FALLBACK, BURST_VOLUME);
        playParticles(accessor, pos, PARTICLE_BURST);
    }

    public static void playFestivalStart(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Vector3d pos) {
        play(accessor, pos, PARTICLE_GROW, SOUND_START, 1.0F);
    }

    private static void play(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Vector3d pos,
        @Nonnull String particleSystemId,
        @Nonnull String soundEventId,
        float volumeModifier
    ) {
        playSound(accessor, pos, soundEventId, null, volumeModifier);
        playParticles(accessor, pos, particleSystemId);
    }

    private static void playSound(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Vector3d pos,
        @Nonnull String soundEventId,
        @javax.annotation.Nullable String fallbackSoundEventId,
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

    private static void playParticles(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Vector3d pos,
        @Nonnull String particleSystemId
    ) {
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            accessor.getResource(EntityModule.get().getPlayerSpatialResourceType());
        if (spatial == null) {
            return;
        }
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        spatial.getSpatialStructure().collect(pos, ParticleUtil.DEFAULT_PARTICLE_DISTANCE, nearby);
        ParticleUtil.spawnParticleEffect(particleSystemId, pos, nearby, accessor);
    }
}
