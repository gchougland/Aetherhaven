package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.hypixel.hytale.logger.HytaleLogger;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Loads {@code prefab_material_conversions.txt} (exact ids + glob patterns). */
public final class PrefabMaterialConversionTable {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE_PATH = "Server/Aetherhaven/prefab_material_conversions.txt";

    private final Map<String, ConversionRule> exact;
    private final List<PatternRule> patterns;

    private PrefabMaterialConversionTable(
        @Nonnull Map<String, ConversionRule> exact,
        @Nonnull List<PatternRule> patterns
    ) {
        this.exact = exact;
        this.patterns = patterns;
    }

    @Nonnull
    public static PrefabMaterialConversionTable loadFromClasspath(@Nonnull ClassLoader classLoader) {
        Map<String, ConversionRule> exact = new HashMap<>();
        exact.put("Empty", ConversionRule.skip());
        List<PatternRule> patterns = new ArrayList<>();
        try (InputStream in = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.atWarning().log("Prefab material conversions resource missing: %s", RESOURCE_PATH);
                return new PrefabMaterialConversionTable(exact, patterns);
            }
            parseLines(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), exact, patterns);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load prefab material conversions");
        }
        return new PrefabMaterialConversionTable(exact, patterns);
    }

    static void parseLines(
        @Nonnull BufferedReader reader,
        @Nonnull Map<String, ConversionRule> exact,
        @Nonnull List<PatternRule> patterns
    ) throws java.io.IOException {
        String line;
        int lineNo = 0;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                LOGGER.atWarning().log("Prefab conversions line %s: missing '=' — %s", lineNo, line);
                continue;
            }
            String key = line.substring(0, eq).strip();
            if (key.isEmpty()) {
                continue;
            }
            try {
                ConversionRule rule = ConversionRule.parse(line.substring(eq + 1));
                if (key.toLowerCase(Locale.ROOT).startsWith("pattern:")) {
                    String pat = key.substring("pattern:".length()).strip();
                    if (!pat.isEmpty()) {
                        patterns.add(new PatternRule(java.util.regex.Pattern.compile(PatternRule.globToRegex(pat)), rule));
                    }
                } else {
                    exact.put(key, rule);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.atSevere().withCause(e).log("Prefab conversions line %s", lineNo);
                throw e;
            }
        }
    }

    @Nullable
    public ConversionRule lookup(@Nonnull String itemId) {
        ConversionRule rule = exact.get(itemId);
        if (rule != null) {
            return rule;
        }
        for (PatternRule pr : patterns) {
            if (pr.matches(itemId)) {
                return pr.rule;
            }
        }
        return null;
    }

    private record PatternRule(@Nonnull java.util.regex.Pattern pattern, @Nonnull ConversionRule rule) {
        static String globToRegex(@Nonnull String glob) {
            StringBuilder sb = new StringBuilder("^");
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                switch (c) {
                    case '*' -> sb.append(".*");
                    case '?' -> sb.append('.');
                    case '.' -> sb.append("\\.");
                    case '\\' -> sb.append("\\\\");
                    default -> sb.append(c);
                }
            }
            sb.append('$');
            return sb.toString();
        }

        boolean matches(@Nonnull String itemId) {
            return pattern.matcher(itemId).matches();
        }
    }
}
