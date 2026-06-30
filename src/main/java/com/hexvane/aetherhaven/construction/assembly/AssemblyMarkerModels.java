package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Cached assembly marker models with runtime texture and grow scale. */
public final class AssemblyMarkerModels {
    /** Building marker prop is half block at unit scale; 2x matches a visible placement ghost. */
    private static final float BUILDING_SCALE_MIN = 1.06f;
    private static final float BUILDING_SCALE_MAX = 2.12f;

    /** Destruction marker prop renders at half block extent at unit scale. */
    private static final float DESTRUCTION_SCALE_MIN = 2.2f;
    private static final float DESTRUCTION_SCALE_MAX = 4.0f;

    private static final float SCALE_EPS = 1.0e-4f;

    @Nullable
    private static volatile Model buildingTemplate;

    @Nullable
    private static volatile Model destructionTemplate;

    private AssemblyMarkerModels() {}

    public static float scaleForGrow01(@Nonnull AssemblyMarkerKind kind, double grow01) {
        double g = Math.min(1.0, Math.max(0.0, grow01));
        if (kind == AssemblyMarkerKind.PLACING) {
            return (float) (BUILDING_SCALE_MIN + (BUILDING_SCALE_MAX - BUILDING_SCALE_MIN) * g);
        }
        return (float) (DESTRUCTION_SCALE_MIN + (DESTRUCTION_SCALE_MAX - DESTRUCTION_SCALE_MIN) * g);
    }

    @Nullable
    public static Model modelFor(
        @Nonnull AssemblyMarkerKind kind,
        @Nullable String texturePath,
        float scale
    ) {
        Model template = templateFor(kind);
        if (template == null) {
            return null;
        }
        String tex =
            kind == AssemblyMarkerKind.PLACING
                ? AssemblyMarkerTextureResolver.entitySafeTexture(
                    texturePath != null && !texturePath.isBlank() ? texturePath.trim() : template.getTexture()
                )
                : template.getTexture();
        return copyWithTextureAndScale(template, tex, scale);
    }

    public static boolean scaleChanged(float prev, float next) {
        return Math.abs(prev - next) > SCALE_EPS;
    }

    @Nullable
    private static Model templateFor(@Nonnull AssemblyMarkerKind kind) {
        if (kind == AssemblyMarkerKind.PLACING) {
            Model cached = buildingTemplate;
            if (cached != null) {
                return cached;
            }
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(AetherhavenConstants.MODEL_ASSET_BUILDING_MARKER);
            if (asset == null) {
                return null;
            }
            buildingTemplate = Model.createUnitScaleModel(asset);
            return buildingTemplate;
        }
        Model cached = destructionTemplate;
        if (cached != null) {
            return cached;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(AetherhavenConstants.MODEL_ASSET_DESTRUCTION_MARKER);
        if (asset == null) {
            return null;
        }
        destructionTemplate = Model.createUnitScaleModel(asset);
        return destructionTemplate;
    }

    @Nonnull
    private static Model copyWithTextureAndScale(@Nonnull Model template, @Nullable String texture, float scale) {
        return new Model(
            template.getModelAssetId(),
            scale,
            template.getRandomAttachmentIds(),
            template.getAttachments(),
            template.getBoundingBox(),
            template.getModel(),
            texture,
            template.getGradientSet(),
            template.getGradientId(),
            template.getEyeHeight(),
            template.getCrouchOffset(),
            template.getSittingOffset(),
            template.getSleepingOffset(),
            template.getAnimationSetMap(),
            template.getCamera(),
            template.getLight(),
            template.getParticles(),
            template.getTrails(),
            template.getPhysicsValues(),
            template.getDetailBoxes(),
            template.getPhobia(),
            template.getPhobiaModelAssetId()
        );
    }
}
