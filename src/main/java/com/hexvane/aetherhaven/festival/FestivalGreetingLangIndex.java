package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackAssetFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Discovered festival greeting lines from lang files. Crossmod packs add keys like
 * {@code aetherhaven.dialogue.festival.wintertide.greeting.mechanic.0} under any
 * {@code Server/Languages/en-US/*.lang} bundle without editing festival JSON.
 */
public final class FestivalGreetingLangIndex {
    private static final String LANG_DIRECTORY = "Server/Languages/en-US";
    private static final Pattern GREETING_KEY =
        Pattern.compile("\\.dialogue\\.festival\\.([^.]+)\\.greeting\\.([^.]+)\\.(\\d+)$");

    private final Map<String, Map<String, List<String>>> byFestivalAndKind;

    private FestivalGreetingLangIndex(@Nonnull Map<String, Map<String, List<String>>> byFestivalAndKind) {
        this.byFestivalAndKind = byFestivalAndKind;
    }

    @Nonnull
    public static FestivalGreetingLangIndex empty() {
        return new FestivalGreetingLangIndex(Map.of());
    }

    @Nonnull
    public static FestivalGreetingLangIndex load(@Nonnull ClassLoader classLoader) {
        Builder builder = new Builder();
        for (PackAssetFile file :
            AetherhavenPackAssetScanner.listFilesUnderAllPacks(LANG_DIRECTORY, ".lang")) {
            parseLangFile(builder, file.absolutePath());
        }
        for (String resourcePath : listClasspathLangFiles(classLoader)) {
            try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
                if (in != null) {
                    parseLangStream(builder, bundleNameFromResourcePath(resourcePath), in);
                }
            } catch (IOException e) {
                // Skip unreadable classpath lang files during index build.
            }
        }
        return builder.build();
    }

    /** Lang keys for one villager kind on a festival, without falling back to {@code default}. */
    @Nonnull
    public List<String> keysForKindOnly(@Nonnull String festivalId, @Nullable String kind) {
        Map<String, List<String>> kinds = byFestivalAndKind.get(normalizeFestivalId(festivalId));
        if (kinds == null) {
            return List.of();
        }
        List<String> keys = kinds.get(normalizeKind(kind));
        return keys != null ? keys : List.of();
    }

    /** Lang keys for the festival {@code default} greeting bucket only. */
    @Nonnull
    public List<String> keysForDefault(@Nonnull String festivalId) {
        return keysForKindOnly(festivalId, FestivalDefinition.GREETING_DEFAULT_KIND);
    }

    @Nonnull
    private static List<String> listClasspathLangFiles(@Nonnull ClassLoader classLoader) {
        return ClasspathResourceScanner.listFiles(classLoader, LANG_DIRECTORY, ".lang");
    }

    private static void parseLangFile(@Nonnull Builder builder, @Nonnull Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".lang")) {
            return;
        }
        String bundle = fileName.substring(0, fileName.length() - ".lang".length());
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            parseLangReader(builder, bundle, reader);
        } catch (IOException e) {
            // Skip unreadable pack lang files during index build.
        }
    }

    /** Visible for tests. */
    static void parseLangContent(
        @Nonnull Builder builder,
        @Nonnull String bundle,
        @Nonnull String content
    ) throws IOException {
        parseLangStream(builder, bundle, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static void parseLangStream(
        @Nonnull Builder builder,
        @Nonnull String bundle,
        @Nonnull InputStream in
    ) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            parseLangReader(builder, bundle, reader);
        }
    }

    private static void parseLangReader(
        @Nonnull Builder builder,
        @Nonnull String bundle,
        @Nonnull BufferedReader reader
    ) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String inFileKey = trimmed.substring(0, eq).trim();
            if (inFileKey.isEmpty()) {
                continue;
            }
            Matcher matcher = GREETING_KEY.matcher(inFileKey);
            if (!matcher.find()) {
                continue;
            }
            String festivalId = matcher.group(1).trim().toLowerCase(Locale.ROOT);
            String kind = matcher.group(2).trim().toLowerCase(Locale.ROOT);
            int index = Integer.parseInt(matcher.group(3));
            String fullKey = bundle + "." + inFileKey;
            builder.add(festivalId, kind, index, fullKey);
        }
    }

    @Nonnull
    private static String bundleNameFromResourcePath(@Nonnull String resourcePath) {
        String name = resourcePath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".lang")) {
            return name.substring(0, name.length() - ".lang".length());
        }
        return name;
    }

    @Nonnull
    private static String normalizeFestivalId(@Nonnull String festivalId) {
        return festivalId.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    private static String normalizeKind(@Nullable String kind) {
        return kind != null ? kind.trim().toLowerCase(Locale.ROOT) : "";
    }

    /** Visible for tests. */
    @Nonnull
    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, Map<String, Map<Integer, String>>> staging = new LinkedHashMap<>();

        void add(@Nonnull String festivalId, @Nonnull String kind, int index, @Nonnull String fullKey) {
            if (festivalId.isEmpty() || kind.isEmpty() || fullKey.isBlank()) {
                return;
            }
            staging
                .computeIfAbsent(festivalId, k -> new LinkedHashMap<>())
                .computeIfAbsent(kind, k -> new LinkedHashMap<>())
                .put(index, fullKey.trim());
        }

        @Nonnull
        FestivalGreetingLangIndex build() {
            Map<String, Map<String, List<String>>> out = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Map<Integer, String>>> festivalEntry : staging.entrySet()) {
                Map<String, List<String>> kinds = new LinkedHashMap<>();
                for (Map.Entry<String, Map<Integer, String>> kindEntry : festivalEntry.getValue().entrySet()) {
                    List<Integer> indices = new ArrayList<>(kindEntry.getValue().keySet());
                    indices.sort(Integer::compareTo);
                    List<String> keys = new ArrayList<>();
                    Set<String> seen = new LinkedHashSet<>();
                    for (int index : indices) {
                        String key = kindEntry.getValue().get(index);
                        if (key != null && seen.add(key)) {
                            keys.add(key);
                        }
                    }
                    if (!keys.isEmpty()) {
                        kinds.put(kindEntry.getKey(), List.copyOf(keys));
                    }
                }
                if (!kinds.isEmpty()) {
                    out.put(festivalEntry.getKey(), Map.copyOf(kinds));
                }
            }
            return new FestivalGreetingLangIndex(Collections.unmodifiableMap(out));
        }
    }
}
