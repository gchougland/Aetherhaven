package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.logger.HytaleLogger;

/** Optional bridge to [Loot4Everyone](https://github.com/MimStar/Loot4Everyone-Hytale) when that mod is present. */
public final class Loot4EveryoneIntegration {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile boolean hooked;
    private static volatile boolean loggedEnabled;

    private Loot4EveryoneIntegration() {}

    public static boolean isAvailable() {
        return Loot4EveryoneReflection.getTemplateResourceType() != null;
    }

    public static boolean isHooked() {
        return hooked;
    }

    public static boolean tryInitialize() {
        if (isAvailable()) {
            return true;
        }
        return Loot4EveryoneReflection.tryResolve();
    }

    /** Marks the soft hook ready once Loot4Everyone types resolve. Safe to call repeatedly until hooked. */
    public static void registerIfAvailable() {
        if (hooked) {
            return;
        }
        if (!tryInitialize()) {
            return;
        }
        hooked = true;
        if (!loggedEnabled) {
            loggedEnabled = true;
            LOGGER.atInfo().log("Loot4Everyone compatibility enabled.");
        }
    }
}
