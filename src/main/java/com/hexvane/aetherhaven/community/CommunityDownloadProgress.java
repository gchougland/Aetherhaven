package com.hexvane.aetherhaven.community;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Worker-thread progress; UI reads immutable snapshots without touching game state. */
public final class CommunityDownloadProgress {
    public enum Phase { PREPARING, DOWNLOADING, LOADING, COMPLETE, FAILED }

    public record Snapshot(String title, Phase phase, String detail, int done, int total, int failed,
                           int skipped, List<String> errors) {
        public int remaining() { return Math.max(0, total - done); }

        /** Reserve the end of the bar for loading; downloaded files are not ready to use yet. */
        public double fraction() {
            return phase == Phase.COMPLETE ? 1 : phase == Phase.LOADING ? .7 + .3 * done / Math.max(1, total) : total == 0 ? 0 : .7 * done / total;
        }

        public boolean terminal() { return phase == Phase.COMPLETE || phase == Phase.FAILED; }

        public String heading() {
            return switch (phase) {
                case PREPARING -> "Finding buildings";
                case DOWNLOADING -> "Downloading";
                case LOADING -> "Loading resources";
                case COMPLETE -> errors.isEmpty() ? "Ready to use" : "Finished with errors";
                case FAILED -> "Download stopped";
            };
        }
    }

    private final String title;
    private Phase phase = Phase.PREPARING;
    private String detail = "Checking the marketplace...";
    private int done, total, failed, skipped;
    private int loaded, loadTotal;
    private final Map<String, String> active = new LinkedHashMap<>();
    private final Set<String> finished = new HashSet<>();
    private final List<String> errors = new ArrayList<>();
    private long expiresAt = Long.MAX_VALUE;
    private Consumer<Snapshot> listener;

    public CommunityDownloadProgress(String title) { this.title = title; }

    public synchronized void start(int total) {
        if (terminal()) return;
        this.total = Math.max(0, total);
        phase = Phase.DOWNLOADING;
        detail = "Preparing downloads...";
        publish();
    }

    public synchronized void file(CommunityManifestEntry entry, String file) {
        if (terminal() || finished.contains(entry.getId())) return;
        active.put(entry.getId(), shortName(entry.getDisplayName()) + " — " + file);
        publish();
    }

    public synchronized void skipped(CommunityManifestEntry entry, boolean missingMods) {
        if (terminal() || !finished.add(entry.getId())) return;
        skipped++;
        done++;
        active.remove(entry.getId());
        // Optional builds requiring absent mods are normal skips, not failed downloads.
        publish();
    }

    public synchronized void finished(CommunityManifestEntry entry, CommunityDownloadService.InstallResult result) {
        if (terminal() || !finished.add(entry.getId())) return;
        done++;
        active.remove(entry.getId());
        if (result != CommunityDownloadService.InstallResult.SUCCESS) {
            failed++;
            errors.add(entry.getDisplayName() + ": " + reason(result));
        }
        if (done == total) detail = "Downloads checked. Preparing resources...";
        publish();
    }

    public synchronized void loading(String detail) { loading(0, 1, detail); }

    public synchronized void loading(int loaded, int total, String detail) {
        if (terminal()) return;
        phase = Phase.LOADING;
        active.clear();
        this.loaded = loaded;
        this.loadTotal = Math.max(1, total);
        this.detail = detail;
        publish();
    }

    public synchronized void complete() {
        if (terminal()) return;
        phase = Phase.COMPLETE;
        active.clear();
        detail = total == 0 ? "No buildings need downloading."
            : (done - skipped - failed) + " downloaded and loaded, " + skipped + " skipped, " + failed + " failed.";
        expiresAt = System.currentTimeMillis() + (errors.isEmpty() ? 12_000 : 60_000);
        publish();
    }

    public synchronized void fail(String message) {
        if (terminal()) return;
        phase = Phase.FAILED;
        active.clear();
        errors.add(message);
        detail = message;
        expiresAt = System.currentTimeMillis() + 60_000;
        publish();
    }

    /** Packet-only observers run on the reporting thread; never access the entity store here. */
    public synchronized void listen(Consumer<Snapshot> listener) {
        this.listener = listener;
        publish();
    }

    public synchronized void unlisten(Consumer<Snapshot> listener) {
        if (this.listener == listener) this.listener = null;
    }

    /** Also flushes throttled download updates on a world tick, even if no new file has finished. */
    public synchronized void publish() {
        if (listener == null) return;
        try {
            listener.accept(snapshot());
        } catch (RuntimeException e) {
            // A disconnected client must not abort a shared resource install.
            java.util.logging.Logger.getLogger(CommunityDownloadProgress.class.getName())
                .log(java.util.logging.Level.FINE, "Could not send style progress", e);
        }
    }

    public synchronized boolean terminal() { return phase == Phase.COMPLETE || phase == Phase.FAILED; }
    public synchronized boolean expired(long now) { return now >= expiresAt; }

    public synchronized Snapshot snapshot() {
        return new Snapshot(title, phase, active.isEmpty() ? detail : String.join("\n", active.values()),
            phase == Phase.LOADING ? loaded : done, phase == Phase.LOADING ? loadTotal : total, failed, skipped, List.copyOf(errors));
    }

    private static String shortName(String value) { return value.length() > 28 ? value.substring(0, 25) + "..." : value; }

    private static String reason(CommunityDownloadService.InstallResult result) {
        return switch (result) {
            case SUCCESS -> "";
            case NOT_FOUND -> "a required file is missing.";
            case DOWNLOAD_FAILED -> "could not download a required file. Try again.";
            case IO_ERROR -> "could not save the files. Check the server log.";
            case MISSING_MODS -> "required mods are missing.";
            case UNSAFE_PREFAB -> "the building file failed validation.";
            case ICON_FAILED -> "the preview image could not be downloaded or loaded.";
        };
    }
}
