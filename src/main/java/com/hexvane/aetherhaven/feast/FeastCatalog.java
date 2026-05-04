package com.hexvane.aetherhaven.feast;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FeastCatalog {
    /** Unlocked in the same innkeeper dialogue as the banquet table recipe (milestone {@code rep_innkeeper_50}). */
    public static final String REWARD_FEAST_STEWARDS = "rep_innkeeper_50";
    public static final String REWARD_FEAST_HEARTHGLASS = "rep_innkeeper_75";
    public static final String REWARD_FEAST_BERRYCIRCLE = "rep_innkeeper_100";

    public static final FeastDefinition STEWARDS_LEDGER =
        new FeastDefinition(
            "feast_stewards_ledger",
            "aetherhaven_feasts_production.aetherhaven.feast.stewards.name",
            "aetherhaven_feasts_production.aetherhaven.feast.stewards.description",
            List.of(
                MaterialRequirement.ofItem("Food_Bread", 8),
                MaterialRequirement.ofItem("Food_Cheese", 6),
                MaterialRequirement.ofItem("Food_Pie_Pumpkin", 4)
            ),
            50,
            FeastEffectKind.STEWARDS_TAX,
            "UI/Custom/fast-food.png",
            REWARD_FEAST_STEWARDS
        );

    public static final FeastDefinition HEARTHGLASS_VIGIL =
        new FeastDefinition(
            "feast_hearthglass_vigil",
            "aetherhaven_feasts_production.aetherhaven.feast.hearthglass.name",
            "aetherhaven_feasts_production.aetherhaven.feast.hearthglass.description",
            List.of(
                MaterialRequirement.ofItem("Food_Wildmeat_Cooked", 8),
                MaterialRequirement.ofItem("Food_Fish_Grilled", 6),
                MaterialRequirement.ofItem("Food_Vegetable_Cooked", 6)
            ),
            75,
            FeastEffectKind.HEARTHGLASS_DECAY,
            "UI/Custom/taco.png",
            REWARD_FEAST_HEARTHGLASS
        );

    public static final FeastDefinition BERRYCIRCLE_CONCORD =
        new FeastDefinition(
            "feast_berrycircle_concord",
            "aetherhaven_feasts_production.aetherhaven.feast.berrycircle.name",
            "aetherhaven_feasts_production.aetherhaven.feast.berrycircle.description",
            List.of(
                MaterialRequirement.ofItem("Food_Salad_Berry", 4),
                MaterialRequirement.ofItem("Food_Kebab_Fruit", 6),
                MaterialRequirement.ofItem("Food_Popcorn", 8)
            ),
            100,
            FeastEffectKind.BERRYCIRCLE_REP,
            "UI/Custom/salad.png",
            REWARD_FEAST_BERRYCIRCLE
        );

    @Nonnull
    public static final List<FeastDefinition> ALL = List.of(STEWARDS_LEDGER, HEARTHGLASS_VIGIL, BERRYCIRCLE_CONCORD);

    /**
     * True if the player may serve this feast: claimed the matching milestone id in dialogue with any town villager,
     * or already meets the hearth-host reputation threshold (legacy saves from before feast milestone rows existed).
     */
    public static boolean isFeastUnlocked(
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull Store<EntityStore> store,
        @Nonnull FeastDefinition def
    ) {
        if (VillagerReputationService.hasPlayerClaimedRewardId(town, playerUuid, def.unlockRewardId())) {
            return true;
        }
        UUID inn = VillagerReputationService.resolveInnkeeperEntityUuidForReputation(town, store);
        if (inn == null) {
            return false;
        }
        return VillagerReputationService.getOrCreateEntry(town, playerUuid, inn).getReputation()
            >= def.unlockReputationThreshold();
    }

    @Nullable
    public static FeastDefinition findById(@Nonnull String feastId) {
        String k = feastId.trim();
        for (FeastDefinition d : ALL) {
            if (d.id().equals(k)) {
                return d;
            }
        }
        return null;
    }

    private FeastCatalog() {}
}
