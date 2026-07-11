package com.hexvane.aetherhaven.guide;

import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackAssetFile;
import com.hypixel.hytale.logger.HytaleLogger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pack markdown under {@link AetherhavenAssetPaths#GUIDE_TOPICS} as {@code <locale>/<topicId>.md}. Later packs
 * override the same locale + topic id.
 */
public final class GuideTopicPackOverlay {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** locale → topicId → raw markdown */
    @Nonnull
    private final Map<String, Map<String, String>> byLocaleThenId;

    private GuideTopicPackOverlay(@Nonnull Map<String, Map<String, String>> byLocaleThenId) {
        this.byLocaleThenId = byLocaleThenId;
    }

    @Nonnull
    public static GuideTopicPackOverlay empty() {
        return new GuideTopicPackOverlay(Map.of());
    }

    @Nonnull
    public static GuideTopicPackOverlay loadFromAssetPacks() {
        List<PackAssetFile> files =
            AetherhavenPackAssetScanner.listFilesUnderAllPacks(AetherhavenAssetPaths.GUIDE_TOPICS, ".md");
        Map<String, Map<String, String>> map = new LinkedHashMap<>();
        int loaded = 0;
        for (PackAssetFile f : files) {
            TopicKey key = parseTopicKey(f.absolutePath(), AetherhavenAssetPaths.GUIDE_TOPICS);
            if (key == null) {
                LOGGER.atWarning().log(
                    "Skip guide topic %s (expected %s/<locale>/<topicId>.md)",
                    f.absolutePath(),
                    AetherhavenAssetPaths.GUIDE_TOPICS
                );
                continue;
            }
            try {
                String raw = Files.readString(f.absolutePath(), StandardCharsets.UTF_8);
                map.computeIfAbsent(key.locale(), k -> new LinkedHashMap<>()).put(key.topicId(), raw);
                loaded++;
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to read guide topic %s", f.absolutePath());
            }
        }
        if (loaded > 0) {
            LOGGER.atInfo().log(
                "Loaded %s guide topic file(s) from asset packs under %s",
                loaded,
                AetherhavenAssetPaths.GUIDE_TOPICS
            );
        }
        Map<String, Map<String, String>> frozen = new LinkedHashMap<>();
        for (var e : map.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        return new GuideTopicPackOverlay(Collections.unmodifiableMap(frozen));
    }

    /** Visible for tests. */
    @Nonnull
    public static GuideTopicPackOverlay of(@Nonnull Map<String, Map<String, String>> byLocaleThenId) {
        Map<String, Map<String, String>> frozen = new LinkedHashMap<>();
        for (var e : byLocaleThenId.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        return new GuideTopicPackOverlay(Collections.unmodifiableMap(frozen));
    }

    @Nullable
    public String rawMarkdown(@Nonnull String locale, @Nonnull String topicId) {
        Map<String, String> byId = byLocaleThenId.get(locale);
        if (byId == null) {
            return null;
        }
        return byId.get(topicId.trim());
    }

    public int topicCount() {
        int n = 0;
        for (Map<String, String> m : byLocaleThenId.values()) {
            n += m.size();
        }
        return n;
    }

    /**
     * Relative path under the guide topics root must be {@code <locale>/<topicId>.md} (one locale folder deep).
     */
    @Nullable
    static TopicKey parseTopicKey(@Nonnull Path absolutePath, @Nonnull String guideTopicsRootRelative) {
        String norm = absolutePath.toString().replace('\\', '/');
        String marker = guideTopicsRootRelative.replace('\\', '/');
        int idx = norm.toLowerCase(Locale.ROOT).lastIndexOf(marker.toLowerCase(Locale.ROOT));
        if (idx < 0) {
            return null;
        }
        String rel = norm.substring(idx + marker.length());
        if (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        String[] parts = rel.split("/");
        if (parts.length != 2) {
            return null;
        }
        String locale = parts[0].trim();
        String file = parts[1].trim();
        if (locale.isEmpty() || !file.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return null;
        }
        String topicId = file.substring(0, file.length() - 3).trim();
        if (topicId.isEmpty()) {
            return null;
        }
        return new TopicKey(locale, topicId);
    }

    record TopicKey(@Nonnull String locale, @Nonnull String topicId) {}
}
