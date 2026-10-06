package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog.ReputationRewardDefinition;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hexvane.aetherhaven.town.VillagerGiftLogEntry;
import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import java.util.*;

/** Remove undiscovered content before it reaches UI labels, item icons or tooltips. */
final class CoreVillagerProfileView {
    private CoreVillagerProfileView() {}
    record Reward(int reputation, boolean unlocked, boolean claimed, String item, int count, boolean recipe) {}
    record Gift(String item, GiftPreference preference, long day) {}
    static Reward reward(ReputationRewardDefinition definition, VillagerReputationEntry entry) {
        boolean claimed = entry != null && entry.getClaimedRewardIds().contains(definition.rewardId());
        boolean unlocked = entry != null && (claimed || entry.getReputation() >= definition.minReputation()
            || entry.getPendingRewardIds().contains(definition.rewardId()));
        if (!unlocked) return new Reward(definition.minReputation(), false, false, "", 0, false);
        boolean recipe = definition.learnRecipeItemId() != null && !definition.learnRecipeItemId().isBlank();
        return new Reward(definition.minReputation(), true, claimed,
            recipe ? definition.learnRecipeItemId() : definition.itemId(), recipe ? 1 : definition.itemCount(), recipe);
    }
    static List<Gift> discoveredGifts(List<VillagerGiftLogEntry> history, UUID viewer) {
        if (viewer == null) return List.of();
        var latest = new HashMap<String, Gift>();
        for (var entry : history) {
            if (entry == null || !viewer.toString().equals(entry.getGiverPlayerUuid()) || entry.getItemId().isBlank()) continue;
            String item = entry.getItemId().trim();
            var before = latest.get(item);
            if (before == null || entry.getGameEpochDay() >= before.day())
                latest.put(item, new Gift(item, entry.getTier(), entry.getGameEpochDay()));
        }
        return latest.values().stream().sorted(Comparator.comparing(Gift::preference)
            .thenComparing(Comparator.comparingLong(Gift::day).reversed()).thenComparing(Gift::item)).limit(200).toList();
    }
}
