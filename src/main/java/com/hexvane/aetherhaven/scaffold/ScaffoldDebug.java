package com.hexvane.aetherhaven.scaffold;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Temporary scaffold diagnostics. Set {@link #ENABLED} to {@code false} to silence logs after debugging.
 */
public final class ScaffoldDebug {
    /** Master switch — flip to {@code false} to disable all scaffold debug logging. */
    public static volatile boolean ENABLED = true;

    static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private ScaffoldDebug() {}

    /**
     * Flogger's {@code log(String, Object...)} must not be called with a forwarded {@code Object[]} from our own
     * varargs — Java passes that array as a single varargs element, so every {@code %s} after the first becomes
     * {@code [ERROR: MISSING LOG ARGUMENT]}. Build the line with {@link String#format} first (same idea as
     * {@code SpawnMarkerBlockStateSystems} in the game sources).
     */
    private static void logPrefixed(Level level, String prefix, String fmt, Object[] args) {
        String message = args == null || args.length == 0 ? prefix + fmt : String.format(Locale.ROOT, prefix + fmt, args);
        LOG.at(level).log("%s", message);
    }

    public static void place(String fmt, Object... args) {
        if (ENABLED) {
            logPrefixed(Level.INFO, "[ScaffoldPlace] ", fmt, args);
        }
    }

    public static void resolve(String fmt, Object... args) {
        if (ENABLED) {
            logPrefixed(Level.INFO, "[ScaffoldResolve] ", fmt, args);
        }
    }

    public static void breaking(String fmt, Object... args) {
        if (ENABLED) {
            logPrefixed(Level.INFO, "[ScaffoldBreak] ", fmt, args);
        }
    }

    public static void physics(String fmt, Object... args) {
        if (ENABLED) {
            logPrefixed(Level.INFO, "[ScaffoldPhysics] ", fmt, args);
        }
    }
}
