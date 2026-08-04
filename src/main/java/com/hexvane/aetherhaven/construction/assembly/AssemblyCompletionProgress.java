package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Mutable state for time-sliced {@link PlotAssemblyService} completion across world tasks. */
final class AssemblyCompletionProgress {
    enum Phase {
        SOLIDS,
        INTERACTIVE,
        FLUIDS,
        ENTITIES,
        BOOKKEEPING
    }

    @Nonnull
    Phase phase = Phase.SOLIDS;
    int index;
    @Nonnull
    IPrefabBuffer completionBuffer;
    boolean borrowedCompletionBuffer;
    @Nullable
    List<PendingBlock> solidCells;
    @Nullable
    List<PendingBlock> interactiveCells;
    @Nullable
    LocalCachedChunkAccessor chunkAccessor;

    AssemblyCompletionProgress(
        @Nonnull IPrefabBuffer completionBuffer,
        boolean borrowedCompletionBuffer
    ) {
        this.completionBuffer = completionBuffer;
        this.borrowedCompletionBuffer = borrowedCompletionBuffer;
    }
}
