package com.hexvane.aetherhaven.feast;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Static feast metadata (costs, unlock rep, UI icon path relative to {@code Common/UI/Custom/Aetherhaven/}).
 */
public record FeastDefinition(
    @Nonnull String id,
    @Nonnull String titleTranslationKey,
    @Nonnull String descriptionTranslationKey,
    @Nonnull List<MaterialRequirement> costs,
    int minInnkeeperRep,
    @Nonnull FeastEffectKind effectKind,
    /** e.g. {@code ../fast-food.png} — sibling of {@code Aetherhaven/} under Custom. */
    @Nonnull String menuIconRelativePath
) {}
