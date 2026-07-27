package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;

/** Remote support bundle upload for world debugging. JSON key: {@code SupportUpload}. */
public final class SupportUploadConfig {
    public static final int DEFAULT_MAX_BUNDLE_BYTES = 40 * 1024 * 1024;

    public static final BuilderCodec<SupportUploadConfig> CODEC =
        BuilderCodec.builder(SupportUploadConfig.class, SupportUploadConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN), (o, v) -> o.enabled = v != null && v, o -> o.enabled)
            .documentation("When true, players may run /aetherhaven support upload to send debug files to ApiBaseUrl.")
            .add()
            .append(
                new KeyedCodec<>("MaxBundleBytes", Codec.INTEGER),
                (o, v) -> o.maxBundleBytes = v != null ? v : DEFAULT_MAX_BUNDLE_BYTES,
                o -> o.maxBundleBytes
            )
            .documentation("Maximum zip size before upload is refused (default 40 MB).")
            .add()
            .build();

    private boolean enabled = true;
    private int maxBundleBytes = DEFAULT_MAX_BUNDLE_BYTES;

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxBundleBytes() {
        int bytes = maxBundleBytes;
        if (bytes < 1024 * 1024) {
            return 1024 * 1024;
        }
        if (bytes > 50 * 1024 * 1024) {
            return 50 * 1024 * 1024;
        }
        return bytes;
    }
}
