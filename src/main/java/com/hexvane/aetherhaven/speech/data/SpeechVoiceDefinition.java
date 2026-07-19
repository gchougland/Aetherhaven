package com.hexvane.aetherhaven.speech.data;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-villager dialogue speech blip profile (base tone + cadence; per-syllable jitter is also here). */
public final class SpeechVoiceDefinition {
    public static final String DEFAULT_SOUND_EVENT_ID = "Aetherhaven_Speech_Blip";
    public static final String DEFAULT_VOICE_ID = "mid";

    @SerializedName("id")
    private String id = "";

    @SerializedName("soundEventId")
    @Nullable
    private String soundEventId;

    @SerializedName("basePitch")
    @Nullable
    private Float basePitch;

    @SerializedName("intervalMs")
    @Nullable
    private Integer intervalMs;

    @SerializedName("pitchJitterMin")
    @Nullable
    private Float pitchJitterMin;

    @SerializedName("pitchJitterMax")
    @Nullable
    private Float pitchJitterMax;

    @SerializedName("rateJitterMin")
    @Nullable
    private Float rateJitterMin;

    @SerializedName("rateJitterMax")
    @Nullable
    private Float rateJitterMax;

    @SerializedName("volumeJitterDb")
    @Nullable
    private Float volumeJitterDb;

    @SerializedName("skipChance")
    @Nullable
    private Float skipChance;

    @Nonnull
    public String getId() {
        return id != null ? id.trim() : "";
    }

    @Nonnull
    public String getSoundEventId() {
        if (soundEventId != null && !soundEventId.isBlank()) {
            return soundEventId.trim();
        }
        return DEFAULT_SOUND_EVENT_ID;
    }

    public float getBasePitch() {
        float v = basePitch != null ? basePitch : 1f;
        return v > 0.01f ? v : 1f;
    }

    public int getIntervalMs() {
        int v = intervalMs != null ? intervalMs : 100;
        return Math.max(40, v);
    }

    public float getPitchJitterMin() {
        return pitchJitterMin != null ? pitchJitterMin : 0.95f;
    }

    public float getPitchJitterMax() {
        return pitchJitterMax != null ? pitchJitterMax : 1.05f;
    }

    public float getRateJitterMin() {
        return rateJitterMin != null ? rateJitterMin : 0.90f;
    }

    public float getRateJitterMax() {
        return rateJitterMax != null ? rateJitterMax : 1.10f;
    }

    public float getVolumeJitterDb() {
        float v = volumeJitterDb != null ? volumeJitterDb : 2f;
        return Math.max(0f, v);
    }

    public float getSkipChance() {
        float v = skipChance != null ? skipChance : 0.12f;
        if (v < 0f) {
            return 0f;
        }
        return Math.min(0.9f, v);
    }
}
