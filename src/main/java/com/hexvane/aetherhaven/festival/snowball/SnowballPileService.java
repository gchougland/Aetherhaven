package com.hexvane.aetherhaven.festival.snowball;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import javax.annotation.Nonnull;

/** Places and clears snowball pile blocks. Call from {@code world.execute}, never as a Store write in a tick. */
public final class SnowballPileService {
    private static final RotationTuple FLAT = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);

    private SnowballPileService() {}

    public static void placePile(@Nonnull World world, @Nonnull SnowballSession.PileSpot spot) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(spot.worldX(), spot.worldZ()));
        if (chunk == null) {
            return;
        }
        chunk.placeBlock(
            spot.worldX(),
            spot.worldY(),
            spot.worldZ(),
            SnowballIds.PILE_BLOCK_ID,
            FLAT,
            SnowballIds.PLACE_SETTINGS,
            false
        );
    }

    public static void clearPile(@Nonnull World world, @Nonnull SnowballSession.PileSpot spot) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(spot.worldX(), spot.worldZ()));
        if (chunk == null) {
            return;
        }
        chunk.setBlock(
            spot.worldX(),
            spot.worldY(),
            spot.worldZ(),
            BlockType.EMPTY_ID,
            BlockType.EMPTY,
            0,
            0,
            SnowballIds.PLACE_SETTINGS
        );
    }

    public static void placeAll(@Nonnull World world, @Nonnull SnowballSession session) {
        for (SnowballSession.PileSpot spot : session.pileSpotsView()) {
            placePile(world, spot);
            session.markPilePresent(spot);
        }
    }

    public static void clearAll(@Nonnull World world, @Nonnull SnowballSession session) {
        for (SnowballSession.PileSpot spot : session.pileSpotsView()) {
            clearPile(world, spot);
        }
    }
}
