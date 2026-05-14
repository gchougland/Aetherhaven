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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Loads Voile-style markdown topics from {@code Common/Docs/Hexvane_AetherhavenWiki/} and walks {@code welcome.md}
 * sub-topics for stable navigation order.
 */
public final class GuideTopicRepository {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PREFIX = "Common/Docs/Hexvane_AetherhavenWiki/";

    private static volatile GuideTopicRepository cached;

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
        GuideTopicRepository c = cached;
        if (c == null) {
            synchronized (GuideTopicRepository.class) {
                c = cached;
                if (c == null) {
                    c = load(classLoader);
                    cached = c;
                }
            }
        }
        return c;
    }

    /** Test hook. */
    public static void clearCache() {
        synchronized (GuideTopicRepository.class) {
            cached = null;
        }
    }

    @Nonnull
    private static GuideTopicRepository load(@Nonnull ClassLoader cl) {
        Map<String, GuideTopicFile> map = new LinkedHashMap<>();
        List<GuideNavEntry> nav = new ArrayList<>();
        Set<String> completed = new LinkedHashSet<>();
        walk(cl, "welcome", 0, map, nav, completed);
        return new GuideTopicRepository(map, nav);
    }

    private static void walk(
        @Nonnull ClassLoader cl,
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
        GuideTopicFile file = loadOne(cl, id);
        map.put(id, file);
        nav.add(new GuideNavEntry(id, depth, file.displayName()));
        for (String child : file.subTopicIds()) {
            walk(cl, child, depth + 1, map, nav, completed);
        }
    }

    @Nonnull
    private static GuideTopicFile loadOne(@Nonnull ClassLoader cl, @Nonnull String id) {
        String path = PREFIX + id + ".md";
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.atWarning().log("Missing guide topic resource %s", path);
                return GuideTopicFile.missing(id);
            }
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GuideTopicFile.parse(id, raw);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to read guide topic %s", path);
            return GuideTopicFile.missing(id);
        }
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
