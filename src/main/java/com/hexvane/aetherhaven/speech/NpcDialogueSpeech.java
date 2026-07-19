package com.hexvane.aetherhaven.speech;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.speech.data.SpeechVoiceDefinition;
import com.hexvane.aetherhaven.ui.DialoguePage;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hexvane.aetherhaven.ui.UiSoundEffects;
import com.hypixel.hytale.common.util.AudioUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Plays Animal Crossing–style speech blips mapped to letters in dialogue body text.
 * Caps length to the first few words; per-syllable pitch/rate/volume jitter; player prefs for mute/volume.
 */
public final class NpcDialogueSpeech {
    private static final String LETTER_SOUND_PREFIX = "Aetherhaven_Speech_Blip_";
    /** Long dialogue lines only speak this many words so blips stay short. */
    private static final int MAX_SPEECH_WORDS = 6;
    private static final ConcurrentHashMap<UUID, AtomicLong> GENERATION_BY_PLAYER = new ConcurrentHashMap<>();

    private NpcDialogueSpeech() {}

    /** Timed random blips while the mouth talk burst is active (no body text available). */
    public static void startTalkSpeech(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        startTalkSpeech(playerEntityRef, npcRef, store, "");
    }

    /**
     * Letter-driven blips for {@code bodyText}. Non-letters pause briefly; A–Z map to matching SoundEvents.
     */
    public static void startTalkSpeech(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable String bodyText
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        if (!cfg.isDialogueSpeechEnabled()) {
            return;
        }
        if (!playerEntityRef.isValid() || !npcRef.isValid()) {
            return;
        }
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerTownJournalState prefs = store.getComponent(playerEntityRef, PlayerTownJournalState.getComponentType());
        if (prefs != null && !prefs.isDialogueSpeechEnabled()) {
            return;
        }
        float playerVolumeLinear = prefs != null ? prefs.getDialogueSpeechVolumeLinear() : 0.7f;
        if (playerVolumeLinear <= 0.001f) {
            return;
        }
        UUID playerUuid = playerUuid(playerEntityRef, store);
        if (playerUuid == null) {
            return;
        }

        SpeechVoiceDefinition voice = SpeechVoiceResolver.resolve(npcRef, store);
        List<SpeechStep> steps = buildSteps(bodyText != null ? bodyText : "", voice);
        if (steps.isEmpty()) {
            return;
        }
        long gen = nextGeneration(playerUuid);
        World world = store.getExternalData().getWorld();
        scheduleStep(playerEntityRef, npcRef, world, voice, gen, playerUuid, steps, 0, 0L, playerVolumeLinear);
    }

    public static void cancelForPlayer(@Nonnull UUID playerUuid) {
        nextGeneration(playerUuid);
    }

    @Nonnull
    static String truncateToMaxWords(@Nonnull String bodyText, int maxWords) {
        if (maxWords <= 0 || bodyText.isBlank()) {
            return "";
        }
        String trimmed = bodyText.trim();
        int words = 0;
        int end = 0;
        boolean inWord = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (!inWord) {
                    words++;
                    inWord = true;
                    if (words > maxWords) {
                        return trimmed.substring(0, end).trim();
                    }
                }
                end = i + 1;
            } else {
                inWord = false;
                if (words > 0 && words <= maxWords) {
                    end = i + 1;
                }
            }
        }
        return trimmed.substring(0, Math.min(end, trimmed.length())).trim();
    }

    @Nonnull
    static List<SpeechStep> buildSteps(@Nonnull String bodyText, @Nonnull SpeechVoiceDefinition voice) {
        List<SpeechStep> steps = new ArrayList<>();
        int interval = voice.getIntervalMs();
        String capped = truncateToMaxWords(bodyText, MAX_SPEECH_WORDS);
        char[] chars = capped.toCharArray();
        for (char c : chars) {
            if (Character.isLetter(c)) {
                char upper = Character.toUpperCase(c);
                if (upper >= 'A' && upper <= 'Z') {
                    steps.add(new SpeechStep(LETTER_SOUND_PREFIX + upper, interval));
                }
            } else if (c == '.' || c == '!' || c == '?' || c == ';' || c == ':') {
                steps.add(new SpeechStep(null, Math.max(interval, (int) (interval * 1.6f))));
            } else if (c == ',' || c == '-' || c == '—' || c == '…') {
                steps.add(new SpeechStep(null, Math.max(interval, (int) (interval * 1.15f))));
            } else if (Character.isWhitespace(c)) {
                steps.add(new SpeechStep(null, Math.max(20, (int) (interval * 0.35f))));
            }
        }
        return steps;
    }

    private static void scheduleStep(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull World world,
        @Nonnull SpeechVoiceDefinition voice,
        long generation,
        @Nonnull UUID playerUuid,
        @Nonnull List<SpeechStep> steps,
        int index,
        long delayMs,
        float playerVolumeLinear
    ) {
        if (index >= steps.size()) {
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(
            () -> world.execute(
                () -> {
                    if (!isCurrentGeneration(playerUuid, generation)) {
                        return;
                    }
                    if (!playerEntityRef.isValid() || !npcRef.isValid()) {
                        return;
                    }
                    Store<EntityStore> store = playerEntityRef.getStore();
                    if (store == null) {
                        return;
                    }
                    if (!isDialoguePageActive(playerEntityRef, store)) {
                        return;
                    }

                    SpeechStep step = steps.get(index);
                    if (step.soundEventId() != null) {
                        ThreadLocalRandom rng = ThreadLocalRandom.current();
                        if (rng.nextFloat() >= voice.getSkipChance()) {
                            float pitchJitter = uniform(rng, voice.getPitchJitterMin(), voice.getPitchJitterMax());
                            float rateJitter = uniform(rng, voice.getRateJitterMin(), voice.getRateJitterMax());
                            float pitchMod = voice.getBasePitch() * pitchJitter * rateJitter;
                            float volDb = uniform(rng, -voice.getVolumeJitterDb(), voice.getVolumeJitterDb());
                            float volMod = AudioUtil.decibelsToLinearGain(volDb) * playerVolumeLinear;
                            UiSoundEffects.play2d(
                                playerEntityRef, store, step.soundEventId(), SoundCategory.SFX, volMod, pitchMod
                            );
                        }
                    }

                    int next = index + 1;
                    if (next >= steps.size()) {
                        return;
                    }
                    scheduleStep(
                        playerEntityRef,
                        npcRef,
                        world,
                        voice,
                        generation,
                        playerUuid,
                        steps,
                        next,
                        step.delayAfterMs(),
                        playerVolumeLinear
                    );
                }
            ),
            Math.max(0L, delayMs),
            TimeUnit.MILLISECONDS
        );
    }

    private static boolean isDialoguePageActive(@Nonnull Ref<EntityStore> playerEntityRef, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        CustomUIPage page = player.getPageManager().getCustomPage();
        return page instanceof DialoguePage;
    }

    private static float uniform(@Nonnull ThreadLocalRandom rng, float min, float max) {
        float lo = Math.min(min, max);
        float hi = Math.max(min, max);
        if (hi <= lo) {
            return lo;
        }
        return lo + rng.nextFloat() * (hi - lo);
    }

    private static long nextGeneration(@Nonnull UUID playerUuid) {
        return GENERATION_BY_PLAYER.computeIfAbsent(playerUuid, u -> new AtomicLong()).incrementAndGet();
    }

    private static boolean isCurrentGeneration(@Nonnull UUID playerUuid, long generation) {
        AtomicLong cur = GENERATION_BY_PLAYER.get(playerUuid);
        return cur != null && cur.get() == generation;
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Ref<EntityStore> playerEntityRef, @Nonnull Store<EntityStore> store) {
        com.hypixel.hytale.server.core.entity.UUIDComponent uuid =
            store.getComponent(playerEntityRef, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        return uuid != null ? uuid.getUuid() : null;
    }

    /** @param soundEventId null = silence/pause step */
    record SpeechStep(@Nullable String soundEventId, int delayAfterMs) {}
}
