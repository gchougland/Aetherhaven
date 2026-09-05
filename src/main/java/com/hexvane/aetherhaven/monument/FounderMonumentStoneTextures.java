package com.hexvane.aetherhaven.monument;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies the two packaged stone textures. Never calls {@code CommonAssetRegistry.addCommonAsset}: every registry
 * entry is required on join, and generating per-cosmetic canvases is what hung packed worlds after mesh clones
 * were already removed.
 */
final class FounderMonumentStoneTextures {
    private FounderMonumentStoneTextures() {}

    @Nonnull
    static Prepared prepare(@Nonnull String baseTexture, @Nonnull ModelAttachment[] attachments) {
        ModelAttachment[] stoneAttachments = new ModelAttachment[attachments.length];
        for (int i = 0; i < attachments.length; i++) {
            ModelAttachment source = attachments[i];
            stoneAttachments[i] = new ModelAttachment(
                source.getModel(),
                stoneTextureFor(source.getTexture()),
                null,
                null,
                source.getWeight()
            );
        }
        return new Prepared(stoneTextureFor(baseTexture), stoneAttachments);
    }

    @Nonnull
    private static String stoneTextureFor(@Nullable String sourceTexture) {
        if (AetherhavenConstants.FOUNDER_MONUMENT_STATUE_BASE_TEXTURE.equals(sourceTexture)
            || AetherhavenConstants.FOUNDER_MONUMENT_STATUE_TEXTURE.equals(sourceTexture)) {
            return sourceTexture;
        }
        return AetherhavenConstants.FOUNDER_MONUMENT_STATUE_TEXTURE;
    }

    record Prepared(@Nonnull String baseTexture, @Nonnull ModelAttachment[] attachments) {}
}
