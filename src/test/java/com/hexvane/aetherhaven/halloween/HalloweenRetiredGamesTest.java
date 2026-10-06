package com.hexvane.aetherhaven.halloween;

import com.hexvane.aetherhaven.town.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class HalloweenRetiredGamesTest {
    @TempDir Path directory;
    @Test void deletedTownsRetainRewardAndCleanupThroughBothWorldSavePaths() throws Exception {
        var player = UUID.randomUUID();
        var state = new HalloweenGameState();
        var spot = new HalloweenGameState.Spot(UUID.randomUUID(),5,30,5,"old building");
        var first = state.start(player,10,spot);
        state.pickup(player,first.id()); state.reward(player,first.id(),10);
        state.start(UUID.randomUUID(),10,spot);
        var archive = new HalloweenGameState(); archive.absorbRetired(state);
        assertNull(state.active()); assertNull(archive.active());
        var path = directory.resolve("towns.json");
        TownWorldFile.writeBytesAtomic(path,TownWorldFile.toJsonBytes(List.of(),archive));
        var file = TownWorldFile.readOrEmpty(path);
        assertTrue(file.getTowns().isEmpty());
        assertTrue(file.retiredHalloweenGames().rewarded(player,10));
        assertFalse(file.retiredHalloweenGames().rewarded(player,11));
        assertEquals(List.of(spot),file.retiredHalloweenGames().cleanup());
        file.writeAtomic(path);
        var second = TownWorldFile.readOrEmpty(path).retiredHalloweenGames();
        assertTrue(second.rewarded(player,10)); assertEquals(List.of(spot),second.cleanup());
        second.cleaned(spot); assertTrue(second.cleanup().isEmpty());
    }
    @Test void oldSavesHaveAnEmptyArchiveAndMergingPreservesLatestReward() throws Exception {
        var path=directory.resolve("towns.json");Files.writeString(path,"{\"towns\":[]}");
        var archive=TownWorldFile.readOrEmpty(path).retiredHalloweenGames();
        var player=UUID.randomUUID();
        for(long day:new long[]{15,10,16}) {
            var retired=new HalloweenGameState();
            var game=retired.start(player,day,new HalloweenGameState.Spot(UUID.randomUUID(),0,1,0,"fixture"));
            retired.pickup(player,game.id());retired.reward(player,game.id(),day);archive.absorbRetired(retired);
        }
        assertTrue(archive.rewarded(player,16));assertFalse(archive.rewarded(player,17));
        assertTrue(archive.cleanup().isEmpty());
    }
}
