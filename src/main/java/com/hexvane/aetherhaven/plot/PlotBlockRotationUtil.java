package com.hexvane.aetherhaven.plot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;

/** Reads placed plot/charter block yaw from world block rotation (NESW variant). */
public final class PlotBlockRotationUtil {
    private PlotBlockRotationUtil() {}

    @Nonnull
    public static Rotation readBlockYaw(@Nonnull World world, @Nonnull Vector3i blockWorldPos) {
        int y = blockWorldPos.y;
        if (y < ChunkUtil.MIN_Y || y > ChunkUtil.HEIGHT_MINUS_1) {
            return Rotation.None;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef =
                chunkStore.getChunkSectionReferenceAtBlock(blockWorldPos.x, blockWorldPos.y, blockWorldPos.z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return Rotation.None;
        }
        Store<ChunkStore> store = sectionRef.getStore();
        BlockSection section = store.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) {
            return Rotation.None;
        }
        return section.getRotation(blockWorldPos.x, blockWorldPos.y, blockWorldPos.z).yaw();
    }
}
