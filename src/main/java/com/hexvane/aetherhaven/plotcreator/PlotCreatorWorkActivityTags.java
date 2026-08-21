package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.autonomy.VillagerWorkActivity;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Stamps {@code workActivity:*} tags on plot creator POIs from building / resident role. */
public final class PlotCreatorWorkActivityTags {
    private PlotCreatorWorkActivityTags() {}

    public static void applyDefault(
        @Nonnull PlotCreatorPoiDraft poi,
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind
    ) {
        String activity = resolveActivityId(type, workResidentKind, poi.getTags());
        if (activity == null) {
            return;
        }
        String tag = VillagerWorkActivity.TAG_PREFIX + activity;
        List<String> tags = poi.getTags();
        tags.removeIf(t -> t != null && t.regionMatches(true, 0, VillagerWorkActivity.TAG_PREFIX, 0, VillagerWorkActivity.TAG_PREFIX.length()));
        tags.add(tag);
    }

    @Nullable
    public static String resolveActivityId(
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind,
        @Nonnull List<String> existingTags
    ) {
        for (String t : existingTags) {
            if (t != null && t.regionMatches(true, 0, VillagerWorkActivity.TAG_PREFIX, 0, VillagerWorkActivity.TAG_PREFIX.length())) {
                return t.substring(VillagerWorkActivity.TAG_PREFIX.length()).trim().toLowerCase(Locale.ROOT);
            }
        }
        if (type == PlotCreatorSubstepType.FUN_POI) {
            return "read";
        }
        if (type == PlotCreatorSubstepType.BARD_WORK_POI) {
            return "leisure";
        }
        if (type == PlotCreatorSubstepType.PLANNING_DESK_POI) {
            return "read";
        }
        if (type != PlotCreatorSubstepType.WORK_POI) {
            return null;
        }
        String kind = workResidentKind != null ? workResidentKind.trim().toLowerCase(Locale.ROOT) : "";
        if (kind.startsWith("visitor_")) {
            kind = kind.substring("visitor_".length());
        }
        return switch (kind) {
            case TownVillagerBinding.KIND_MINER -> "mine";
            case TownVillagerBinding.KIND_LOGGER -> "chop";
            case TownVillagerBinding.KIND_FARMER -> "water";
            case TownVillagerBinding.KIND_BLACKSMITH -> "smith";
            case TownVillagerBinding.KIND_RANCHER, TownVillagerBinding.KIND_BUILDER -> "craft";
            case TownVillagerBinding.KIND_BARD, TownVillagerBinding.KIND_CLOWN -> "leisure";
            case TownVillagerBinding.KIND_INNKEEPER,
                TownVillagerBinding.KIND_GUILD_MASTER,
                TownVillagerBinding.KIND_MERCHANT,
                TownVillagerBinding.KIND_CHEF,
                TownVillagerBinding.KIND_FLORIST,
                TownVillagerBinding.KIND_FURNITURE_MERCHANT,
                TownVillagerBinding.KIND_PYROTECHNIC,
                TownVillagerBinding.KIND_CRYSTAL_KEEPER,
                TownVillagerBinding.KIND_ELDER,
                TownVillagerBinding.KIND_PRIESTESS -> "read";
            default -> "leisure";
        };
    }

    @Nullable
    public static String activityLabel(@Nullable String activityId) {
        if (activityId == null || activityId.isBlank()) {
            return null;
        }
        return switch (activityId.trim().toLowerCase(Locale.ROOT)) {
            case "mine" -> "Mining spot";
            case "chop" -> "Chopping spot";
            case "water" -> "Watering spot";
            case "till" -> "Tilling spot";
            case "smith", "forge", "anvil" -> "Smithing spot";
            case "craft", "build" -> "Crafting spot";
            case "read" -> "Reading spot";
            case "leisure", "fun" -> "Fun spot";
            default -> null;
        };
    }
}
