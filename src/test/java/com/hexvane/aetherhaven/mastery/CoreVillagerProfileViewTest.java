package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog.ReputationRewardDefinition;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hexvane.aetherhaven.town.VillagerGiftLogEntry;
import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class CoreVillagerProfileViewTest {
    private static final ReputationRewardDefinition RECIPE = new ReputationRewardDefinition(
        "secret_recipe_reward", "Aetherhaven_Druid", 50, "Secret_Gift_Item", 3, "secret_dialogue", "Secret_Recipe_Item");

    @Test void lockedRewardDoesNotExposeItsItemRecipeQuantityOrClaimIdentity() {
        var entry = new VillagerReputationEntry(); entry.setReputation(49);
        var view = CoreVillagerProfileView.reward(RECIPE, entry);
        assertEquals(new CoreVillagerProfileView.Reward(50, false, false, "", 0, false), view);
        assertFalse(view.toString().contains("Secret"));
        assertEquals(view, CoreVillagerProfileView.reward(RECIPE, null));
    }

    @Test void reachingThresholdRevealsRecipeAndPreviouslyUnlockedRewardsRemainKnown() {
        var entry = new VillagerReputationEntry(); entry.setReputation(50);
        var view = CoreVillagerProfileView.reward(RECIPE, entry);
        assertTrue(view.unlocked()); assertTrue(view.recipe());
        assertEquals("Secret_Recipe_Item", view.item()); assertFalse(view.claimed());
        entry.setReputation(20); entry.getPendingRewardIds().add(RECIPE.rewardId());
        assertTrue(CoreVillagerProfileView.reward(RECIPE, entry).unlocked());
        entry.getPendingRewardIds().clear(); entry.getClaimedRewardIds().add(RECIPE.rewardId());
        assertTrue(CoreVillagerProfileView.reward(RECIPE, entry).claimed());
    }

    @Test void giftsRevealOnlyTheViewersActualDiscoveriesWithLatestReaction() {
        UUID viewer = UUID.randomUUID(), other = UUID.randomUUID();
        var history = List.of(
            new VillagerGiftLogEntry("Secret_Other_Gift", GiftPreference.LOVE, other.toString(), 9),
            new VillagerGiftLogEntry("Known_Gift", GiftPreference.LIKE, viewer.toString(), 8),
            new VillagerGiftLogEntry("Known_Gift", GiftPreference.LOVE, viewer.toString(), 9),
            new VillagerGiftLogEntry("Same_Day", GiftPreference.LIKE, viewer.toString(), 9),
            new VillagerGiftLogEntry("Same_Day", GiftPreference.NEUTRAL, viewer.toString(), 9));
        var gifts = CoreVillagerProfileView.discoveredGifts(history, viewer);
        assertEquals(List.of(new CoreVillagerProfileView.Gift("Known_Gift", GiftPreference.LOVE, 9),
            new CoreVillagerProfileView.Gift("Same_Day", GiftPreference.NEUTRAL, 9)), gifts);
        assertTrue(CoreVillagerProfileView.discoveredGifts(history, UUID.randomUUID()).isEmpty());
    }
}
