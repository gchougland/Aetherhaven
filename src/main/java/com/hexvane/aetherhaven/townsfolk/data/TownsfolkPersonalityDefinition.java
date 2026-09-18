package com.hexvane.aetherhaven.townsfolk.data;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
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
    /** What the villager thinks about. The gold coin is left out under an economy provider: it is no item then. */
    public Map<String, Double> getThoughtItemWeights() {
        Map<String, Double> weights = weightsOrEmpty(thoughtItemWeights);
        if (AetherhavenEconomy.usesCoinItem() || !weights.containsKey(AetherhavenConstants.ITEM_GOLD_COIN)) {
            return weights;
        }
        Map<String, Double> kept = new HashMap<>(weights);
        kept.remove(AetherhavenConstants.ITEM_GOLD_COIN);
        return Collections.unmodifiableMap(kept);
    }

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
