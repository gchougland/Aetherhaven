package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Rows shown on the plot creator festival step: one "new festival" row plus every festival already saved. */
public final class PlotCreatorFestivalChoices {
    /**
     * @param festivalId existing festival to edit, or null for the new festival row
     * @param labelLang optional message id; when null the raw {@code fallbackLabel} is shown
     */
    public record Choice(@Nullable String festivalId, @Nullable String labelLang, @Nonnull String fallbackLabel) {}

    private static final String NEW_FESTIVAL_LANG =
        "aetherhaven_plot_creator.aetherhaven.plotcreator.festival.newFestival";

    private PlotCreatorFestivalChoices() {}

    @Nonnull
    public static List<Choice> list(@Nullable AetherhavenPlugin plugin) {
        List<Choice> out = new ArrayList<>();
        out.add(new Choice(null, NEW_FESTIVAL_LANG, "New festival"));
        if (plugin == null) {
            return out;
        }
        List<FestivalDefinition> defs = new ArrayList<>(plugin.getFestivalCatalog().list());
        defs.sort(Comparator.comparing(d -> d.getDisplayName().toLowerCase(Locale.ROOT)));
        for (FestivalDefinition def : defs) {
            out.add(new Choice(def.getId(), def.getDisplayNameLangKey(), def.getDisplayName()));
        }
        return out;
    }
}
