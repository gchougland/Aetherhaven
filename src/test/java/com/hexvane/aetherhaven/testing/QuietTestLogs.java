package com.hexvane.aetherhaven.testing;

import com.hypixel.hytale.logger.backend.HytaleLoggerBackend;
import java.util.logging.Level;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Keeps the game's logger quiet while unit tests run.
 *
 * <p>Many tests hand the mod damaged files or impossible states on purpose, and the mod rightly logs each one as an
 * error with a stack trace. The game's logger writes straight to the real console, past Gradle's test output
 * settings, so a passing build was full of lines that read like failures. A failed test is still reported by JUnit
 * with its own exception.
 *
 * <p>Loaded for every test class through {@code META-INF/services} (see {@code junit-platform.properties}). Run with
 * {@code -Daetherhaven.testLogs=true} (Gradle: {@code -PtestLogs=true}) to see the log again.
 */
public final class QuietTestLogs implements BeforeAllCallback {
    static {
        if (!Boolean.getBoolean("aetherhaven.testLogs")) {
            // The engine asks this for each logger's level, the way a server takes them from its config.
            HytaleLoggerBackend.LOG_LEVEL_LOADER = name -> Level.OFF;
            HytaleLoggerBackend.reloadLogLevels();
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        // The static block above has done the work once for the whole test run.
    }
}
