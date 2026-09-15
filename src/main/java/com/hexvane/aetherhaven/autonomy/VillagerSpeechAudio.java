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

/** Listener-local preferences preserve spatial attenuation and shared NPC behavior. */
final class VillagerSpeechAudio {
    private VillagerSpeechAudio() {}

    static void play(Ref<EntityStore> npc, VillagerLifeSpeech.Clip clip, boolean randomChatter, Store<EntityStore> store) {
        var transform = store.getComponent(npc, TransformComponent.getComponentType());
        var identity = store.getComponent(npc, UUIDComponent.getComponentType());
        int index = SoundEvent.getAssetMap().getIndex(VillagerLifeVisuals.PREFIX + clip.clip());
        var event = SoundEvent.getAssetMap().getAsset(index);
        if (transform == null || identity == null || event == null) return;
        var position = transform.getPosition();
        long now = System.currentTimeMillis();
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
            if (volume <= 0 || (randomChatter && !preferences.tryAmbientSpeech(identity.getUuid(), now))) continue;
            SoundUtil.playSoundEvent3dToPlayer(ref, index, SoundCategory.SFX, position.x, position.y+1.5, position.z,
                volume, clip.pitch(), store);
        }
    }
}
