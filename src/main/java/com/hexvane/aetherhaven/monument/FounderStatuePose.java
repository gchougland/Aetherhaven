package com.hexvane.aetherhaven.monument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The poses a founder statue can hold. Each pose is one frame of a native player animation, baked into a still clip
 * by a one-off script; which clip a pose uses is data, in the animation sets of the
 * {@value #POSES_MODEL_ID} model asset.
 */
public enum FounderStatuePose {
    /** The plain standing statue, as every statue stood before poses existed. Saved as a blank id. */
    STANDING("standing", null),
    PROUD("proud", "StatueProud"),
    RALLY("rally", "StatueRally"),
    WAVE("wave", "StatueWave"),
    CHEER("cheer", "StatueCheer"),
    SIT("sit", "StatueSit");

    /** Model asset whose animation sets name the still clip of each pose. Never used as a statue's own model id. */
    public static final String POSES_MODEL_ID = "Aetherhaven_Founder_Statue_Poses";
    private static final String LANG = "aetherhaven_monument.aetherhaven.monument.pose.";

    private final String id;
    @Nullable
    private final String animationSetId;

    FounderStatuePose(@Nonnull String id, @Nullable String animationSetId) {
        this.id = id;
        this.animationSetId = animationSetId;
    }

    /** Stable id used in events and lang keys. */
    @Nonnull
    public String id() {
        return id;
    }

    /** What is saved on the pedestal: blank for {@link #STANDING} so older statues keep reading as standing. */
    @Nonnull
    public String savedId() {
        return this == STANDING ? "" : id;
    }

    /** Animation set of {@value #POSES_MODEL_ID} that holds this pose, or null for the plain standing statue. */
    @Nullable
    public String animationSetId() {
        return animationSetId;
    }

    @Nonnull
    public String nameKey() {
        return LANG + id + ".name";
    }

    @Nonnull
    public String descriptionKey() {
        return LANG + id + ".description";
    }

    /** Unknown and blank ids read as {@link #STANDING}, so a pose removed later never breaks a saved statue. */
    @Nonnull
    public static FounderStatuePose byId(@Nullable String id) {
        if (id != null) {
            for (FounderStatuePose pose : values()) {
                if (pose.id.equalsIgnoreCase(id.trim())) {
                    return pose;
                }
            }
        }
        return STANDING;
    }
}
