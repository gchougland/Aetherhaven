package com.hexvane.aetherhaven.villager.data;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Gameplay metadata for an NPC role (dialogue keys, rep, inn pool, schedule location bindings). Loaded from
 * {@code Server/Aetherhaven/Villagers/}. {@link #getNpcRoleId()} matches Hytale {@code NPCEntity} role name.
 */
public final class VillagerDefinition {
    @SerializedName("npcRoleId")
    private String npcRoleId = "";

    @SerializedName("dialogueVillagerKind")
    private String dialogueVillagerKind = "";

    @SerializedName("residentTreeId")
    @Nullable
    private String residentTreeId;

    @SerializedName("visitorTreeId")
    @Nullable
    private String visitorTreeId;

    @SerializedName("scheduleRoleId")
    @Nullable
    private String scheduleRoleId;

    @SerializedName("workConstructionId")
    @Nullable
    private String workConstructionId;

    @SerializedName("scheduleSharedLocations")
    @Nullable
    private Map<String, String> scheduleSharedLocations;

    @SerializedName("reputationMilestones")
    @Nullable
    private List<VillagerReputationMilestoneJson> reputationMilestones;

    @SerializedName("displayName")
    @Nullable
    private String displayName;

    /** File name under {@code Icons/ModelsGenerated/} (e.g. {@code Merchant.png}) or null to use fallback. */
    @SerializedName("portraitIcon")
    @Nullable
    private String portraitIcon;

    @SerializedName("uiSortOrder")
    @Nullable
    private Integer uiSortOrder;

    @SerializedName("innPoolEligible")
    @Nullable
    private Boolean innPoolEligible;

    @SerializedName("innPoolOrder")
    @Nullable
    private Integer innPoolOrder;

    @SerializedName("visitorBindingKind")
    @Nullable
    private String visitorBindingKind;

    @SerializedName("weeklySchedule")
    @Nullable
    private VillagerScheduleDefinition weeklySchedule;

    @SerializedName("dailyTalkBonus")
    @Nullable
    private Integer dailyTalkBonus;

    /** Item ids that grant maximum gift reputation for this role. */
    @SerializedName("giftLoves")
    @Nullable
    private List<String> giftLoves;

    @SerializedName("giftLikes")
    @Nullable
    private List<String> giftLikes;

    @SerializedName("giftDislikes")
    @Nullable
    private List<String> giftDislikes;

    @Nonnull
    public String getNpcRoleId() {
        return npcRoleId != null ? npcRoleId.trim() : "";
    }

    @Nonnull
    public String getDialogueVillagerKind() {
        return dialogueVillagerKind != null ? dialogueVillagerKind.trim() : "";
    }

    @Nullable
    public String getResidentTreeId() {
        return residentTreeId;
    }

    @Nullable
    public String getVisitorTreeId() {
        return visitorTreeId;
    }

    /**
     * When non-blank, schedule JSON path key; defaults to {@link #getNpcRoleId()}.
     */
    @Nonnull
    public String effectiveScheduleRoleId() {
        if (scheduleRoleId != null && !scheduleRoleId.isBlank()) {
            return scheduleRoleId.trim();
        }
        return getNpcRoleId();
    }

    @Nullable
    public String getWorkConstructionId() {
        return workConstructionId != null && !workConstructionId.isBlank() ? workConstructionId.trim() : null;
    }

    @Nonnull
    public Map<String, String> getScheduleSharedLocations() {
        if (scheduleSharedLocations == null || scheduleSharedLocations.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new HashMap<>(scheduleSharedLocations));
    }

    @Nullable
    public String sharedConstructionIdForLocationSymbol(@Nonnull String locationSymbol) {
        String k = locationSymbol.trim().toLowerCase();
        if (k.isEmpty() || scheduleSharedLocations == null) {
            return null;
        }
        for (var e : scheduleSharedLocations.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(k)) {
                String v = e.getValue();
                return v != null && !v.isBlank() ? v.trim() : null;
            }
        }
        return null;
    }

    @Nonnull
    public List<VillagerReputationMilestoneJson> getReputationMilestones() {
        if (reputationMilestones == null) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(reputationMilestones));
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public String getPortraitIcon() {
        return portraitIcon;
    }

    public int getUiSortOrder() {
        return uiSortOrder != null ? uiSortOrder : 1000;
    }

    public boolean isInnPoolEligible() {
        return Boolean.TRUE.equals(innPoolEligible);
    }

    public int getInnPoolOrder() {
        return innPoolOrder != null ? innPoolOrder : 0;
    }

    @Nullable
    public String getVisitorBindingKind() {
        if (visitorBindingKind == null || visitorBindingKind.isBlank()) {
            return null;
        }
        return visitorBindingKind.trim();
    }

    @Nullable
    public VillagerScheduleDefinition getWeeklySchedule() {
        return weeklySchedule;
    }

    public int getDailyTalkBonusOrDefault(int defaultValue) {
        return dailyTalkBonus != null ? dailyTalkBonus : defaultValue;
    }

    @Nonnull
    public List<String> getGiftLoves() {
        return listOrEmpty(giftLoves);
    }

    @Nonnull
    public List<String> getGiftLikes() {
        return listOrEmpty(giftLikes);
    }

    @Nonnull
    public List<String> getGiftDislikes() {
        return listOrEmpty(giftDislikes);
    }

    @Nonnull
    private static List<String> listOrEmpty(@Nullable List<String> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(in));
    }
}
