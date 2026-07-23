package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.math.util.ChunkUtil;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("raid")
class TownTerritoryClaimsRaidBorderTest {

    @Test
    void charterToClaimBorderAlongUsesApproachSideNotFarthestEdge() {
        TownRecord town =
            new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "w", 100, 64, 100, 1, 1, 0L);
        town.getClaimedTerritoryChunksMutable().clear();
        int homeChunkX = ChunkUtil.chunkCoordinate(100);
        int homeChunkZ = ChunkUtil.chunkCoordinate(100);
        town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(homeChunkX, homeChunkZ));
        town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(homeChunkX + 5, homeChunkZ));

        int eastBorder = TownTerritoryClaims.charterToClaimBorderAlong(town, 1, 0);
        int northBorder = TownTerritoryClaims.charterToClaimBorderAlong(town, 0, -1);
        int farthestBorder = TownTerritoryClaims.maxCharterToClaimEdgeBlocks(town);

        assertEquals(farthestBorder, eastBorder);
        assertTrue(northBorder < eastBorder, "north approach should not use east expansion distance");
    }
}
