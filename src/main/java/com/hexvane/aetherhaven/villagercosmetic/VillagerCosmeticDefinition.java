package com.hexvane.aetherhaven.villagercosmetic;

import javax.annotation.Nonnull;

/** One unlockable villager cosmetic from the catalog. */
public record VillagerCosmeticDefinition(
    @Nonnull String id,
    @Nonnull String slot,
    @Nonnull String displayNameKey,
    @Nonnull String model,
    @Nonnull String texture,
    @Nonnull String unlockItemId
) {
    public static final String SLOT_HEAD_ACCESSORY = "HeadAccessory";
    public static final String SLOT_FACE_ACCESSORY = "FaceAccessory";
    public static final String DEFAULT_ID = "default";
}
