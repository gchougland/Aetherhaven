package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Loaded-world activity only. Conversations and reservations never survive a reload. */
public final class VillagerLifeState implements Component<EntityStore> {
    private static ComponentType<EntityStore, VillagerLifeState> type;
    long nextUpdateMs;
    long nextSearchMs;
    long nextEmoteMs;
    long emoteUntilMs;
    long socialCooldownMs;
    long hungryCooldownMs;
    boolean mealDeparture;
    long nextEatingSoundMs;
    long visualUntilMs;
    long nextAmbientVoiceMs;
    long nextContextThoughtMs;
    boolean readingLoop;
    long readingResumeMs;
    long nextPonderVoiceMs;
    Session session;
    String conversationItemId;
    String temporaryItemId;
    byte temporarySlot = -1;
    byte previousActiveSlot = -1;
    byte temporaryUtilitySlot = -1;
    byte previousUtilitySlot = -1;
    long nextEmotionParticleMs;

    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        type = registry.registerComponent(VillagerLifeState.class, VillagerLifeState::new);
    }

    public static ComponentType<EntityStore, VillagerLifeState> getComponentType() { return type; }

    public boolean ownsActivity(long now) { return session != null || emoteUntilMs != 0; }

    @Nonnull
    @Override
    public VillagerLifeState clone() {
        VillagerLifeState copy = new VillagerLifeState();
        copy.nextUpdateMs = nextUpdateMs;
        copy.nextSearchMs = nextSearchMs;
        copy.nextEmoteMs = nextEmoteMs;
        copy.emoteUntilMs = emoteUntilMs;
        copy.socialCooldownMs = socialCooldownMs;
        copy.hungryCooldownMs = hungryCooldownMs;
        copy.mealDeparture = mealDeparture;
        copy.nextEatingSoundMs = nextEatingSoundMs;
        copy.visualUntilMs = visualUntilMs;
        copy.nextAmbientVoiceMs = nextAmbientVoiceMs;
        copy.nextContextThoughtMs = nextContextThoughtMs;
        copy.readingLoop = readingLoop;
        copy.readingResumeMs = readingResumeMs;
        copy.nextPonderVoiceMs = nextPonderVoiceMs;
        // Intentionally shared: one atomic, world-thread conversation for both participants.
        copy.session = session;
        copy.conversationItemId = conversationItemId;
        copy.temporaryItemId = temporaryItemId;
        copy.temporarySlot = temporarySlot;
        copy.previousActiveSlot = previousActiveSlot;
        copy.temporaryUtilitySlot = temporaryUtilitySlot;
        copy.previousUtilitySlot = previousUtilitySlot;
        copy.nextEmotionParticleMs = nextEmotionParticleMs;
        return copy;
    }

    static final class Session {
        static final long RESPONSE_DELAY_MS = 1200;
        record Response(boolean toFirst, String gesture, String bubble, boolean hearts, long dueMs) {}
        Response pendingResponse;
        void respondLater(boolean toFirst, String gesture, String bubble, boolean hearts, long now) {
            pendingResponse = new Response(toFirst, gesture, bubble, hearts, now + RESPONSE_DELAY_MS);
        }
        Response takeResponse(long now) {
            if (pendingResponse == null || now < pendingResponse.dueMs()) return null;
            Response response = pendingResponse;
            pendingResponse = null;
            return response;
        }
        final UUID first;
        final UUID second;
        final long createdMs;
        String topic;
        long lastUpdateMs;
        long talkingSinceMs;
        long nextBeatMs;
        int beat;
        boolean stationary;
        long readyAfterMs;
        boolean prepared;
        java.util.List<VillagerRomance.Beat> romance = java.util.List.of();

        boolean finishedTalking(long now) {
            if (pendingResponse != null) return false;
            if (now < nextBeatMs) return false;
            return romance.isEmpty() ? now - talkingSinceMs >= VillagerLifePolicy.CONVERSATION_MS : beat >= romance.size();
        }

        Session(UUID first, UUID second, long now, String topic) {
            this.first = first;
            this.second = second;
            this.createdMs = now;
            this.lastUpdateMs = now;
            this.topic = topic;
        }
    }
}
