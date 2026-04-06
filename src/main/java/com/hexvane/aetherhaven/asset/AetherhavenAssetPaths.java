package com.hexvane.aetherhaven.asset;

import javax.annotation.Nonnull;

/**
 * Namespaced paths under each {@link com.hypixel.hytale.assetstore.AssetPack} root so other mods can use
 * {@code Server/Quests/} or {@code Server/Dialogue/} without colliding with Aetherhaven data.
 */
public final class AetherhavenAssetPaths {
    /** Relative to pack root: quest JSON (recursive). */
    public static final String QUESTS = "Server/Aetherhaven/Quests";

    /** Relative to pack root: dialogue tree JSON (recursive). */
    public static final String DIALOGUE = "Server/Aetherhaven/Dialogue";

    private AetherhavenAssetPaths() {}

    @Nonnull
    public static String questsPrefix() {
        return QUESTS + "/";
    }

    @Nonnull
    public static String dialoguePrefix() {
        return DIALOGUE + "/";
    }
}
