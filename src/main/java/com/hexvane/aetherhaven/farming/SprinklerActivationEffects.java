package com.hexvane.aetherhaven.farming;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Visual/audio feedback when a sprinkler runs: same 3D sound as {@code Watering_Can_Use} ({@code SFX_Tool_Watering_Can_Water})
 * and a custom particle system derived from the watering-can splash spawners.
 */
public final class SprinklerActivationEffects {
    /** Matches {@code Watering_Can_Use.json} {@code WorldSoundEventId}. */
    public static final String WATERING_CAN_SOUND_EVENT_ID = "SFX_Tool_Watering_Can_Water";

    /** {@link com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem} id for {@code Aetherhaven_Sprinkler_Water.particlesystem}. */
    public static final String SPRINKLER_PARTICLE_SYSTEM_ID = "Aetherhaven_Sprinkler_Water";

    private SprinklerActivationEffects() {}

    /**
     * Plays at the sprinkler block (near the top face); audible to players in range of the vanilla watering-can sound.
     */
    public static void playAtSprinklerBlock(@Nonnull ComponentAccessor<EntityStore> entityAccessor, int blockX, int blockY, int blockZ) {
        double px = blockX + 0.5;
        double py = blockY + 0.42;
        double pz = blockZ + 0.5;
        Vector3d pos = new Vector3d(px, py, pz);

        int soundIdx = SoundEvent.getAssetMap().getIndex(WATERING_CAN_SOUND_EVENT_ID);
        if (soundIdx != 0) {
            SoundUtil.playSoundEvent3d(null, soundIdx, pos, entityAccessor);
        }

        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            entityAccessor.getResource(EntityModule.get().getPlayerSpatialResourceType());
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(pos, ParticleUtil.DEFAULT_PARTICLE_DISTANCE, nearby);
        ParticleUtil.spawnParticleEffect(SPRINKLER_PARTICLE_SYSTEM_ID, pos, nearby, entityAccessor);
    }
}
