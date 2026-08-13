package com.hexvane.aetherhaven.bard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.bard.data.BardSongDefinition;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("bard")
class BardShufflePlaylistTest {

    @Test
    void buildQueue_excludesCurrentSongWhenOthersExist() {
        List<BardSongDefinition> songs = List.of(song("a"), song("b"), song("c"));

        List<String> queue = BardShufflePlaylist.buildQueue(songs, "b", new Random(1L));

        assertEquals(2, queue.size());
        assertFalse(queue.contains("b"));
        assertTrue(queue.contains("a"));
        assertTrue(queue.contains("c"));
    }

    @Test
    void buildQueue_keepsOnlySongWhenCatalogHasOneTrack() {
        List<String> queue = BardShufflePlaylist.buildQueue(List.of(song("solo")), "solo", new Random(1L));

        assertEquals(List.of("solo"), queue);
    }

    @Test
    void fromString_parsesPlaybackModes() {
        assertEquals(BardPlaybackMode.ONCE, BardPlaybackMode.fromString(null));
        assertEquals(BardPlaybackMode.ONCE, BardPlaybackMode.fromString("once"));
        assertEquals(BardPlaybackMode.LOOP, BardPlaybackMode.fromString("LOOP"));
        assertEquals(BardPlaybackMode.SHUFFLE, BardPlaybackMode.fromString("shuffle"));
    }

    private static BardSongDefinition song(String id) {
        String json = "{\"id\":\"" + id + "\",\"displayLangKey\":\"k\",\"durationSeconds\":30}";
        return new com.google.gson.Gson().fromJson(json, BardSongDefinition.class);
    }
}
