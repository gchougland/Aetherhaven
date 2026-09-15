package com.hexvane.aetherhaven.townsfolk.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownsfolkPersonalityDefinition {
    @SerializedName("id")
    private String id = "";

    @SerializedName("dialogueGreetingLangKeys")
    @Nullable
    private List<String> dialogueGreetingLangKeys;

    @SerializedName("leisurePoiTagWeights")
    @Nullable
    private Map<String, Double> leisurePoiTagWeights;

    @SerializedName("preferredScheduleLocations")
    @Nullable
    private List<String> preferredScheduleLocations;

    @SerializedName("thoughtItemWeights")
    private Map<String, Double> thoughtItemWeights;

    @SerializedName("socialEmoteWeights")
    private Map<String, Double> socialEmoteWeights;

    @SerializedName("idleEmoteWeights")
    private Map<String, Double> idleEmoteWeights;

    @Nonnull
    public Map<String, Double> getThoughtItemWeights() { return weightsOrEmpty(thoughtItemWeights); }

    @Nonnull
    public Map<String, Double> getSocialEmoteWeights() { return weightsOrEmpty(socialEmoteWeights); }

    @Nonnull
    public Map<String, Double> getIdleEmoteWeights() { return weightsOrEmpty(idleEmoteWeights); }

    private static Map<String, Double> weightsOrEmpty(Map<String, Double> weights) {
        return weights == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(weights));
    }

    @Nonnull
    public String getId() {
        return id != null ? id.trim() : "";
    }

    @Nonnull
    public List<String> getDialogueGreetingLangKeys() {
        return listOrEmpty(dialogueGreetingLangKeys);
    }

    @Nonnull
    public Map<String, Double> getLeisurePoiTagWeights() {
        if (leisurePoiTagWeights == null || leisurePoiTagWeights.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new HashMap<>(leisurePoiTagWeights));
    }

    @Nonnull
    public List<String> getPreferredScheduleLocations() {
        return listOrEmpty(preferredScheduleLocations);
    }

    @Nonnull
    private static List<String> listOrEmpty(@Nullable List<String> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(in));
    }
}
