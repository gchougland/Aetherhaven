package com.hexvane.aetherhaven.guide;

import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Maps {@link com.hypixel.hytale.server.core.universe.PlayerRef#getLanguage()} to guide markdown folders. */
public final class GuideLocaleResolver {
    private static final String PREFIX = AetherhavenAssetPaths.GUIDE_TOPICS + "/";
    private static final String DEFAULT_LOCALE = "en-US";

    /** Folders under {@link AetherhavenAssetPaths#GUIDE_TOPICS}. */
    private static final List<String> GUIDE_LOCALES = List.of(
        "en-US",
        "zh-CN",
        "zh-TW",
        "fr-FR",
        "de-DE",
        "ja-JP",
        "ko-KR",
        "pt-BR",
        "ru-RU",
        "es-ES",
        "es-419",
        "tr-TR",
        "uk-UA"
    );

    private GuideLocaleResolver() {}

    @Nonnull
    public static String resolve(@Nonnull ClassLoader classLoader, @Nullable String playerLanguage) {
        String normalized = normalizeTag(playerLanguage);
        if (hasWelcome(classLoader, normalized)) {
            return normalized;
        }
        String lang = primaryLanguage(normalized);
        for (String candidate : GUIDE_LOCALES) {
            if (candidate.equals(normalized)) {
                continue;
            }
            if (primaryLanguage(candidate).equals(lang) && hasWelcome(classLoader, candidate)) {
                return candidate;
            }
        }
        return DEFAULT_LOCALE;
    }

    @Nonnull
    private static String normalizeTag(@Nullable String playerLanguage) {
        if (playerLanguage == null || playerLanguage.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return playerLanguage.trim().replace('_', '-');
    }

    @Nonnull
    private static String primaryLanguage(@Nonnull String localeTag) {
        int dash = localeTag.indexOf('-');
        if (dash <= 0) {
            return localeTag.toLowerCase(Locale.ROOT);
        }
        return localeTag.substring(0, dash).toLowerCase(Locale.ROOT);
    }

    private static boolean hasWelcome(@Nonnull ClassLoader cl, @Nonnull String locale) {
        if (DEFAULT_LOCALE.equals(locale)) {
            return cl.getResource(PREFIX + DEFAULT_LOCALE + "/welcome.md") != null
                || cl.getResource(PREFIX + "welcome.md") != null;
        }
        return cl.getResource(PREFIX + locale + "/welcome.md") != null;
    }
}
