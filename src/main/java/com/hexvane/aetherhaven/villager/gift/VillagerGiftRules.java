package com.hexvane.aetherhaven.villager.gift;

import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagerGiftRules {
    private VillagerGiftRules() {}

    @Nonnull
    public static GiftPreference classifyItem(@Nonnull String itemId, @Nullable VillagerDefinition def) {
        String id = itemId.trim();
        if (id.isEmpty() || def == null) {
            return GiftPreference.NEUTRAL;
        }
        if (containsId(def.getGiftLoves(), id)) {
            return GiftPreference.LOVE;
        }
        if (containsId(def.getGiftLikes(), id)) {
            return GiftPreference.LIKE;
        }
        if (containsId(def.getGiftDislikes(), id)) {
            return GiftPreference.DISLIKE;
        }
        return GiftPreference.NEUTRAL;
    }

    public static int reputationDelta(@Nonnull GiftPreference p) {
        return switch (p) {
            case LOVE -> 5;
            case LIKE -> 3;
            case NEUTRAL -> 1;
            case DISLIKE -> -2;
        };
    }

    private static boolean containsId(@Nonnull List<String> ids, @Nonnull String want) {
        for (String s : ids) {
            if (s != null && want.equals(s.trim())) {
                return true;
            }
        }
        return false;
    }
}
