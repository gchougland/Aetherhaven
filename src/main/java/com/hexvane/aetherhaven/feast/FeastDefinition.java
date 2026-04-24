package com.hexvane.aetherhaven.feast;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Static feast metadata. Unlocked when the matching milestone {@link #unlockRewardId()} is claimed in dialogue with
 * any town villager, or {@link #unlockReputationThreshold()} is met on the resolved hearth-host row for older saves.
 */
public record FeastDefinition(
    @Nonnull String id,
    @Nonnull String titleTranslationKey,
    @Nonnull String descriptionTranslationKey,
    @Nonnull List<MaterialRequirement> costs,
    int unlockReputationThreshold,
    @Nonnull FeastEffectKind effectKind,
    /** e.g. {@code UI/Custom/fast-food.png} (same convention as other mod {@code AssetImage} paths). */
    @Nonnull String menuIconRelativePath,
    /** {@link com.hexvane.aetherhaven.reputation.ReputationRewardCatalog} reward id (claimed in NPC dialogue). */
    @Nonnull String unlockRewardId
) {}
