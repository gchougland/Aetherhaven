package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

public record PlotAssemblyJob(
    @Nonnull UUID plotId,
    @Nonnull UUID ownerUuid,
    @Nonnull Vector3i anchor,
    @Nonnull Rotation yaw,
    @Nonnull List<PendingBlock> pendingBlocks,
    @Nonnull List<Holder<EntityStore>> prefabEntitiesInOrder,
    @Nonnull IPrefabBuffer buffer,
    @Nonnull PrefabRotation prefabRotation,
    int prefabId,
    /** Wall-clock ms between auto placements: (selfBuildGameDays * msPerGameDay) / N. */
    long slotWallMs,
    @Nonnull String constructionId
) {}
