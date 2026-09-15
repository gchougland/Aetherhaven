package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import org.joml.Vector3d;

/** Call only from a deferred command, after the store has finished ticking. */
public final class VillagerLifeVisuals {
    static final String PREFIX = "Aetherhaven_Life_";
    static final String BODY_ANIMATIONS = "Aetherhaven_Life_Actions";
    static final AnimationSlot BODY_SLOT = AnimationSlot.Action;

    private VillagerLifeVisuals() {}

    public static long dialogue(Ref<EntityStore> player, Ref<EntityStore> ref, String category, float volume, Store<EntityStore> store) {
        var cue = com.hexvane.aetherhaven.speech.DialogueSpeechCue.resolve(category);
        if (cue.clip().equals("None")) return 0;
        var id = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        if (id == null) return 0;
        var preferences = store.getComponent(player, com.hexvane.aetherhaven.ui.PlayerTownJournalState.getComponentType());
        if (preferences == null) {
            preferences = new com.hexvane.aetherhaven.ui.PlayerTownJournalState();
            store.putComponent(player, com.hexvane.aetherhaven.ui.PlayerTownJournalState.getComponentType(), preferences);
        }
        var clip = VillagerLifeSpeech.selectExcept(VillagerLifePersonality.voice(ref, id.getUuid(), store), cue.clip(),
            java.util.concurrent.ThreadLocalRandom.current().nextInt(), preferences.getLastDialogueClip());
        if (clip == null) {
            preferences.setLastDialogueClip(null);
            VillagerLifeProps.equip(ref, cue.gesture(), store);
            AnimationUtils.playAnimation(ref, BODY_SLOT, NpcFaceVisuals.itemAnimationsForFaceRig(ref, BODY_ANIMATIONS, store), cue.gesture(), false, store);
            state(ref, store).visualUntilMs = System.currentTimeMillis() + VillagerLifeTiming.durationMs(cue.gesture());
            return VillagerLifeTiming.durationMs(cue.gesture());
        }
        preferences.setLastDialogueClip(clip.clip());
        var expression = clip.faces().get(cue.gesture());
        if (expression != null && NpcFaceVisuals.supportsFaceExpressions(ref, store)) {
            VillagerLifeProps.equip(ref, cue.gesture(), store);
            AnimationUtils.playAnimation(ref, BODY_SLOT, NpcFaceVisuals.itemAnimationsForFaceRig(ref, clip.actionsId(), store), expression.actionId(), false, store);
            NpcFaceVisuals.playDialogueExpression(ref, expression.id(), expression.durationMs()/1000f, store);
        }
        if (volume > .001f) com.hexvane.aetherhaven.ui.UiSoundEffects.play2d(player, store, PREFIX + clip.clip(), SoundCategory.SFX, volume, clip.pitch());
        long duration = Math.max(clip.audioMs(), expression == null ? 0 : expression.durationMs());
        var life = state(ref, store);
        life.visualUntilMs = System.currentTimeMillis() + duration;
        life.nextAmbientVoiceMs = life.visualUntilMs + 5000;
        return duration;
    }

    static VillagerLifeState state(Ref<EntityStore> ref, Store<EntityStore> store) {
        var state = store.getComponent(ref, VillagerLifeState.getComponentType());
        if (state == null) { state = new VillagerLifeState(); store.putComponent(ref, VillagerLifeState.getComponentType(), state); }
        return state;
    }

    public static long ponder(Ref<EntityStore> ref, Store<EntityStore> store) {
        var life = state(ref, store);
        long now = System.currentTimeMillis();
        if (now < life.nextPonderVoiceMs) return life.nextPonderVoiceMs - now;
        var id = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        long duration = id == null ? 5200 : voice(ref, id.getUuid(), "Thinking", "Ponder", store);
        life.nextPonderVoiceMs = now + Math.max(5200, duration) + 1500;
        return duration;
    }

    public static void maintainReading(Ref<EntityStore> ref, Store<EntityStore> store) {
        var life = state(ref, store);
        var npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null && NpcFaceVisuals.isInInteractionDialogue(npc)) return;
        if (life.readingLoop && life.readingResumeMs != 0 && System.currentTimeMillis() >= life.readingResumeMs) {
            life.readingResumeMs = 0;
            AnimationUtils.playAnimation(ref, BODY_SLOT, NpcFaceVisuals.itemAnimationsForFaceRig(ref, BODY_ANIMATIONS, store), "ReadLoop", false, store);
        }
    }

    public static void continueReading(Ref<EntityStore> ref, Store<EntityStore> store) {
        var life = store.getComponent(ref, VillagerLifeState.getComponentType());
        if (life != null && life.readingLoop) read(ref, false, store);
    }

    private static long read(Ref<EntityStore> ref, boolean working, Store<EntityStore> store) {
        var life = state(ref, store);
        if (!life.readingLoop) {
            VillagerLifeProps.equip(ref, "ReadLoop", store);
            AnimationUtils.playAnimation(ref, BODY_SLOT, NpcFaceVisuals.itemAnimationsForFaceRig(ref, BODY_ANIMATIONS, store), "ReadLoop", false, store);
            life.readingLoop = true;
        }
        maintainReading(ref, store);
        long now = System.currentTimeMillis();
        if (now >= life.nextAmbientVoiceMs) {
            var rng = java.util.concurrent.ThreadLocalRandom.current();
            String mood = rng.nextDouble() < .4 ? "Thinking" : VillagerLifePolicy.ambientMood(working, rng.nextBoolean());
            var id = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (id != null) {
                long duration = voice(ref, id.getUuid(), mood, "ReadLoop", store);
                life.readingResumeMs = now + duration;
                life.nextAmbientVoiceMs = now + duration + rng.nextLong(3500, 7000);
            }
        }
        return Math.max(6000, life.readingResumeMs - now);
    }

    /** Tool swings retain their body track while a new idle/work recording drives the face. */
    public static void workMurmur(Ref<EntityStore> ref, Store<EntityStore> store) {
        var life = state(ref, store);
        long now = System.currentTimeMillis();
        if (now < life.nextAmbientVoiceMs) return;
        var id = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        if (id == null) return;
        String mood = VillagerLifePolicy.ambientMood(true, java.util.concurrent.ThreadLocalRandom.current().nextBoolean());
        var clip = VillagerLifeSpeech.select(VillagerLifePersonality.voice(ref, id.getUuid(), store), mood,
            java.util.concurrent.ThreadLocalRandom.current().nextInt());
        if (clip == null) return;
        var face = clip.faces().get("LookAround");
        if (face != null) NpcFaceVisuals.playExpression(ref, face.id(), face.durationMs()/1000f, store);
        VillagerSpeechAudio.play(ref, clip, true, store);
        life.nextAmbientVoiceMs = now + clip.audioMs() + java.util.concurrent.ThreadLocalRandom.current().nextLong(4500, 9000);
    }

    static void emote(Ref<EntityStore> ref, String gesture, Store<EntityStore> store) {
        if (gesture.equals("Stretch") || gesture.equals("Sleepy")) {
            var id = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (id != null) { voice(ref, id.getUuid(), "Yawn", gesture, store); return; }
        }
        silentEmote(ref, gesture, store);
    }

    private static void silentEmote(Ref<EntityStore> ref, String gesture, Store<EntityStore> store) {
        VillagerLifeProps.equip(ref, gesture, store);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) {
            // Player-rig Emote resolves cosmetic Emote assets, not Model.AnimationSets.
            // ThirdPersonFace pairs the facial track with the body at Action priority.
            AnimationUtils.playAnimation(ref, BODY_SLOT, NpcFaceVisuals.itemAnimationsForFaceRig(ref, BODY_ANIMATIONS, store), gesture, false, store);
        }
        face(ref, gesture, false, store);
    }

    static void face(Ref<EntityStore> ref, String gesture, boolean talking, Store<EntityStore> store) {
        NpcFaceVisuals.playExpression(ref, PREFIX + "Face_" + (talking ? "Talking_" : "") + gesture,
            VillagerLifeTiming.durationMs((talking ? "Talking_" : "") + gesture) / 1000f, store);
    }

    static void bubble(Ref<EntityStore> ref, boolean speech, String icon, Store<EntityStore> store) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) return;
        BoundingBox box = store.getComponent(ref, BoundingBox.getComponentType());
        double height = box == null ? 2.2 : box.getBoundingBox().max.y;
        ModelComponent model = store.getComponent(ref, ModelComponent.getComponentType());
        if (model != null) {
            // Collision bounds can be shorter than the visible head and hair.
            height = Math.max(height, model.getModel().getEyeHeight(ref, store) + .35 * model.getModel().getScale());
        }
        // The particle canvas pivot is the tail tip, not the center of its cloud.
        Vector3d pos = new Vector3d(tc.getPosition()).add(0, height + .2, 0);
        ParticleUtil.spawnParticleEffect(PREFIX + (speech ? "Speech_" : "Thought_") + icon, pos, store);
    }

    static long voice(Ref<EntityStore> ref, UUID id, String mood, Store<EntityStore> store) {
        String gesture = switch (mood) {
            case "Groan" -> "Hungry"; case "Yawn" -> "Sleepy"; case "Sigh" -> "Bored"; default -> null;
        };
        return voice(ref, id, mood, gesture, store);
    }

    private static long voice(Ref<EntityStore> ref, UUID id, String mood, String gesture, Store<EntityStore> store) {
        return voice(ref, id, mood, gesture, store, !mood.equals("Stomach"));
    }

    private static long voice(Ref<EntityStore> ref, UUID id, String mood, String gesture, Store<EntityStore> store, boolean randomChatter) {
        String profile = VillagerLifePersonality.voice(ref, id, store);
        var clip = VillagerLifeSpeech.select(profile, mood, java.util.concurrent.ThreadLocalRandom.current().nextInt());
        if (clip == null) {
            if (gesture != null) silentEmote(ref, gesture, store);
            return 0;
        }
        var expression = gesture == null ? null : clip.faces().get(gesture);
        if (expression != null) {
            VillagerLifeProps.equip(ref, gesture, store);
            AnimationUtils.playAnimation(ref, BODY_SLOT, NpcFaceVisuals.itemAnimationsForFaceRig(ref, clip.actionsId(), store), expression.actionId(), false, store);
            NpcFaceVisuals.playExpression(ref, expression.id(), expression.durationMs() / 1000f, store);
            VillagerSpeechAudio.play(ref, clip, randomChatter, store);
            return Math.max(clip.audioMs(), expression.durationMs());
        }
        if (gesture != null) silentEmote(ref, gesture, store);
        VillagerSpeechAudio.play(ref, clip, randomChatter, store);
        return clip.audioMs();
    }

    public static void eatingSound(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        int sound = SoundEvent.getAssetMap().getIndex("SFX_Consume_Bread");
        if (tc == null || sound < 0) return;
        Vector3d p = tc.getPosition();
        SoundUtil.playSoundEvent3d(sound, SoundCategory.SFX, p.x, p.y + 1.5, p.z, .65f, 1f, store);
        var life = store.getComponent(ref, VillagerLifeState.getComponentType());
        if (life != null) life.nextEatingSoundMs = System.currentTimeMillis() + 3200;
    }

    /** Returns the duration so guild idle pacing never cuts off a voiced action. */
    public static long ambient(Ref<EntityStore> ref, String gesture, Store<EntityStore> store) {
        return ambient(ref, gesture, false, store);
    }

    public static long ambient(Ref<EntityStore> ref, String gesture, boolean working, Store<EntityStore> store) {
        if (gesture.equals("Read")) return read(ref, working, store);
        if (gesture.equals("Eat")) {
            VillagerLifeProps.equip(ref, "Eat", store);
            var item = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap()
                .getAsset(com.hexvane.aetherhaven.AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID);
            if (item != null) AnimationUtils.playAnimation(ref, BODY_SLOT, item.getPlayerAnimationsId(), "Consume", false, store);
            eatingSound(ref, store);
            return 3500;
        }
        if (gesture.equals("Stretch") || gesture.equals("Sleepy")) {
            var id = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (id != null) return voice(ref, id.getUuid(), "Yawn", gesture, store);
        }
        if (gesture.equals("Laugh") || gesture.equals("Bored") || gesture.equals("Greet")) {
            var id = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (id != null) return voice(ref, id.getUuid(), gesture.equals("Laugh") ? "Laugh" : gesture.equals("Bored") ? "Sigh" : "Talk", gesture, store);
        }
        var life = state(ref, store);
        long now = System.currentTimeMillis();
        if (now >= life.nextAmbientVoiceMs) {
            var id = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (id != null) {
                String mood = VillagerLifePolicy.ambientMood(working, java.util.concurrent.ThreadLocalRandom.current().nextBoolean());
                long duration = voice(ref, id.getUuid(), mood, gesture, store);
                life.nextAmbientVoiceMs = now + duration + java.util.concurrent.ThreadLocalRandom.current().nextLong(4500, 9000);
                return Math.max(duration, VillagerLifeTiming.durationMs(gesture));
            }
        }
        emote(ref, gesture, store);
        return VillagerLifeTiming.durationMs(gesture);
    }

    public static void endAmbient(Ref<EntityStore> ref, Store<EntityStore> store) {
        AnimationUtils.stopAnimation(ref, BODY_SLOT, store);
        VillagerLifeProps.clear(ref, store);
        var hotbar = store.getComponent(ref, com.hypixel.hytale.server.core.inventory.InventoryComponent.Hotbar.getComponentType());
        if (hotbar != null && hotbar.getActiveItem() != null
            && com.hexvane.aetherhaven.AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID.equals(hotbar.getActiveItem().getItemId())) {
            hotbar.getInventory().setItemStackForSlot(hotbar.getActiveSlot(), com.hypixel.hytale.server.core.inventory.ItemStack.EMPTY);
            com.hexvane.aetherhaven.equipment.VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, hotbar.getActiveSlot(), ref, store);
            store.putComponent(ref, com.hypixel.hytale.server.core.inventory.InventoryComponent.Hotbar.getComponentType(), hotbar);
        }
    }

    static long speak(Ref<EntityStore> ref, UUID id, String gesture, String topic, Store<EntityStore> store) {
        bubble(ref, true, topic, store);
        long speechMs = voice(ref, id, switch (gesture) {
            case "Question" -> "Question"; case "Laugh" -> "Laugh";
            case "Surprise" -> "Gasp"; case "Disagree" -> "Grumble"; default -> "Talk";
        }, gesture, store, false);
        return Math.max(speechMs, VillagerLifeTiming.durationMs(gesture));
    }

    static long romance(Ref<EntityStore> speaker, Ref<EntityStore> listener, UUID speakerId,
                        VillagerRomance.Beat beat, Store<EntityStore> store) {
        bubble(speaker, beat.speechBubble(), beat.bubble(), store);
        if (beat.hearts()) loveHearts(speaker, store);
        // Conversation audio bypasses the random idle-chatter limiter so the groan
        // cannot be swallowed by the preceding affectionate utterance.
        long voiceMs = voice(speaker, speakerId, beat.voice(), beat.gesture(), store, false);
        silentEmote(listener, beat.listenerGesture(), store);
        if (beat.listenerBubble() != null) bubble(listener, false, beat.listenerBubble(), store);
        if (beat.listenerHearts()) loveHearts(listener, store);
        return Math.max(voiceMs, Math.max(VillagerLifeTiming.durationMs(beat.gesture()),
            VillagerLifeTiming.durationMs(beat.listenerGesture())));
    }

    private static void loveHearts(Ref<EntityStore> ref, Store<EntityStore> store) {
        com.hexvane.aetherhaven.villager.gift.VillagerGiftService.playLoveGiftParticles(ref, store);
    }
}
