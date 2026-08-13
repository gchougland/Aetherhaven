package com.hexvane.aetherhaven.bard;

import com.hexvane.aetherhaven.bard.data.BardSongDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Builds a shuffled remaining-song queue so the bard can walk through tracks without instant repeats. */
public final class BardShufflePlaylist {
    private BardShufflePlaylist() {}

    @Nonnull
    public static List<String> buildQueue(
        @Nonnull List<BardSongDefinition> songs,
        @Nullable String excludeId,
        @Nonnull Random random
    ) {
        List<String> ids = new ArrayList<>();
        String skip = excludeId != null ? excludeId.trim() : "";
        for (BardSongDefinition song : songs) {
            String id = song.getId();
            if (id.isEmpty()) {
                continue;
            }
            if (!skip.isEmpty() && skip.equalsIgnoreCase(id) && songs.size() > 1) {
                continue;
            }
            ids.add(id);
        }
        Collections.shuffle(ids, random);
        if (ids.isEmpty() && !skip.isEmpty()) {
            ids.add(skip);
        }
        return ids;
    }
}
