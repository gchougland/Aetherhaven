package com.hexvane.aetherhaven.backup;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.hexvane.aetherhaven.town.TownWorldFile;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Incremental, disk-backed ZIP preparation. Runtime only calls prepare on its backup worker. */
public final class AetherhavenBackupArchive {
    public static final String MANIFEST = "aetherhaven-backup-manifest.json";
    record FileState(long size, java.nio.file.attribute.FileTime modified, Object key) {}
    record Cached(FileState state, String hash) {}
    record Prepared(Path path, String hash, Map<String, Cached> files, int changedFiles, long sourceBytes) {}
    private Map<String, Cached> previous;
    private FileState publishedState;

    /** Returns null when unchanged; unchanged ZIP entries retain their compressed bytes. */
    Prepared prepare(Path source, Path published, Path scratch) throws IOException {
        source = source.toAbsolutePath().normalize();
        published = published.toAbsolutePath().normalize();
        scratch = scratch.toAbsolutePath().normalize();
        if (published.startsWith(source) || scratch.startsWith(source)) throw new IOException("Backup output must be outside mod data");
        Map<String, FileState> files = listFiles(source);
        boolean reuse = previous != null && Files.isRegularFile(published) && state(published).equals(publishedState);
        if (reuse && files.size() == previous.size() && files.entrySet().stream()
            .allMatch(e -> previous.containsKey(e.getKey()) && previous.get(e.getKey()).state.equals(e.getValue()))) return null;
        Files.createDirectories(scratch);
        Path zip = scratch.resolve("prepared.zip");
        Path raw = scratch.resolve("current-file.tmp");
        Files.deleteIfExists(zip);
        if (reuse) copy(published, zip);
        Map<String, Cached> next = new LinkedHashMap<>();
        int changed = 0;
        long readBytes = 0;
        try {
            // zipfs copies unchanged compressed entries verbatim on commit. useTempFile prevents
            // large changed entries being buffered in the Java heap.
            try (var fs = FileSystems.newFileSystem(zip, Map.of("create", "true", "useTempFile", "true"))) {
                if (reuse) {
                    for (String old : previous.keySet()) {
                        if (!files.containsKey(old)) Files.deleteIfExists(fs.getPath("data", old));
                    }
                }
                for (var file : files.entrySet()) {
                    checkInterrupted();
                    Cached old = reuse ? previous.get(file.getKey()) : null;
                    if (old != null && old.state.equals(file.getValue())) { next.put(file.getKey(), old); continue; }
                    Path original = source.resolve(file.getKey());
                    FileState captured = copyStable(original, raw);
                    if (file.getKey().endsWith(".json")) validateJson(raw, original);
                    String hash = sha256(raw);
                    Path entry = fs.getPath("data", file.getKey());
                    Files.createDirectories(entry.getParent());
                    Files.copy(raw, entry, StandardCopyOption.REPLACE_EXISTING);
                    next.put(file.getKey(), new Cached(captured, hash));
                    changed++;
                    readBytes += captured.size;
                }
                var manifest = new LinkedHashMap<String, Object>();
                manifest.put("formatVersion", 1);
                manifest.put("capturedAtUtc", Instant.now().toString());
                manifest.put("sourceDirectoryName", source.getFileName().toString());
                Map<String, String> hashes = new LinkedHashMap<>();
                next.forEach((name, cached) -> hashes.put("data/" + name, cached.hash));
                manifest.put("files", hashes);
                Files.writeString(fs.getPath(MANIFEST), new Gson().toJson(manifest), StandardCharsets.UTF_8);
            }
            checkInterrupted();
            return new Prepared(zip, sha256(zip), next, changed, readBytes);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(zip);
            throw e;
        } finally { Files.deleteIfExists(raw); }
    }

    /** Runs under vanilla's resource save lock: only a same-filesystem rename and a stat. */
    void publish(Prepared ready, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try { Files.move(ready.path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(ready.path, target, StandardCopyOption.REPLACE_EXISTING); }
        previous = ready.files;
        publishedState = state(target);
    }

    private static FileState copyStable(Path source, Path raw) throws IOException {
        // Release the town writer's lock BEFORE validation, hashing or compression.
        if (source.getFileName().toString().startsWith("towns.json")) {
            synchronized (TownWorldFile.class) { return copyStableUnlocked(source, raw); }
        }
        return copyStableUnlocked(source, raw);
    }
    private static FileState copyStableUnlocked(Path source, Path raw) throws IOException {
        for (int attempt = 0; attempt < 3; attempt++) {
            FileState before = state(source);
            copy(source, raw);
            if (before.equals(state(source)) && Files.size(raw) == before.size) return before;
        }
        throw new IOException("File changed repeatedly during backup: " + source);
    }
    private static void validateJson(Path raw, Path source) throws IOException {
        try (var reader = new JsonReader(Files.newBufferedReader(raw, StandardCharsets.UTF_8))) {
            if (reader.peek() == JsonToken.END_DOCUMENT || reader.peek() == JsonToken.NULL) throw new IOException("Empty JSON");
            reader.skipValue();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON");
        } catch (IOException | RuntimeException e) { throw new IOException("Invalid JSON in backup source: " + source, e); }
    }
    private static Map<String, FileState> listFiles(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing backup source: " + root);
        Map<String, FileState> result = new TreeMap<>();
        try (var walk = Files.walk(root)) {
            for (var it = walk.iterator(); it.hasNext();) {
                checkInterrupted();
                Path path = it.next();
                var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isSymbolicLink() || attrs.isOther()) throw new IOException("Unsupported linked/special file: " + path);
                if (!attrs.isRegularFile()) continue;
                String name = path.getFileName().toString();
                if (name.endsWith(".tmp") || name.endsWith(".lock") || name.equals("LOCK")) continue;
                result.put(root.relativize(path).toString().replace('\\', '/'), new FileState(attrs.size(), attrs.lastModifiedTime(), attrs.fileKey()));
            }
        }
        return result;
    }
    private static FileState state(Path path) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile()) throw new IOException("Not a regular file: " + path);
        return new FileState(attrs.size(), attrs.lastModifiedTime(), attrs.fileKey());
    }
    private static void copy(Path from, Path to) throws IOException {
        try (var in = Files.newInputStream(from, LinkOption.NOFOLLOW_LINKS); var out = Files.newOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) != -1;) { checkInterrupted(); out.write(buffer, 0, n); }
        }
    }
    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Backup worker stopped");
    }
    static String sha256(Path file) throws IOException {
        var digest = digest();
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = input.read(buffer)) != -1;) { checkInterrupted(); digest.update(buffer, 0, n); }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static String sha256(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    // Synchronous utility for offline tests. Runtime uses its dedicated worker.
    public static String writeSnapshot(Path source, Path target) throws IOException {
        Path scratch = Files.createTempDirectory("aetherhaven-backup-test-");
        try {
            var builder = new AetherhavenBackupArchive();
            var ready = builder.prepare(source, target, scratch);
            builder.publish(ready, target);
            return ready.hash;
        } finally { cleanScratch(scratch); }
    }
    public static byte[] capture(Path source) throws IOException {
        Path target = Files.createTempFile("aetherhaven-backup-test-", ".zip");
        try { writeSnapshot(source, target); return Files.readAllBytes(target); }
        finally { Files.deleteIfExists(target); }
    }
    static void cleanScratch(Path scratch) throws IOException {
        if (!Files.exists(scratch)) return;
        try (var files = Files.walk(scratch)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}
