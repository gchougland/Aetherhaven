package com.hexvane.aetherhaven.villager.data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Inn morning visitor pool row from {@link VillagerDefinitionCatalog#innPoolEntriesSorted()}. */
public record InnPoolEntry(
    @Nonnull String npcRoleId,
    @Nonnull String visitorBindingKind,
    int order,
    int weight,
    @Nonnull InnPoolRequires requires,
    @Nullable int[] spawnLocal
) {
    public InnPoolEntry(@Nonnull String npcRoleId, @Nonnull String visitorBindingKind, int order) {
        this(npcRoleId, visitorBindingKind, order, 1, InnPoolRequires.EMPTY, null);
    }

    public InnPoolEntry(@Nonnull String npcRoleId, @Nonnull String visitorBindingKind, int order, int weight) {
        this(npcRoleId, visitorBindingKind, order, weight, InnPoolRequires.EMPTY, null);
    }

    public InnPoolEntry(
        @Nonnull String npcRoleId,
        @Nonnull String visitorBindingKind,
        int order,
        @Nonnull InnPoolRequires requires,
        @Nullable int[] spawnLocal
    ) {
        this(npcRoleId, visitorBindingKind, order, 1, requires, spawnLocal);
    }
}
