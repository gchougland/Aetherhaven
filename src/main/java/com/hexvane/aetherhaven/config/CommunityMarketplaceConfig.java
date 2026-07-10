package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;

/** Remote community building marketplace (browse / download / submit). JSON key: {@code CommunityMarketplace}. */
public final class CommunityMarketplaceConfig {
    public static final BuilderCodec<CommunityMarketplaceConfig> CODEC =
        BuilderCodec.builder(CommunityMarketplaceConfig.class, CommunityMarketplaceConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN), (o, v) -> o.enabled = v != null && v, o -> o.enabled)
            .documentation("When true, the plot crafting bench shows a Community tab backed by ApiBaseUrl.")
            .add()
            .append(
                new KeyedCodec<>("ApiBaseUrl", Codec.STRING),
                (o, v) -> o.apiBaseUrl = v != null ? v.trim() : "",
                o -> o.apiBaseUrl
            )
            .documentation(
                "Base URL of the community marketplace API (no trailing slash). Public URL only — no secrets here."
            )
            .add()
            .append(
                new KeyedCodec<>("ManifestRefreshMinutes", Codec.INTEGER),
                (o, v) -> o.manifestRefreshMinutes = v != null ? v : 5,
                o -> o.manifestRefreshMinutes
            )
            .documentation("Minimum minutes between manifest metadata refreshes while the Community tab is open.")
            .add()
            .append(
                new KeyedCodec<>("SubmitOnSaveDefault", Codec.BOOLEAN),
                (o, v) -> o.submitOnSaveDefault = v != null && v,
                o -> o.submitOnSaveDefault
            )
            .documentation("Default checked state for plot creator 'Submit to community' on save.")
            .add()
            .build();

    private boolean enabled = true;
    private String apiBaseUrl = "https://aetherhaven.net";
    private int manifestRefreshMinutes = 5;
    private boolean submitOnSaveDefault = true;

    public boolean isEnabled() {
        return enabled;
    }

    @Nonnull
    public String getApiBaseUrl() {
        String url = apiBaseUrl != null ? apiBaseUrl.trim() : "";
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isBlank() ? "https://aetherhaven.net" : url;
    }

    public int getManifestRefreshMinutes() {
        int m = manifestRefreshMinutes;
        return m >= 1 && m <= 120 ? m : 5;
    }

    public boolean isSubmitOnSaveDefault() {
        return submitOnSaveDefault;
    }
}
