package com.hexvane.aetherhaven.quest.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestObjective {
    @SerializedName("id")
    @Nullable
    private String id;

    @SerializedName("kind")
    @Nullable
    private String kind;

    @SerializedName("text")
    @Nullable
    private String text;

    @SerializedName("textLangKey")
    @Nullable
    private String textLangKey;

    @SerializedName("constructionId")
    @Nullable
    private String constructionId;

    @SerializedName("npcRoleId")
    @Nullable
    private String npcRoleId;

    /** For {@code kind: entity_kills}. */
    @SerializedName("killCount")
    private int killCount;

    @SerializedName("entityTagsAny")
    @Nullable
    private List<String> entityTagsAny;

    @SerializedName("entityIdsAny")
    @Nullable
    private List<String> entityIdsAny;

    @SerializedName("entityTagsAll")
    @Nullable
    private List<String> entityTagsAll;

    @SerializedName("excludeEntityIds")
    @Nullable
    private List<String> excludeEntityIds;

    @SerializedName("excludeTagsAny")
    @Nullable
    private List<String> excludeTagsAny;

    @Nullable
    public String id() {
        return id;
    }

    @Nullable
    public String kind() {
        return kind;
    }

    @Nullable
    public String text() {
        return text;
    }

    @Nullable
    public String textLangKey() {
        return textLangKey;
    }

    @Nullable
    public String constructionId() {
        return constructionId;
    }

    @Nullable
    public String npcRoleId() {
        return npcRoleId;
    }

    public int killCount() {
        return killCount;
    }

    @Nonnull
    public List<String> entityTagsAnyOrEmpty() {
        return entityTagsAny != null ? entityTagsAny : List.of();
    }

    @Nonnull
    public List<String> entityIdsAnyOrEmpty() {
        return entityIdsAny != null ? entityIdsAny : List.of();
    }

    @Nonnull
    public List<String> entityTagsAllOrEmpty() {
        return entityTagsAll != null ? entityTagsAll : List.of();
    }

    @Nonnull
    public List<String> excludeEntityIdsOrEmpty() {
        return excludeEntityIds != null ? excludeEntityIds : List.of();
    }

    @Nonnull
    public List<String> excludeTagsAnyOrEmpty() {
        return excludeTagsAny != null ? excludeTagsAny : List.of();
    }

    public boolean hasEntityKillFilters() {
        return !entityTagsAnyOrEmpty().isEmpty()
            || !entityIdsAnyOrEmpty().isEmpty()
            || !entityTagsAllOrEmpty().isEmpty();
    }
}
