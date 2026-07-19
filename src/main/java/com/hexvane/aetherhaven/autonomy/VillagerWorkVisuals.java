package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Repeating tool swings / leisure emotes while a villager occupies a work or fun POI.
 * All packet / Store side effects go through {@link CommandBuffer#run}.
 */
public final class VillagerWorkVisuals {
    private static final long WORK_HIT_INTERVAL_MS = 1300L;
    private static final long LEISURE_EMOTE_INTERVAL_MS = 5500L;
    private static final String[] LEISURE_EMOTES = {"PonderDismissive", "Yawn", "Laugh"};
    private static final String READ_EMOTE = "PonderDismissive";

    private VillagerWorkVisuals() {}

    /**
     * Play one work hit or leisure flavor beat when the pacing timer elapses.
     *
     * @return true if autonomy state's last-hit timestamp should be updated
     */
    public static boolean tickHit(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull PoiEntry poi,
        @Nullable String bindingKind,
        long nowMs,
        long lastHitEpochMs
    ) {
        // Never overlay tool swings or fidget emotes on sleep / meal consume.
        if (poi.getInteractionKind() == com.hexvane.aetherhaven.poi.PoiInteractionKind.SLEEP
            || PoiScoring.isEatPoi(poi)) {
            return false;
        }
        VillagerWorkActivity activity = VillagerWorkActivity.resolve(poi, bindingKind);
        long interval = activity.isLeisure() ? LEISURE_EMOTE_INTERVAL_MS : WORK_HIT_INTERVAL_MS;
        if (lastHitEpochMs > 0L && nowMs - lastHitEpochMs < interval) {
            return false;
        }
        if (activity.isLeisure()) {
            // Fun spots always; READ/CRAFT fidget at desks and quiet benches; LEISURE on work = stand (bard).
            if (!PoiScoring.isWorkPoi(poi)
                || activity == VillagerWorkActivity.READ
                || activity == VillagerWorkActivity.CRAFT) {
                playLeisureBeat(npcRef, store, commandBuffer, npc, activity);
                // Emotes can leave Face looking neutral while mood still thinks Grin is set.
                NpcFaceVisuals.applyMoodFace(npcRef, commandBuffer, store);
                return true;
            }
            return false;
        }
        if (!PoiScoring.isWorkPoi(poi)) {
            return false;
        }
        if (activity.playsToolAction()) {
            playToolSwing(npcRef, store, commandBuffer, npc, activity);
            spawnHitFx(store, commandBuffer, poi, activity);
            NpcFaceVisuals.applyMoodFace(npcRef, commandBuffer, store);
        }
        return true;
    }

    private static void playLeisureBeat(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerWorkActivity activity
    ) {
        // Keep Sit/Sleep Status; sprinkle Emote only.
        String emote = activity == VillagerWorkActivity.READ
            ? READ_EMOTE
            : LEISURE_EMOTES[ThreadLocalRandom.current().nextInt(LEISURE_EMOTES.length)];
        NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Emote, emote, commandBuffer);
        commandBuffer.putComponent(npcRef, NPCEntity.getComponentType(), npc);
    }

    private static void playToolSwing(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerWorkActivity activity
    ) {
        String animId = activity.itemAnimationId();
        if (animId == null) {
            return;
        }
        ItemPlayerAnimations ipa = resolveItemAnimations(store, npcRef, activity);
        if (ipa != null) {
            NpcAnimationPlayback.playItem(npcRef, AnimationSlot.Action, ipa, animId, commandBuffer);
        } else if (activity.fallbackItemAnimationsId() != null) {
            NpcAnimationPlayback.playItem(
                npcRef,
                AnimationSlot.Action,
                activity.fallbackItemAnimationsId(),
                animId,
                commandBuffer
            );
        } else {
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Action, animId, commandBuffer);
        }
        commandBuffer.putComponent(npcRef, NPCEntity.getComponentType(), npc);
    }

    @Nullable
    private static ItemPlayerAnimations resolveItemAnimations(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull VillagerWorkActivity activity
    ) {
        InventoryComponent.Hotbar hb = store.getComponent(npcRef, InventoryComponent.Hotbar.getComponentType());
        if (hb != null) {
            ItemStack held = hb.getActiveItem();
            if (held != null && held.getItemId() != null) {
                Item item = Item.getAssetMap().getAsset(held.getItemId());
                if (item != null) {
                    String pid = item.getPlayerAnimationsId();
                    if (pid != null && !pid.isBlank()) {
                        ItemPlayerAnimations fromHeld = ItemPlayerAnimations.getAssetMap().getAsset(pid);
                        if (fromHeld != null) {
                            return fromHeld;
                        }
                    }
                }
            }
        }
        String fallback = activity.fallbackItemAnimationsId();
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        return ItemPlayerAnimations.getAssetMap().getAsset(fallback);
    }

    private static void spawnHitFx(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi,
        @Nonnull VillagerWorkActivity activity
    ) {
        Vector3d pos = new Vector3d(poi.getX() + 0.5, poi.getY() + 0.55, poi.getZ() + 0.5);
        String swingId = activity.swingSoundEventId();
        String hitId = activity.hitSoundEventId();
        String particleId = activity.hitParticleSystemId();
        float volume = activity.soundVolume();
        commandBuffer.run(s -> {
            if (swingId != null) {
                playSound3d(swingId, pos, volume, s);
            }
            if (hitId != null) {
                playSound3d(hitId, pos, volume, s);
            }
            if (particleId != null && !particleId.isBlank()) {
                try {
                    ParticleUtil.spawnParticleEffect(particleId, pos, s);
                } catch (RuntimeException ignored) {
                    // Particle asset may be absent on some packs; skip quietly.
                }
            }
        });
    }

    private static void playSound3d(
        @Nonnull String soundEventId,
        @Nonnull Vector3d pos,
        float volume,
        @Nonnull Store<EntityStore> store
    ) {
        int idx = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (idx < 0) {
            return;
        }
        try {
            SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, pos.x, pos.y, pos.z, volume, 1.0F, store);
        } catch (RuntimeException ignored) {
            // Sound missing or store busy; skip.
        }
    }
}
