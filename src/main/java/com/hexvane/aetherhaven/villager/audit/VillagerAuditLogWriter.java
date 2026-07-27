package com.hexvane.aetherhaven.villager.audit;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nonnull;

/** Appends villager audit events to per-world JSONL files on a background thread. */
public final class VillagerAuditLogWriter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String AUDIT_FILE = "audit.jsonl";

    private static volatile ExecutorService executor;

    private VillagerAuditLogWriter() {}

    public static void append(@Nonnull AetherhavenPlugin plugin, @Nonnull World world, @Nonnull VillagerAuditEvent event) {
        if (!plugin.getConfig().get().isVillagerAuditLogEnabled()) {
            return;
        }
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        Path file = resolveAuditFile(plugin, world);
        String line = event.toJsonLine() + System.lineSeparator();
        executor().execute(
            () -> {
                try {
                    Files.createDirectories(file.getParent());
                    Files.writeString(
                        file,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                    );
                } catch (IOException e) {
                    LOGGER.atWarning().withCause(e).log("Failed to append villager audit log");
                }
            }
        );
    }

    @Nonnull
    static Path resolveAuditFile(@Nonnull AetherhavenPlugin plugin, @Nonnull World world) {
        String worldDir = sanitizeWorldDirName(world.getName());
        return TownManager.pluginData(plugin).resolve("villager_audit").resolve(worldDir).resolve(AUDIT_FILE);
    }

    @Nonnull
    private static ExecutorService executor() {
        ExecutorService ex = executor;
        if (ex == null) {
            synchronized (VillagerAuditLogWriter.class) {
                ex = executor;
                if (ex == null) {
                    ThreadFactory factory = r -> {
                        Thread t = new Thread(r, "Aetherhaven-VillagerAudit");
                        t.setDaemon(true);
                        return t;
                    };
                    ex = Executors.newSingleThreadExecutor(factory);
                    executor = ex;
                }
            }
        }
        return ex;
    }

    @Nonnull
    private static String sanitizeWorldDirName(@Nonnull String worldName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < worldName.length(); i++) {
            char c = worldName.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return !sb.isEmpty() ? sb.toString() : "world";
    }
}
