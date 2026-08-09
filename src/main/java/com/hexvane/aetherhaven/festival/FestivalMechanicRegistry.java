package com.hexvane.aetherhaven.festival;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Crossmod registry mapping a festival JSON {@code mechanicId} to the code that runs it. */
public final class FestivalMechanicRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, FestivalMechanic> byId = new ConcurrentHashMap<>();

    public void register(@Nonnull String mechanicId, @Nonnull FestivalMechanic mechanic) {
        String key = normalize(mechanicId);
        if (key == null) {
            throw new IllegalArgumentException("festival mechanic id must not be blank");
        }
        FestivalMechanic previous = byId.put(key, mechanic);
        if (previous != null) {
            LOGGER.atInfo().log("Festival mechanic %s replaced by a later registration", key);
        }
    }

    @Nullable
    public FestivalMechanic get(@Nullable String mechanicId) {
        String key = normalize(mechanicId);
        return key != null ? byId.get(key) : null;
    }

    public boolean isRegistered(@Nullable String mechanicId) {
        return get(mechanicId) != null;
    }

    @Nonnull
    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }

    @Nullable
    private static String normalize(@Nullable String mechanicId) {
        if (mechanicId == null || mechanicId.isBlank()) {
            return null;
        }
        return mechanicId.trim().toLowerCase(Locale.ROOT);
    }
}
