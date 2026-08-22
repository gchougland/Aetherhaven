package com.hexvane.aetherhaven.blockpalette;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per player clipboard for block palette draft selections (paint tab copy and paste). */
public final class BlockPaletteClipboard {
    private static final ConcurrentHashMap<UUID, Map<String, String>> BY_PLAYER = new ConcurrentHashMap<>();

    private BlockPaletteClipboard() {}

    public static void copy(@Nonnull UUID playerId, @Nonnull Map<String, String> selections) {
        BY_PLAYER.put(playerId, new LinkedHashMap<>(selections));
    }

    @Nullable
    public static Map<String, String> peek(@Nonnull UUID playerId) {
        Map<String, String> clip = BY_PLAYER.get(playerId);
        if (clip == null || clip.isEmpty()) {
            return null;
        }
        return Map.copyOf(clip);
    }

    public static void clear(@Nonnull UUID playerId) {
        BY_PLAYER.remove(playerId);
    }
}
