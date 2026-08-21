package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.plot.PlotBuildingStyles;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Curated town look options shown after founding (and via {@code /aetherhaven town style}). */
public final class TownBuildingStyleShowcase {
    public static final String DEFAULT_STYLE_ID = "core";

    private static final List<Entry> ENTRIES =
        List.of(
            new Entry(
                "core",
                false,
                "UI/Custom/Aetherhaven/StyleShowcases/Core.png",
                "aetherhaven_town_style.aetherhaven.townStyle.style.core.name",
                "aetherhaven_town_style.aetherhaven.townStyle.style.core.desc"
            ),
            new Entry(
                "stormwind",
                false,
                "UI/Custom/Aetherhaven/StyleShowcases/Stormwind.png",
                "aetherhaven_town_style.aetherhaven.townStyle.style.stormwind.name",
                "aetherhaven_town_style.aetherhaven.townStyle.style.stormwind.desc"
            ),
            new Entry(
                "jimmys village",
                true,
                "UI/Custom/Aetherhaven/StyleShowcases/JimmyVillage.png",
                "aetherhaven_town_style.aetherhaven.townStyle.style.jimmysVillage.name",
                "aetherhaven_town_style.aetherhaven.townStyle.style.jimmysVillage.desc"
            ),
            new Entry(
                "fairy tale",
                true,
                "UI/Custom/Aetherhaven/StyleShowcases/Fairytale.png",
                "aetherhaven_town_style.aetherhaven.townStyle.style.fairyTale.name",
                "aetherhaven_town_style.aetherhaven.townStyle.style.fairyTale.desc"
            ),
            new Entry(
                "coastal ruins",
                true,
                "UI/Custom/Aetherhaven/StyleShowcases/CoastalRuins.png",
                "aetherhaven_town_style.aetherhaven.townStyle.style.coastalRuins.name",
                "aetherhaven_town_style.aetherhaven.townStyle.style.coastalRuins.desc"
            ),
            new Entry(
                "slate fjord",
                true,
                "UI/Custom/Aetherhaven/StyleShowcases/SlateFjord.png",
                "aetherhaven_town_style.aetherhaven.townStyle.style.slateFjord.name",
                "aetherhaven_town_style.aetherhaven.townStyle.style.slateFjord.desc"
            )
        );

    private TownBuildingStyleShowcase() {}

    @Nonnull
    public static List<Entry> entries() {
        return ENTRIES;
    }

    @Nullable
    public static Entry findByStyleId(@Nullable String styleId) {
        String normalized = PlotBuildingStyles.normalize(styleId);
        if (normalized == null) {
            return null;
        }
        for (Entry entry : ENTRIES) {
            if (normalized.equals(entry.styleId())) {
                return entry;
            }
        }
        return null;
    }

    @Nonnull
    public static String effectiveStyleId(@Nullable String stored) {
        String normalized = PlotBuildingStyles.normalize(stored);
        return normalized != null ? normalized : DEFAULT_STYLE_ID;
    }

    public record Entry(
        @Nonnull String styleId,
        boolean marketplace,
        @Nonnull String imageAssetPath,
        @Nonnull String nameLangKey,
        @Nonnull String descLangKey
    ) {}
}
