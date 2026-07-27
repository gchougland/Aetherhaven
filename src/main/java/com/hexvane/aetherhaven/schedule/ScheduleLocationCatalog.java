package com.hexvane.aetherhaven.schedule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered custom schedule location symbols from {@link AetherhavenAssetPaths#SCHEDULE_LOCATIONS}. Later packs
 * override the same symbol.
 */
public final class ScheduleLocationCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String JOURNAL_LANG = "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.";
    private static final String GUIDE_LANG = "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.";

    private static final Set<String> RESERVED =
        Set.of(
            VillagerScheduleResolver.LOC_HOME,
            VillagerScheduleResolver.LOC_WORK,
            VillagerScheduleResolver.LOC_INN,
            VillagerScheduleResolver.LOC_PARK,
            VillagerScheduleResolver.LOC_GAIA_ALTAR,
            VillagerScheduleResolver.LOC_SHOP
        );

    private final Map<String, ScheduleLocationDefinition> bySymbol;

    private ScheduleLocationCatalog(@Nonnull Map<String, ScheduleLocationDefinition> bySymbol) {
        this.bySymbol = bySymbol;
    }

    @Nonnull
    public static ScheduleLocationCatalog empty() {
        return new ScheduleLocationCatalog(Collections.emptyMap());
    }

    @Nonnull
    public static ScheduleLocationCatalog forTests(@Nonnull Map<String, ScheduleLocationDefinition> bySymbol) {
        return new ScheduleLocationCatalog(Collections.unmodifiableMap(new LinkedHashMap<>(bySymbol)));
    }

    @Nonnull
    public static ScheduleLocationCatalog loadFromAssetPacks() {
        Gson gson = new GsonBuilder().create();
        Map<String, ScheduleLocationDefinition> map = new LinkedHashMap<>();
        List<PackJsonFile> packFiles =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.SCHEDULE_LOCATIONS);
        for (PackJsonFile f : packFiles) {
            String symbol = symbolFromFileName(f.absolutePath().getFileName().toString());
            if (symbol.isEmpty()) {
                continue;
            }
            if (RESERVED.contains(symbol)) {
                LOGGER.atWarning().log(
                    "Skipping schedule location %s: symbol is reserved (%s)",
                    f.absolutePath(),
                    symbol
                );
                continue;
            }
            try (InputStream in = Files.newInputStream(f.absolutePath())) {
                ScheduleLocationDefinition def =
                    gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ScheduleLocationDefinition.class);
                if (def == null || def.getConstructionId() == null || def.getConstructionId().isBlank()) {
                    LOGGER.atWarning().log("Schedule location %s missing constructionId (%s)", symbol, f.absolutePath());
                    continue;
                }
                if (map.containsKey(symbol)) {
                    LOGGER.atInfo().log("Schedule location %s overridden by later asset (%s)", symbol, f.packName());
                }
                map.put(symbol, def);
                LOGGER.atInfo().log("Loaded schedule location: %s -> %s (%s)", symbol, def.getConstructionId(), f.packName());
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load schedule location %s", f.absolutePath());
            }
        }
        if (!map.isEmpty()) {
            LOGGER.atInfo().log(
                "Loaded %s schedule location(s) from asset packs under %s",
                map.size(),
                AetherhavenAssetPaths.SCHEDULE_LOCATIONS
            );
        }
        return new ScheduleLocationCatalog(Collections.unmodifiableMap(map));
    }

    public boolean isKnownSymbol(@Nonnull String symbol) {
        String s = normalizeSymbol(symbol);
        if (s == null) {
            return false;
        }
        return RESERVED.contains(s) || bySymbol.containsKey(s);
    }

    @Nullable
    public String constructionIdForSymbol(@Nonnull String symbol) {
        String s = normalizeSymbol(symbol);
        if (s == null) {
            return null;
        }
        ScheduleLocationDefinition def = bySymbol.get(s);
        if (def == null) {
            return null;
        }
        String c = def.getConstructionId();
        return c != null && !c.isBlank() ? c.trim() : null;
    }

    @Nonnull
    public Message journalDisplayMessage(@Nonnull String symbol) {
        String s = normalizeSymbol(symbol);
        if (s == null) {
            return Message.translation(JOURNAL_LANG + "scheduleUnknown");
        }
        Message builtIn = builtInJournalMessage(s);
        if (builtIn != null) {
            return builtIn;
        }
        ScheduleLocationDefinition def = bySymbol.get(s);
        if (def != null) {
            String key = def.getDisplayNameLangKey();
            if (key != null && !key.isBlank()) {
                return Message.translation(key.trim());
            }
        }
        return Message.raw(s);
    }

    @Nonnull
    public Message guideShortLabelMessage(@Nonnull String symbol) {
        String s = normalizeSymbol(symbol);
        if (s == null) {
            return Message.translation(GUIDE_LANG + "scheduleLocUnknown");
        }
        Message builtIn = builtInGuideShortMessage(s);
        if (builtIn != null) {
            return builtIn;
        }
        ScheduleLocationDefinition def = bySymbol.get(s);
        if (def != null) {
            String key = def.getDisplayNameLangKey();
            if (key != null && !key.isBlank()) {
                return Message.translation(key.trim());
            }
        }
        return Message.raw(s.length() > 5 ? s.substring(0, 5) : s);
    }

    @Nonnull
    public Message guideFriendlyLocationMessage(@Nonnull String symbol) {
        String s = normalizeSymbol(symbol);
        if (s == null) {
            return Message.translation(GUIDE_LANG + "scheduleLocUnknown");
        }
        Message builtIn = builtInGuideFriendlyMessage(s);
        if (builtIn != null) {
            return builtIn;
        }
        ScheduleLocationDefinition def = bySymbol.get(s);
        if (def != null) {
            String key = def.getDisplayNameLangKey();
            if (key != null && !key.isBlank()) {
                return Message.translation(key.trim());
            }
        }
        return Message.raw(s);
    }

    @Nullable
    private static Message builtInJournalMessage(@Nonnull String s) {
        return switch (s) {
            case VillagerScheduleResolver.LOC_HOME -> Message.translation(JOURNAL_LANG + "scheduleHome");
            case VillagerScheduleResolver.LOC_WORK -> Message.translation(JOURNAL_LANG + "scheduleWork");
            case VillagerScheduleResolver.LOC_INN -> Message.translation(JOURNAL_LANG + "scheduleInn");
            case VillagerScheduleResolver.LOC_PARK -> Message.translation(JOURNAL_LANG + "schedulePark");
            case VillagerScheduleResolver.LOC_GAIA_ALTAR -> Message.translation(JOURNAL_LANG + "scheduleAltar");
            case VillagerScheduleResolver.LOC_SHOP -> Message.translation(JOURNAL_LANG + "scheduleShop");
            default -> null;
        };
    }

    @Nullable
    private static Message builtInGuideShortMessage(@Nonnull String s) {
        return switch (s) {
            case "home" -> Message.translation(GUIDE_LANG + "scheduleLocShortHome");
            case "work" -> Message.translation(GUIDE_LANG + "scheduleLocShortWork");
            case "inn" -> Message.translation(GUIDE_LANG + "scheduleLocShortInn");
            case "park" -> Message.translation(GUIDE_LANG + "scheduleLocShortPark");
            case "gaia_altar" -> Message.translation(GUIDE_LANG + "scheduleLocShortAltar");
            case VillagerScheduleResolver.LOC_SHOP -> Message.translation(GUIDE_LANG + "scheduleLocShortShop");
            default -> null;
        };
    }

    @Nullable
    private static Message builtInGuideFriendlyMessage(@Nonnull String s) {
        return switch (s) {
            case "home" -> Message.translation(GUIDE_LANG + "scheduleLocHome");
            case "work" -> Message.translation(GUIDE_LANG + "scheduleLocWork");
            case "inn" -> Message.translation(GUIDE_LANG + "scheduleLocInn");
            case "park" -> Message.translation(GUIDE_LANG + "scheduleLocPark");
            case "gaia_altar" -> Message.translation(GUIDE_LANG + "scheduleLocAltar");
            case VillagerScheduleResolver.LOC_SHOP -> Message.translation(GUIDE_LANG + "scheduleLocShop");
            default -> null;
        };
    }

    @Nullable
    private static String normalizeSymbol(@Nullable String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return symbol.trim().toLowerCase();
    }

    @Nonnull
    private static String symbolFromFileName(@Nonnull String fileName) {
        if (!fileName.endsWith(".json")) {
            return "";
        }
        return fileName.substring(0, fileName.length() - 5).trim().toLowerCase();
    }
}
