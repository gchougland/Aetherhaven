package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;

/** Listener-local preferences preserve spatial attenuation and shared NPC behavior. */
final class VillagerSpeechAudio {
    private VillagerSpeechAudio() {}

    record Listener(Ref<EntityStore> ref, float volume) {}
    record Playback(int sound, double x, double y, double z, float pitch, List<Listener> listeners) {
        void play(Store<EntityStore> store) {
            for (var listener : listeners) {
                SoundUtil.playSoundEvent3dToPlayer(listener.ref(), sound, SoundCategory.SFX, x, y, z,
                    listener.volume(), pitch, store);
            }
        }
    }

    /** Reserve listeners before starting particles or mouth tracks. Null means nobody will hear it. */
    static Playback prepare(Ref<EntityStore> npc, VillagerLifeSpeech.Clip clip, boolean randomChatter, Store<EntityStore> store) {
        var transform = store.getComponent(npc, TransformComponent.getComponentType());
        var identity = store.getComponent(npc, UUIDComponent.getComponentType());
        int index = SoundEvent.getAssetMap().getIndex(VillagerLifeVisuals.PREFIX + clip.clip());
        var event = SoundEvent.getAssetMap().getAsset(index);
        if (transform == null || identity == null || event == null) return null;
        var position = transform.getPosition();
        long now = System.currentTimeMillis();
        List<Listener> listeners = null;
        for (var listener : store.getExternalData().getWorld().getPlayerRefs()) {
            var ref = listener.getReference();
            if (ref == null || !ref.isValid() || ref.getStore() != store) continue;
            var listenerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            if (listenerTransform == null || listenerTransform.getPosition().distanceSquared(position) > event.getMaxDistance()*event.getMaxDistance()) continue;
            var preferences = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (preferences == null) {
                preferences = new PlayerTownJournalState();
                store.putComponent(ref, PlayerTownJournalState.getComponentType(), preferences);
            }
            float volume = preferences.getVillagerSpeechVolumePercent()/100f;
            if (volume <= 0 || (randomChatter && !preferences.tryAmbientSpeech(identity.getUuid(), now, clip.audioMs()))) continue;
            if (listeners == null) listeners = new ArrayList<>();
            listeners.add(new Listener(ref, volume));
        }
        return listeners == null ? null : new Playback(index, position.x, position.y + 1.5, position.z, clip.pitch(), listeners);
    }
}
