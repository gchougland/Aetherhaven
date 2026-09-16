package com.hexvane.aetherhaven.leveling;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class LevelingPolicyTest {
    @Test void completionRewardsUseFullLevelCostAndRespectDisableAndInvalidValues() {
        assertEquals(50, LevelingIntegration.completionXp(1000, 5));
        assertEquals(150, LevelingIntegration.completionXp(1000, 15));
        assertEquals(0, LevelingIntegration.completionXp(1000, 0));
        assertEquals(0, LevelingIntegration.completionXp(0, 15));
        assertEquals(0, LevelingIntegration.completionXp(Double.NaN, 15));
        assertEquals(0, LevelingIntegration.completionXp(1000, Double.POSITIVE_INFINITY));
        assertEquals(1000, LevelingIntegration.completionXp(1000, 200));
        assertEquals(1, LevelingIntegration.completionXp(2, 5));
    }
    @Test void completionRewardSettingsSurviveJournalCopy() {
        var defaults = new AetherhavenPluginConfig();
        assertEquals(5, defaults.getCompletionXpPercent(false));
        assertEquals(15, defaults.getCompletionXpPercent(true));
        var edited = new Gson().fromJson("{\"questCompletionXpPercent\":0,\"raidCompletionXpPercent\":25}", AetherhavenPluginConfig.class);
        defaults.copyStateFrom(edited);
        assertEquals(0, defaults.getCompletionXpPercent(false));
        assertEquals(25, defaults.getCompletionXpPercent(true));
    }
    @Test void raidKeepsItsOriginalLevelAcrossOwnerLevelUpsAndReloads() {
        var state = new NpcLevelState();
        assertEquals(42, state.target("ENDLESS_LEVELING", 42, true));
        state.appliedLevel = 42;
        assertEquals(42, state.target("ENDLESS_LEVELING", 85, true));
        var reloaded = state.clone();
        assertEquals(42, reloaded.target("ENDLESS_LEVELING", 0, true));
        assertEquals(0, reloaded.appliedLevel, "Native caches must be restored after loading");
        var jsonReload = new Gson().fromJson(new Gson().toJson(state), NpcLevelState.class);
        assertEquals(42, jsonReload.target("ENDLESS_LEVELING", 85, true));
        assertEquals(0, jsonReload.appliedLevel);
    }
    @Test void unknownOwnerDoesNotPermanentlyFreezeRaidAtLevelOne() {
        var state = new NpcLevelState();
        assertEquals(0, state.target("RPG_LEVELING", 0, true));
        assertEquals(31, state.target("RPG_LEVELING", 31, true));
    }
    @Test void existingRaidNeverTransfersNumericLevelToDifferentProgressionSystem() {
        var state = new NpcLevelState();
        state.target("ENDLESS_LEVELING", 450, true);
        assertEquals(0, state.target("RPG_LEVELING", 12, true));
        assertEquals(450, state.target("ENDLESS_LEVELING", 12, true));
    }
    @Test void residentsFollowOwnerLevelInBothDirections() {
        var state = new NpcLevelState();
        assertEquals(15, state.target("RPG_LEVELING", 15, false));
        assertEquals(40, state.target("RPG_LEVELING", 40, false));
        assertEquals(10, state.target("RPG_LEVELING", 10, false));
        assertEquals(0, state.raidLevel);
    }
    @Test void savedTownRetainsOfflineLevelButRejectsPreviousOwnerAndOtherProvider() {
        UUID owner = UUID.randomUUID();
        var town = new TownRecord();
        town.setOwner(owner, "Owner");
        town.setOwnerLevelSnapshot(new OwnerLevelSnapshot(owner, "RPG_LEVELING", 23));
        Gson gson = new Gson();
        var restored = gson.fromJson(gson.toJson(town), TownRecord.class);
        var snapshot = restored.getOwnerLevelSnapshot();
        assertEquals(23, snapshot.levelFor(restored.getOwnerUuid(), "RPG_LEVELING"));
        assertEquals(0, snapshot.levelFor(UUID.randomUUID(), "RPG_LEVELING"));
        assertEquals(0, snapshot.levelFor(owner, "ENDLESS_LEVELING"));
        assertEquals(0, snapshot.levelFor(null, "RPG_LEVELING"));
    }
    @Test void healthUpdatesPreserveInjuriesAndNeverResurrectDeadNpcs() {
        assertEquals(.25f, LevelingIntegration.healthFraction(25, 100));
        assertEquals(250, LevelingIntegration.healthFraction(25, 100) * 1000);
        assertEquals(0, LevelingIntegration.healthFraction(0, 100));
        assertEquals(0, LevelingIntegration.healthFraction(-5, 100));
        assertEquals(0, LevelingIntegration.healthFraction(25, 0));
        assertEquals(0, LevelingIntegration.healthFraction(Float.NaN, 100));
        assertEquals(1, LevelingIntegration.healthFraction(105, 100));
    }
    @Test void integrationConfigDefaultsToAutoAndSurvivesJournalConfigCopy() {
        assertEquals("AUTO", new AetherhavenPluginConfig().getLevelingIntegration());
        var rpg = new Gson().fromJson("{\"levelingIntegration\":\"rpg_leveling\"}", AetherhavenPluginConfig.class);
        var copy = new AetherhavenPluginConfig();
        copy.copyStateFrom(rpg);
        assertEquals("RPG_LEVELING", copy.getLevelingIntegration());
        assertEquals("NONE", new Gson().fromJson("{\"levelingIntegration\":\"typo\"}", AetherhavenPluginConfig.class).getLevelingIntegration());
    }
}
