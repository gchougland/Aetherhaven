package com.hexvane.aetherhaven.asset;

import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Lists {@code .json} resources under a classpath directory prefix (e.g. {@code Server/Aetherhaven/Quests/}) without a manifest.
 * Works for resources in a JAR and for exploded directories (IDE / Gradle resources).
 */
public final class ClasspathResourceScanner {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String ANCHOR_RESOURCE = "Server/Constructions/constructions.json";

    private ClasspathResourceScanner() {}

    /**
     * @param directoryPrefix e.g. {@code Server/Aetherhaven/Quests/} (trailing slash optional)
     */
    @Nonnull
    public static List<String> listJsonFiles(@Nonnull ClassLoader classLoader, @Nonnull String directoryPrefix) {
        String prefix = directoryPrefix.endsWith("/") ? directoryPrefix : directoryPrefix + "/";
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            URL anchor = classLoader.getResource(ANCHOR_RESOURCE);
            if (anchor == null) {
                LOGGER.atWarning().log("ClasspathResourceScanner: missing anchor %s", ANCHOR_RESOURCE);
                return List.of();
            }
            if ("jar".equalsIgnoreCase(anchor.getProtocol())) {
                scanJar(anchor, prefix, out);
            } else {
                scanDirectory(anchor, prefix, out);
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("ClasspathResourceScanner failed for prefix %s", prefix);
        }
        return new ArrayList<>(out);
    }

    private static void scanJar(@Nonnull URL anchorJarEntry, @Nonnull String prefix, @Nonnull LinkedHashSet<String> out)
        throws IOException {
        JarURLConnection conn = (JarURLConnection) anchorJarEntry.openConnection();
        try (JarFile jar = conn.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                if (name.startsWith(prefix) && name.endsWith(".json")) {
                    out.add(name);
                }
            }
        }
    }

    private static void scanDirectory(@Nonnull URL anchorResource, @Nonnull String prefix, @Nonnull LinkedHashSet<String> out)
        throws Exception {
        URI uri = anchorResource.toURI();
        Path anchorPath = Path.of(uri);
        Path serverDir = findServerDirectory(anchorPath);
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return;
        }
        String rel = prefix.substring("Server/".length());
        if (rel.endsWith("/")) {
            rel = rel.substring(0, rel.length() - 1);
        }
        Path target = serverDir;
        for (String part : rel.split("/")) {
            if (!part.isEmpty()) {
                target = target.resolve(part);
            }
        }
        if (!Files.isDirectory(target)) {
            return;
        }
        try (var stream = Files.walk(target, FileVisitOption.FOLLOW_LINKS)) {
            stream
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
                .forEach(
                    p -> {
                        Path relPath = serverDir.relativize(p);
                        String resourcePath =
                            "Server/" + relPath.toString().replace(serverDir.getFileSystem().getSeparator(), "/");
                        out.add(resourcePath);
                    }
                );
        }
    }

    @Nullable
    private static Path findServerDirectory(@Nonnull Path anchorPath) {
        Path p = anchorPath;
        for (int i = 0; i < 8 && p != null; i++) {
            if (p.getFileName() != null && "Server".equalsIgnoreCase(p.getFileName().toString())) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }
}
