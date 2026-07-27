package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;

/** Ordered prefab cells and entities for construction paste ({@link ConstructionPasteOps#buildSequence}). */
public record ConstructionPrefabSequence(
    @Nonnull List<PendingBlock> pendingBlocks,
    @Nonnull List<Holder<EntityStore>> prefabEntitiesInOrder,
    @Nonnull PrefabRotation prefabRotation
) {}
