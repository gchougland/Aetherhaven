package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.math.util.ChunkUtil;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownTerritoryClaimsSellTest {

    @Test
    void sellRefundIsHalfOfLastExpandPrice() {
        TownRecord town = starterTown(2);
        AetherhavenPluginConfig cfg = new AetherhavenPluginConfig();
        // Defaults: first 20, increment 20. N=0 → last=20 → refund=10
        assertEquals(10L, TownTerritoryClaims.sellClaimBlockRefundGold(town, cfg));

        int cx = TownTerritoryClaims.charterChunkX(town);
        int cz = TownTerritoryClaims.charterChunkZ(town);
        assertTrue(TownTerritoryClaims.addClaimBlock(town, cx, cz - 4));
        // N=1 → last=20 → refund=10
        assertEquals(10L, TownTerritoryClaims.sellClaimBlockRefundGold(town, cfg));
        assertEquals(40L, TownTerritoryClaims.nextClaimBlockCostGold(town, cfg));

        assertTrue(TownTerritoryClaims.addClaimBlock(town, cx + 2, cz - 4));
        // N=2 → last=40 → refund=20
        assertEquals(20L, TownTerritoryClaims.sellClaimBlockRefundGold(town, cfg));
        assertEquals(60L, TownTerritoryClaims.nextClaimBlockCostGold(town, cfg));
    }

    @Test
    void cannotSellSquareWithPlotInside() {
        TownRecord town = starterTown(2);
        int cx = TownTerritoryClaims.charterChunkX(town);
        int cz = TownTerritoryClaims.charterChunkZ(town);
        int ax = cx - 2;
        int az = cz - 2;
        int blockX = ChunkUtil.minBlock(ax);
        int blockZ = ChunkUtil.minBlock(az);
        town.addPlotInstance(
            new PlotInstance(
                UUID.randomUUID(),
                "test",
                PlotInstanceState.COMPLETE,
                new PlotFootprintRecord(blockX, 64, blockZ, blockX + 2, 70, blockZ + 2),
                blockX,
                64,
                blockZ,
                0L
            )
        );
        assertEquals(
            TownTerritoryClaims.SellClaimBlockReject.HAS_BUILDINGS,
            TownTerritoryClaims.reasonCannotSellClaimBlock(town, ax, az)
        );
        assertFalse(TownTerritoryClaims.canSellClaimBlock(town, ax, az));
    }

    @Test
    void cannotSellSquareContainingCharter() {
        TownRecord town = starterTown(2);
        int cx = TownTerritoryClaims.charterChunkX(town);
        int cz = TownTerritoryClaims.charterChunkZ(town);
        assertTrue(TownTerritoryClaims.isClaimBlockFullyOwned(town, cx, cz));
        assertEquals(
            TownTerritoryClaims.SellClaimBlockReject.CHARTER_OUTSIDE,
            TownTerritoryClaims.reasonCannotSellClaimBlock(town, cx, cz)
        );
    }

    @Test
    void cannotSellIfItWouldDisconnectClaims() {
        TownRecord town =
            new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "w", 0, 64, 0, 1, 1, 0L);
        town.getClaimedTerritoryChunksMutable().clear();
        int cx = ChunkUtil.chunkCoordinate(0);
        int cz = ChunkUtil.chunkCoordinate(0);
        addBlock(town, cx, cz);
        addBlock(town, cx + 2, cz);
        addBlock(town, cx + 4, cz);
        town.setCharterPosition(ChunkUtil.minBlock(cx), 64, ChunkUtil.minBlock(cz));

        assertEquals(
            TownTerritoryClaims.SellClaimBlockReject.WOULD_SPLIT,
            TownTerritoryClaims.reasonCannotSellClaimBlock(town, cx + 2, cz)
        );
        assertFalse(TownTerritoryClaims.removeClaimBlock(town, cx + 2, cz));
    }

    @Test
    void removeExpansionBlockDropsExpansionCount() {
        TownRecord town = starterTown(2);
        int cx = TownTerritoryClaims.charterChunkX(town);
        int cz = TownTerritoryClaims.charterChunkZ(town);
        int ax = cx;
        int az = cz - 4;
        assertTrue(TownTerritoryClaims.addClaimBlock(town, ax, az));
        assertEquals(1, TownTerritoryClaims.countExpansionClaimBlocks(town));
        assertTrue(TownTerritoryClaims.canSellClaimBlock(town, ax, az));
        assertTrue(TownTerritoryClaims.removeClaimBlock(town, ax, az));
        assertEquals(0, TownTerritoryClaims.countExpansionClaimBlocks(town));
        assertFalse(TownTerritoryClaims.contains(town, ax, az));
    }

    @Test
    void canSellEmptyStarterEdgeSquare() {
        TownRecord town = starterTown(2);
        int cx = TownTerritoryClaims.charterChunkX(town);
        int cz = TownTerritoryClaims.charterChunkZ(town);
        int ax = cx - 2;
        int az = cz - 2;
        assertNull(TownTerritoryClaims.reasonCannotSellClaimBlock(town, ax, az));
        assertTrue(TownTerritoryClaims.removeClaimBlock(town, ax, az));
        assertTrue(TownTerritoryClaims.containsBlock(town, town.getCharterX(), town.getCharterZ()));
    }

    private static TownRecord starterTown(int radius) {
        TownRecord town =
            new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "w", 16, 64, 16, 1, radius, 0L);
        TownTerritoryClaims.initializeStarterClaims(town);
        return town;
    }

    private static void addBlock(TownRecord town, int ax, int az) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(ax + dx, az + dz));
            }
        }
    }
}
