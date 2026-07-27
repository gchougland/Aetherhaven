package com.hexvane.aetherhaven.schedule;

import javax.annotation.Nullable;

/** Crossmod schedule location symbol from {@code Server/Aetherhaven/ScheduleLocations/<symbol>.json}. */
public final class ScheduleLocationDefinition {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private int schemaVersion = SUPPORTED_SCHEMA_VERSION;

    @Nullable
    private String constructionId;

    @Nullable
    private String displayNameLangKey;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    @Nullable
    public String getConstructionId() {
        return constructionId;
    }

    @Nullable
    public String getDisplayNameLangKey() {
        return displayNameLangKey;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public void setConstructionId(@Nullable String constructionId) {
        this.constructionId = constructionId;
    }

    public void setDisplayNameLangKey(@Nullable String displayNameLangKey) {
        this.displayNameLangKey = displayNameLangKey;
    }
}
