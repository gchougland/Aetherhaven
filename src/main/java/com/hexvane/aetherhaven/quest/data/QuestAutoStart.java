package com.hexvane.aetherhaven.quest.data;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A town quest that begins by itself. {@code when} names a condition of the town (see
 * {@link com.hexvane.aetherhaven.quest.QuestAutoStartService}); the quest starts the first time it holds, once per town.
 *
 * <p>A quest started this way has nobody to hand a starting item to, so it cannot grant a plot token or blueprint.
 */
public final class QuestAutoStart {
    @SerializedName("when")
    @Nullable
    private String when;

    /**
     * When true, a town that has already done every step before the closing talk is not offered the quest: it is
     * marked done with no banner and no reward. Use it for quests that teach something the town already does.
     */
    @SerializedName("skipWhenDone")
    private boolean skipWhenDone;

    /**
     * A chat line for the town's members when the quest starts by itself: the giver saying, in a sentence or two,
     * why it has turned up. A quest taken in dialogue has the dialogue for that.
     */
    @SerializedName("announceLangKey")
    @Nullable
    private String announceLangKey;

    @Nullable
    public String announceLangKey() {
        return announceLangKey != null && !announceLangKey.isBlank() ? announceLangKey.trim() : null;
    }

    @Nonnull
    public String whenOrEmpty() {
        return when != null ? when.trim() : "";
    }

    public boolean skipWhenDone() {
        return skipWhenDone;
    }
}
