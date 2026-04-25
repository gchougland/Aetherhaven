package com.hexvane.aetherhaven.ui;

import java.util.UUID;
import javax.annotation.Nonnull;

/** One town resident row for the needs and gift UIs: stable sort, role name from entity when loaded. */
public record TownVillagerRow(@Nonnull String label, @Nonnull UUID entityUuid, @Nonnull String roleId, int kindOrder) {
}
