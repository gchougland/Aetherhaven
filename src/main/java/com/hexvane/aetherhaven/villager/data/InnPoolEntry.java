package com.hexvane.aetherhaven.villager.data;

import javax.annotation.Nonnull;

/** Inn morning visitor pool row from {@link VillagerDefinitionCatalog#innPoolEntriesSorted()}. */
public record InnPoolEntry(@Nonnull String npcRoleId, @Nonnull String visitorBindingKind, int order) {}
