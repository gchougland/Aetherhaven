package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.autonomy.VillagerWorkActivity;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Selectable work/fun POI animation options for the plot creator activity picker. */
public final class PlotCreatorWorkActivityOptions {
    private static final List<String> SELECTABLE =
        List.of("mine", "chop", "water", "till", "smith", "craft", "read", "leisure");

    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator.poiActivity";

    private PlotCreatorWorkActivityOptions() {}

    @Nonnull
    public static List<String> allSelectable() {
        return SELECTABLE;
    }

    @Nonnull
    public static String langKeyFor(@Nonnull String activityId) {
        return MSG + ".option." + activityId.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static String equipmentProfileFor(@Nonnull String activityId) {
        return switch (activityId.trim().toLowerCase(Locale.ROOT)) {
            case "mine" -> "work_miner";
            case "chop" -> "work_logger";
            case "water" -> "work_farmer";
            case "till" -> "work_farmer_hoe";
            case "smith" -> "work_blacksmith";
            case "craft" -> "work_builder";
            case "leisure" -> "work_bard";
            default -> null;
        };
    }

    public static void applyToPoi(@Nonnull PlotCreatorPoiDraft poi, @Nonnull String activityId) {
        String id = activityId.trim().toLowerCase(Locale.ROOT);
        String tag = VillagerWorkActivity.TAG_PREFIX + id;
        poi.getTags()
            .removeIf(
                t ->
                    t != null
                        && t.regionMatches(
                            true,
                            0,
                            VillagerWorkActivity.TAG_PREFIX,
                            0,
                            VillagerWorkActivity.TAG_PREFIX.length()
                        )
            );
        poi.getTags().add(tag);
        poi.setEquipmentProfileId(equipmentProfileFor(id));
    }

    /** Maps debug POI marker work action names to activity ids. */
    @Nullable
    public static String activityIdForWorkAction(@Nullable String workAction) {
        if (workAction == null || workAction.isBlank()) {
            return null;
        }
        return switch (workAction.trim()) {
            case "Mine" -> "mine";
            case "Chop" -> "chop";
            case "Water" -> "water";
            case "Till" -> "till";
            case "Smith" -> "smith";
            case "Craft" -> "craft";
            case "Read" -> "read";
            case "Leisure" -> "leisure";
            default -> null;
        };
    }
}
