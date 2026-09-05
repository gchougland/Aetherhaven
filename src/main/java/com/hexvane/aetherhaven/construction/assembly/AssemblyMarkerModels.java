package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Cached assembly marker models with runtime texture and grow scale. */
public final class AssemblyMarkerModels {
    /** Placing markers are BlockEntity previews; unit scale matches a full block. */
    private static final float PLACE_SCALE_MIN = 0.25f;
    private static final float PLACE_SCALE_MAX = 1.0f;

    /** Destruction marker prop renders at half block extent at unit scale. */
    private static final float DESTRUCTION_SCALE_MIN = 2.2f;
    private static final float DESTRUCTION_SCALE_MAX = 4.0f;

    /** Slow continuous yaw while idle; ramps toward max as the channel grows. */
    private static final float SPIN_YAW_IDLE_RAD_PER_SEC = 0.7f;
    private static final float SPIN_YAW_MAX_RAD_PER_SEC = 8.0f;

    private static final float SCALE_EPS = 1.0e-4f;

    @Nullable
    private static volatile Model destructionTemplate;

    private AssemblyMarkerModels() {}

    public static float scaleForGrow01(@Nonnull AssemblyMarkerKind kind, double grow01) {
        double g = Math.min(1.0, Math.max(0.0, grow01));
        if (kind == AssemblyMarkerKind.PLACING) {
            return (float) (PLACE_SCALE_MIN + (PLACE_SCALE_MAX - PLACE_SCALE_MIN) * g);
        }
        return (float) (DESTRUCTION_SCALE_MIN + (DESTRUCTION_SCALE_MAX - DESTRUCTION_SCALE_MIN) * g);
    }

    /** Yaw spin rate for placing BlockEntity previews; idle is slow, grows with {@code grow01}. */
    public static float spinYawRadiansPerSec(double grow01) {
        double g = Math.min(1.0, Math.max(0.0, grow01));
        double t = g * g;
        return (float) (SPIN_YAW_IDLE_RAD_PER_SEC + (SPIN_YAW_MAX_RAD_PER_SEC - SPIN_YAW_IDLE_RAD_PER_SEC) * t);
    }

    /** Destruction markers only — placing uses {@code BlockEntity} + {@code EntityScaleComponent}. */
    @Nullable
    public static Model modelFor(
        @Nonnull AssemblyMarkerKind kind,
        @Nullable String texturePath,
        float scale
    ) {
        if (kind != AssemblyMarkerKind.CLEARING) {
            return null;
        }
        Model template = destructionTemplate();
        if (template == null) {
            return null;
        }
        return copyWithTextureAndScale(template, template.getTexture(), scale);
    }

    public static boolean scaleChanged(float prev, float next) {
        return Math.abs(prev - next) > SCALE_EPS;
    }

    @Nullable
    private static Model destructionTemplate() {
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
