package com.hexvane.aetherhaven.quest.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Loaded from JSON under {@code Server/Aetherhaven/Quests/} (asset packs or classpath fallback). */
public final class QuestDefinition {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    @SerializedName("schemaVersion")
    private int schemaVersion;

    @SerializedName("id")
    @Nullable
    private String id;

    @SerializedName("category")
    @Nullable
    private String category;

    @SerializedName("title")
    @Nullable
    private String title;

    @SerializedName("titleLangKey")
    @Nullable
    private String titleLangKey;

    @SerializedName("description")
    @Nullable
    private String description;

    @SerializedName("descriptionLangKey")
    @Nullable
    private String descriptionLangKey;

    @SerializedName("prerequisites")
    @Nullable
    private QuestPrerequisites prerequisites;

    @SerializedName("objectives")
    @Nullable
    private List<QuestObjective> objectives;

    @SerializedName("rewards")
    @Nullable
    private List<QuestReward> rewards;

    @SerializedName("lifecycle")
    @Nullable
    private QuestLifecycle lifecycle;

    @SerializedName("repeat")
    @Nullable
    private QuestRepeat repeat;

    /** When set, identifies which villager this house quest belongs to (management block / dialogue). */
    @SerializedName("assignNpcRoleId")
    @Nullable
    private String assignNpcRoleId;

    /**
     * If set, the plot sign item for this construction (see {@code plotTokenItemId} on the building JSON) is given to the
     * player when the quest starts (dialogue accept or debug grant).
     */
    @SerializedName("grantPlotTokenConstructionId")
    @Nullable
    private String grantPlotTokenConstructionId;

    @Nonnull
    public String idOrEmpty() {
        return id != null ? id.trim() : "";
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    @Nullable
    public String category() {
        return category;
    }

    @Nonnull
    public String titleOrId() {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return idOrEmpty();
    }

    @Nullable
    public String titleLangKey() {
        return titleLangKey;
    }

    @Nonnull
    public String descriptionOrDefault() {
        if (description != null && !description.isBlank()) {
            return description.trim();
        }
        return "No description for this quest yet.";
    }

    @Nullable
    public String descriptionLangKey() {
        return descriptionLangKey;
    }

    @Nonnull
    public QuestPrerequisites prerequisitesOrEmpty() {
        return prerequisites != null ? prerequisites : QuestPrerequisites.EMPTY;
    }

    @Nonnull
    public List<QuestObjective> objectivesOrEmpty() {
        return objectives != null ? objectives : Collections.emptyList();
    }

    @Nonnull
    public List<QuestReward> rewardsOrEmpty() {
        return rewards != null ? rewards : Collections.emptyList();
    }

    @Nonnull
    public QuestLifecycle lifecycleOrEmpty() {
        return lifecycle != null ? lifecycle : QuestLifecycle.EMPTY;
    }

    @Nonnull
    public QuestRepeat repeatOrDefault() {
        return repeat != null ? repeat : QuestRepeat.NONE;
    }

    @Nullable
    public String assignNpcRoleId() {
        return assignNpcRoleId != null ? assignNpcRoleId.trim() : null;
    }

    @Nullable
    public String grantPlotTokenConstructionId() {
        return grantPlotTokenConstructionId != null ? grantPlotTokenConstructionId.trim() : null;
    }

    /**
     * @return true if this objective should be tracked in {@link com.hexvane.aetherhaven.town.TownRecord}
     *     {@code questObjectiveProgress} boolean maps. {@code entity_kills} uses {@link #entityKillObjectiveIds()}
     *     instead.
     */
    public static boolean isTrackableObjective(@Nonnull QuestObjective o) {
        String k = o.kind();
        if (k == null || k.isBlank()) {
            return false;
        }
        String kt = k.trim();
        return !"journal".equalsIgnoreCase(kt) && !"entity_kills".equalsIgnoreCase(kt);
    }

    @Nonnull
    public List<String> trackableObjectiveIds() {
        List<QuestObjective> obs = objectivesOrEmpty();
        if (obs.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (QuestObjective o : obs) {
            if (isTrackableObjective(o) && o.id() != null && !o.id().isBlank()) {
                ids.add(o.id().trim());
            }
        }
        return ids;
    }

    @Nonnull
    public List<String> entityKillObjectiveIds() {
        List<QuestObjective> obs = objectivesOrEmpty();
        if (obs.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (QuestObjective o : obs) {
            String k = o.kind();
            if (k != null && "entity_kills".equalsIgnoreCase(k.trim()) && o.id() != null && !o.id().isBlank()) {
                ids.add(o.id().trim());
            }
        }
        return ids;
    }
}
