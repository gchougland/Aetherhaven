package com.hexvane.aetherhaven.guide;

import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Loads Voile-style markdown topics from {@code Common/Docs/Hexvane_AetherhavenWiki/} and walks {@code welcome.md}
 * sub-topics for stable navigation order.
 *
 * <p>Display text ({@code name}, {@code description}, body) resolves per player locale; {@code sub-topics} order and
 * ids always come from the English canonical tree ({@code en-US/} or legacy flat path), plus pack
 * {@link com.hexvane.aetherhaven.asset.AetherhavenAssetPaths#GUIDE_PATCHES} extras and
 * {@link com.hexvane.aetherhaven.asset.AetherhavenAssetPaths#GUIDE_TOPICS} overlays.
 *
 * <p>Crossmod: ship {@code Server/Aetherhaven/GuideTopics/<locale>/<id>.md} and a GuidePatches JSON that adds the id
 * under an existing hub ({@code villagers}, {@code mechanics}, {@code welcome}, etc.).
 */
public final class GuideTopicRepository {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PREFIX = "Common/Docs/Hexvane_AetherhavenWiki/";
    private static final String DEFAULT_LOCALE = "en-US";

    private static final ConcurrentHashMap<String, GuideTopicRepository> CACHE = new ConcurrentHashMap<>();

    @Nonnull
    private final Map<String, GuideTopicFile> byId;
    @Nonnull
    private final List<GuideNavEntry> navEntries;

    private GuideTopicRepository(@Nonnull Map<String, GuideTopicFile> byId, @Nonnull List<GuideNavEntry> navEntries) {
        this.byId = byId;
        this.navEntries = navEntries;
    }

    @Nonnull
    public static GuideTopicRepository get(@Nonnull ClassLoader classLoader) {
        return get(classLoader, DEFAULT_LOCALE);
    }

    @Nonnull
    public static GuideTopicRepository get(@Nonnull ClassLoader classLoader, @Nullable String locale) {
        String key = GuideLocaleResolver.resolve(classLoader, locale);
        return CACHE.computeIfAbsent(key, k -> load(classLoader, k));
    }

    /** Test hook / asset reload. */
    public static void clearCache() {
        CACHE.clear();
    }

    @Nonnull
    private static GuideTopicRepository load(@Nonnull ClassLoader cl, @Nonnull String locale) {
        GuideTopicPackOverlay overlay = GuideTopicPackOverlay.loadFromAssetPacks();
        Map<String, List<String>> subTopicExtras = GuidePatchApplier.loadMergedSubTopicExtras();
        return load(cl, locale, overlay, subTopicExtras);
    }

    /** Visible for tests. */
    @Nonnull
    static GuideTopicRepository load(
        @Nonnull ClassLoader cl,
        @Nonnull String locale,
        @Nonnull GuideTopicPackOverlay overlay,
        @Nonnull Map<String, List<String>> subTopicExtras
    ) {
        Map<String, GuideTopicFile> map = new LinkedHashMap<>();
        List<GuideNavEntry> nav = new ArrayList<>();
        Set<String> completed = new LinkedHashSet<>();
        walk(cl, locale, overlay, subTopicExtras, "welcome", 0, map, nav, completed);
        return new GuideTopicRepository(map, nav);
    }

    private static void walk(
        @Nonnull ClassLoader cl,
        @Nonnull String locale,
        @Nonnull GuideTopicPackOverlay overlay,
        @Nonnull Map<String, List<String>> subTopicExtras,
        @Nonnull String topicId,
        int depth,
        @Nonnull Map<String, GuideTopicFile> map,
        @Nonnull List<GuideNavEntry> nav,
        @Nonnull Set<String> completed
    ) {
        String id = topicId.trim();
        if (id.isEmpty()) {
            return;
        }
        if (!completed.add(id)) {
            return;
        }
        GuideTopicFile file = loadDisplay(cl, id, locale, overlay, subTopicExtras);
        map.put(id, file);
        nav.add(new GuideNavEntry(id, depth, file.displayName()));
        for (String child : file.subTopicIds()) {
            walk(cl, locale, overlay, subTopicExtras, child, depth + 1, map, nav, completed);
        }
    }

    /** Navigation graph ids from the English canonical file only, plus pack patches. */
    @Nonnull
    private static List<String> canonicalSubTopicIds(
        @Nonnull ClassLoader cl,
        @Nonnull String id,
        @Nonnull GuideTopicPackOverlay overlay,
        @Nonnull Map<String, List<String>> subTopicExtras
    ) {
        List<String> base = loadRaw(cl, id, canonicalResourcePaths(id), overlay, DEFAULT_LOCALE).subTopicIds();
        return GuidePatchApplier.mergeSubTopics(base, subTopicExtras, id);
    }

    @Nonnull
    private static GuideTopicFile loadDisplay(
        @Nonnull ClassLoader cl,
        @Nonnull String id,
        @Nonnull String locale,
        @Nonnull GuideTopicPackOverlay overlay,
        @Nonnull Map<String, List<String>> subTopicExtras
    ) {
        GuideTopicFile localized = loadRaw(cl, id, displayResourcePaths(id, locale), overlay, locale);
        return new GuideTopicFile(
            id,
            localized.displayName(),
            localized.description(),
            localized.npcRoleId(),
            canonicalSubTopicIds(cl, id, overlay, subTopicExtras),
            localized.markdownBody()
        );
    }

    @Nonnull
    private static String[] displayResourcePaths(@Nonnull String id, @Nonnull String locale) {
        if (DEFAULT_LOCALE.equals(locale)) {
            return new String[] {
                PREFIX + DEFAULT_LOCALE + "/" + id + ".md",
                PREFIX + id + ".md",
            };
        }
        return new String[] {
            PREFIX + locale + "/" + id + ".md",
            PREFIX + DEFAULT_LOCALE + "/" + id + ".md",
            PREFIX + id + ".md",
        };
    }

    @Nonnull
    private static String[] canonicalResourcePaths(@Nonnull String id) {
        return new String[] {
            PREFIX + DEFAULT_LOCALE + "/" + id + ".md",
            PREFIX + id + ".md",
        };
    }

    /**
     * Prefer pack overlay for the display locale (then en-US overlay), then classpath wiki paths.
     */
    @Nonnull
    private static GuideTopicFile loadRaw(
        @Nonnull ClassLoader cl,
        @Nonnull String id,
        @Nonnull String[] paths,
        @Nonnull GuideTopicPackOverlay overlay,
        @Nonnull String preferredLocale
    ) {
        String packRaw = overlay.rawMarkdown(preferredLocale, id);
        if (packRaw == null && !DEFAULT_LOCALE.equals(preferredLocale)) {
            packRaw = overlay.rawMarkdown(DEFAULT_LOCALE, id);
        }
        if (packRaw != null) {
            return GuideTopicFile.parse(id, packRaw);
        }
        for (String path : paths) {
            try (InputStream in = cl.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return GuideTopicFile.parse(id, raw);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to read guide topic %s", path);
            }
        }
        LOGGER.atWarning().log("Missing guide topic resource for id %s (tried packs + %s)", id, String.join(", ", paths));
        return GuideTopicFile.missing(id);
    }

    @Nullable
    public GuideTopicFile byId(@Nonnull String id) {
        return byId.get(id.trim());
    }

    @Nonnull
    public List<GuideNavEntry> navEntries() {
        return navEntries;
    }

    public record GuideNavEntry(@Nonnull String topicId, int depth, @Nonnull String title) {}
}
