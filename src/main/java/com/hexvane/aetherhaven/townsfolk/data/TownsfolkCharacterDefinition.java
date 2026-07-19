package com.hexvane.aetherhaven.townsfolk.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownsfolkCharacterDefinition {
    @SerializedName("id")
    private String id = "";

    @SerializedName("displayName")
    @Nullable
    private String displayName;

    @SerializedName("gender")
    @Nullable
    private String gender;

    @SerializedName("race")
    @Nullable
    private String race;

    @SerializedName("modelAssetId")
    private String modelAssetId = "";

    /** All personality traits for this character (fixed in data; not chosen at spawn). */
    @SerializedName("personalityIds")
    @Nullable
    private List<String> personalityIds;

    @SerializedName("allowedAssignmentKinds")
    @Nullable
    private List<String> allowedAssignmentKinds;

    @SerializedName("befriendable")
    @Nullable
    private Boolean befriendable;

    /** Equipment profile for guards or job visuals (see Server/Aetherhaven/Equipment/). */
    @SerializedName("equipmentProfileId")
    @Nullable
    private String equipmentProfileId;

    /** Optional model scale multiplier (1.0 = default). Applied when the character model is set on spawn. */
    @SerializedName("modelScale")
    @Nullable
    private Float modelScale;

    /** Optional dialogue speech voice profile id (see {@code Server/Aetherhaven/SpeechVoices/}). */
    @SerializedName("speechVoiceId")
    @Nullable
    private String speechVoiceId;

    @Nonnull
    public String getId() {
        return id != null ? id.trim() : "";
    }

    @Nullable
    public String getDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName.trim() : null;
    }

    @Nullable
    public String getGender() {
        return gender;
    }

    @Nullable
    public String getRace() {
        return race;
    }

    @Nonnull
    public String getModelAssetId() {
        return modelAssetId != null ? modelAssetId.trim() : "";
    }

    @Nonnull
    public List<String> getPersonalityIds() {
        return listOrEmpty(personalityIds);
    }

    @Nonnull
    public List<String> getAllowedAssignmentKinds() {
        return listOrEmpty(allowedAssignmentKinds);
    }

    public boolean isBefriendable() {
        return Boolean.TRUE.equals(befriendable);
    }

    @Nullable
    public String getEquipmentProfileId() {
        return equipmentProfileId != null && !equipmentProfileId.isBlank() ? equipmentProfileId.trim() : null;
    }

    /** @return custom model scale when {@code modelScale} is set and valid; otherwise {@code null} */
    @Nullable
    public Float getModelScale() {
        if (modelScale == null || modelScale <= 0f || Float.isNaN(modelScale) || Float.isInfinite(modelScale)) {
            return null;
        }
        return modelScale;
    }

    @Nullable
    public String getSpeechVoiceId() {
        return speechVoiceId != null && !speechVoiceId.isBlank() ? speechVoiceId.trim() : null;
    }

    public boolean supportsAssignment(@Nonnull String assignmentKind) {
        String want = assignmentKind.trim().toLowerCase();
        for (String k : getAllowedAssignmentKinds()) {
            if (k != null && k.trim().equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static List<String> listOrEmpty(@Nullable List<String> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(in));
    }
}
