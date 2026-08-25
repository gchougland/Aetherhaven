package com.hexvane.aetherhaven.festival.snowball;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Snow puff, hit sound, and a pickup-style notice when a fight snowball lands. */
final class SnowballHitFeedback {
    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.snowball.notify.";

    private SnowballHitFeedback() {}

    /** Snow puff and thud with no scoring attached, for a snowball that lands on somebody outside a fight. */
    static void burst(@Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Vector3d at) {
        ParticleUtil.spawnParticleEffect(SnowballIds.HIT_PARTICLE, at, commandBuffer);
        playHitSound(commandBuffer, at);
    }

    static void play(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull UUID attackerUuid,
        int livesLeft,
        boolean victimOut
    ) {
        TransformComponent transform = store.getComponent(victimRef, TransformComponent.getComponentType());
        Vector3d at = transform != null ? new Vector3d(transform.getPosition()) : null;
        if (at != null) {
            at.y += 1.2;
            ParticleUtil.spawnParticleEffect(SnowballIds.HIT_PARTICLE, at, commandBuffer);
            playHitSound(commandBuffer, at);
        }
        notifyPlayer(store, attackerUuid, true, livesLeft, victimOut);
        UUIDComponent victimUuid = store.getComponent(victimRef, UUIDComponent.getComponentType());
        if (victimUuid != null && victimUuid.getUuid() != null) {
            notifyPlayer(store, victimUuid.getUuid(), false, livesLeft, victimOut);
        }
    }

    private static void playHitSound(@Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Vector3d at) {
        int idx = SoundEvent.getAssetMap().getIndex(SnowballIds.HIT_SOUND);
        if (idx == SoundEvent.EMPTY_ID || idx == Integer.MIN_VALUE || idx == 0) {
            return;
        }
        SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, at.x, at.y, at.z, 1.4F, 1.0F, commandBuffer);
    }

    private static void notifyPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID uuid,
        boolean attacker,
        int livesLeft,
        boolean victimOut
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(uuid);
        if (ref == null || !ref.isValid() || store.getComponent(ref, Player.getComponentType()) == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || playerRef.getPacketHandler() == null) {
            return;
        }
        String titleKey = attacker ? LANG + "hit" : LANG + "hurt";
        String subKey;
        if (victimOut) {
            subKey = attacker ? LANG + "hit.out" : LANG + "hurt.out";
        } else {
            subKey = attacker ? LANG + "hit.left" : LANG + "hurt.left";
        }
        Message title = Message.translation(titleKey);
        Message sub = Message.translation(subKey).param("lives", String.valueOf(Math.max(0, livesLeft)));
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(),
            title,
            sub,
            SnowballIds.HIT_NOTIFY_ICON,
            new ItemStack(SnowballIds.SNOWBALL_ITEM_ID, 1).toPacket(),
            attacker ? NotificationStyle.Success : NotificationStyle.Warning
        );
    }
}
