package com.hexvane.aetherhaven.battlehorn;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Server broadcast for the battle horn charge sound (client charging effects are local only). */
final class BattleHornSounds {
    private static volatile int worldHoldIndexResolved = Integer.MIN_VALUE;

    private BattleHornSounds() {}

    static void playWorldHoldAt(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        int idx = resolveWorldHoldIndex();
        if (idx < 0) {
            return;
        }
        TransformComponent transform = commandBuffer.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        var pos = transform.getPosition();
        SoundUtil.playSoundEvent3d(playerRef, idx, pos.x(), pos.y(), pos.z(), true, commandBuffer);
    }

    private static int resolveWorldHoldIndex() {
        int local = worldHoldIndexResolved;
        if (local != Integer.MIN_VALUE) {
            return local;
        }
        synchronized (BattleHornSounds.class) {
            if (worldHoldIndexResolved == Integer.MIN_VALUE) {
                int idx = SoundEvent.getAssetMap().getIndex(AetherhavenConstants.BATTLE_HORN_WORLD_SOUND_EVENT_ID);
                worldHoldIndexResolved = idx;
            }
            return worldHoldIndexResolved;
        }
    }
}
