package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3d;

/** Brief local accents, separate from speech bubbles and the existing loved-gift hearts. */
final class VillagerEmotionParticles {
    static String effect(String gesture, String mood, double roll) {
        // Mood also covers voiced reading/thinking and reactions whose body action differs.
        if ("Gasp".equals(mood)) return roll < .45 ? "Shock" : "Surprise";
        if ("Question".equals(mood)) return "Question";
        if ("Thinking".equals(mood) && ("Ponder".equals(gesture) || roll < .35)) return "Confusion";
        if ("Sigh".equals(mood)) return "Gloom";
        if (gesture == null) return null;
        return switch (gesture) {
            case "Surprise" -> "Gasp".equals(mood) && roll < .45 ? "Shock" : "Surprise";
            case "Question" -> "Question";
            case "Ponder", "Disagree" -> "Confusion";
            case "Bored" -> "Gloom";
            default -> null;
        };
    }
    static void play(Ref<EntityStore> ref, String gesture, String mood, Store<EntityStore> store) {
        String effect = effect(gesture, mood, ThreadLocalRandom.current().nextDouble());
        if (effect == null) return;
        var transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        var life = VillagerLifeVisuals.state(ref, store);
        long now = System.currentTimeMillis();
        if (now < life.nextEmotionParticleMs) return;
        life.nextEmotionParticleMs = now + 1400;
        // Match VillagerGiftService.playLoveGiftParticles: anchor at eye height,
        // let the native Hearts system provide its head offset, and dispatch on the world queue.
        double eye = 1.6;
        var model = store.getComponent(ref, ModelComponent.getComponentType());
        if (model != null && model.getModel() != null) eye = model.getModel().getEyeHeight(ref, store);
        Vector3d position = new Vector3d(transform.getPosition()).add(0, eye, 0);
        String systemId = "Aetherhaven_Emotion_" + effect;
        store.getExternalData().getWorld().execute(() -> ParticleUtil.spawnParticleEffect(systemId, position, store));

    }
    private VillagerEmotionParticles() {}
}
