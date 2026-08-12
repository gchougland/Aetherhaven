package com.hexvane.aetherhaven.asset;

import com.hypixel.hytale.protocol.Asset;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Filters which pack Common assets are force-sent on join so clients are not double-fed assets vanilla already
 * requested, and so non-gameplay folders (wiki Docs, etc.) are never pushed.
 */
public final class AetherhavenCommonAssetDelivery {
    private static final String[] GAMEPLAY_PREFIXES = {
        "Icons/",
        "UI/",
        "Items/",
        "Blocks/",
        "Sounds/",
        "NPC/",
        "Characters/",
        "Cosmetics/",
        "Particles/",
        "Music/",
    };

    private AetherhavenCommonAssetDelivery() {}

    /** True when the asset path under {@code Common/} is needed for gameplay icons, UI, models, or audio. */
    public static boolean isGameplayCommonAsset(@Nonnull String assetName) {
        String name = assetName.replace('\\', '/');
        String lower = name.toLowerCase(Locale.ROOT);
        for (String prefix : GAMEPLAY_PREFIXES) {
            if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gameplay pack assets whose hashes were not already present in the client's {@code RequestAssets} list (vanilla
     * will send those).
     */
    @Nonnull
    public static List<CommonAsset> assetsToForceSend(
        @Nonnull List<CommonAsset> packAssets,
        @Nullable Asset[] alreadyRequested
    ) {
        Set<String> requestedHashes = new HashSet<>();
        if (alreadyRequested != null) {
            for (Asset asset : alreadyRequested) {
                if (asset != null && asset.hash != null && !asset.hash.isBlank()) {
                    requestedHashes.add(asset.hash);
                }
            }
        }
        List<CommonAsset> out = new ArrayList<>();
        for (CommonAsset asset : packAssets) {
            if (asset == null || asset.getName() == null) {
                continue;
            }
            if (!isGameplayCommonAsset(asset.getName())) {
                continue;
            }
            String hash = asset.getHash();
            if (hash != null && requestedHashes.contains(hash)) {
                continue;
            }
            out.add(asset);
        }
        return out;
    }
}
