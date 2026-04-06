package com.hexvane.aetherhaven.quest.data;

import com.google.gson.annotations.SerializedName;
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
}
