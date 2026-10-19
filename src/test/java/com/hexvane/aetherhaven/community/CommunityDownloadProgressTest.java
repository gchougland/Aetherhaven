package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class CommunityDownloadProgressTest {
    @Test
    void loadingPushesEveryStepFromTheWorkerWithoutWorldTicks() {
        var job = new CommunityDownloadProgress("Style");
        var received = new java.util.concurrent.CopyOnWriteArrayList<CommunityDownloadProgress.Snapshot>();
        job.listen(received::add);
        CompletableFuture.runAsync(() -> {
            job.loading(42, 72, "Images registered");
            for (int i = 43; i <= 72; i++) job.loading(i, 72, "Loading resource " + i);
            job.complete();
        }).join();
        var loading = received.stream().filter(s -> s.phase() == CommunityDownloadProgress.Phase.LOADING).toList();
        assertEquals(IntStream.rangeClosed(42, 72).boxed().toList(), loading.stream().map(CommunityDownloadProgress.Snapshot::done).toList());
        for (int i = 1; i < loading.size(); i++) assertTrue(loading.get(i).fraction() > loading.get(i - 1).fraction());
        assertTrue(received.getLast().terminal());
    }

    @Test
    void removedHudCannotReceiveUpdatesOrDetachItsReplacement() {
        var job = new CommunityDownloadProgress("Style");
        var old = new java.util.ArrayList<CommunityDownloadProgress.Snapshot>();
        var current = new java.util.ArrayList<CommunityDownloadProgress.Snapshot>();
        java.util.function.Consumer<CommunityDownloadProgress.Snapshot> first = old::add;
        java.util.function.Consumer<CommunityDownloadProgress.Snapshot> second = current::add;
        job.listen(first);
        job.listen(second);
        job.unlisten(first);
        job.loading(1, 2, "Loading");
        assertEquals(1, old.size());
        assertEquals(2, current.size());
        job.unlisten(second);
        job.complete();
        assertEquals(2, current.size());
    }

    @Test
    void disconnectedHudCannotAbortResourceLoading() {
        var job = new CommunityDownloadProgress("Style");
        job.listen(snapshot -> { throw new IllegalStateException("Disconnected"); });
        assertDoesNotThrow(() -> { job.loading(1, 1, "Loaded"); job.complete(); });
        assertTrue(job.terminal());
    }

    private static CommunityManifestEntry entry(int id) {
        return new Gson().fromJson("{\"id\":\"plot_" + id + "\",\"displayName\":\"Building " + id + "\"}", CommunityManifestEntry.class);
    }

    @Test
    void concurrentDownloadsShowCurrentFilesAndAccurateCountsWithoutCompletingBeforeLoading() {
        var job = new CommunityDownloadProgress("Stormwind");
        job.start(12);
        job.file(entry(0), "building shape");
        job.file(entry(1), "preview image");
        var snapshot = job.snapshot();
        assertTrue(snapshot.detail().contains("Building 0 — building shape"));
        assertTrue(snapshot.detail().contains("Building 1 — preview image"));
        assertEquals(12, snapshot.remaining());
        CompletableFuture.allOf(IntStream.range(0, 12).mapToObj(i -> CompletableFuture.runAsync(() -> {
            job.file(entry(i), "building settings");
            job.finished(entry(i), CommunityDownloadService.InstallResult.SUCCESS);
            // Repeated or late notifications must not inflate counts or resurrect finished files.
            job.finished(entry(i), CommunityDownloadService.InstallResult.SUCCESS);
            job.file(entry(i), "late file");
        })).toArray(CompletableFuture[]::new)).join();
        assertEquals(12, job.snapshot().done());
        assertEquals(0, job.snapshot().remaining());
        assertFalse(job.snapshot().detail().contains("late file"));
        assertFalse(job.terminal());
        assertTrue(job.snapshot().fraction() < 1);
        job.loading("Loading buildings...");
        assertEquals(CommunityDownloadProgress.Phase.LOADING, job.snapshot().phase());
        assertEquals("Loading buildings...", job.snapshot().detail());
        assertTrue(job.snapshot().fraction() < 1);
        job.complete();
        assertTrue(job.terminal());
        assertEquals(1, job.snapshot().fraction());
        assertEquals("12 downloaded and loaded, 0 skipped, 0 failed.", job.snapshot().detail());
    }

    @Test
    void skipsAndFailuresNameTheBuildingAndPersistInImmutableSnapshots() {
        var job = new CommunityDownloadProgress("Stormwind");
        job.start(4);
        job.skipped(entry(0), false);
        job.skipped(entry(1), true);
        job.finished(entry(2), CommunityDownloadService.InstallResult.DOWNLOAD_FAILED);
        var before = job.snapshot();
        job.finished(entry(3), CommunityDownloadService.InstallResult.UNSAFE_PREFAB);
        job.complete();
        var after = job.snapshot();
        assertEquals(3, before.done());
        assertEquals(1, before.errors().size());
        assertEquals(4, after.done());
        assertEquals(2, after.skipped());
        assertEquals(2, after.failed());
        assertEquals(2, after.errors().size());
        assertTrue(after.errors().getFirst().contains("Building 2: could not download"));
        assertTrue(after.errors().getLast().contains("Building 3: the building file failed validation"));
        assertEquals("Finished with errors", after.heading());
        assertFalse(job.expired(System.currentTimeMillis()+15_000));
        assertTrue(job.expired(System.currentTimeMillis()+61_000));
    }

    @Test
    void resourceLoadFailureCannotBeOverwrittenByLateSuccess() {
        var job = new CommunityDownloadProgress("Stormwind");
        job.start(1);
        job.finished(entry(0), CommunityDownloadService.InstallResult.SUCCESS);
        job.loading("Loading buildings...");
        job.fail("Could not load buildings. Please try again.");
        var failure = job.snapshot();
        job.complete();
        job.loading("Late loading");
        job.start(3);
        assertEquals(failure, job.snapshot());
        assertEquals(CommunityDownloadProgress.Phase.FAILED, failure.phase());
        assertEquals("Download stopped", failure.heading());
    }

    @Test
    void loadingCountsAdvanceIndependentlyOfDownloadedBuildings() {
        var job = new CommunityDownloadProgress("Style");
        job.start(1);
        job.finished(entry(0), CommunityDownloadService.InstallResult.SUCCESS);
        job.loading(0, 14, "Loading images");
        double previous = job.snapshot().fraction();
        for (int i = 1; i <= 14; i++) {
            job.loading(i, 14, "Resource " + i + " loaded");
            assertEquals(i, job.snapshot().done());
            assertEquals(14-i, job.snapshot().remaining());
            assertTrue(job.snapshot().fraction() > previous);
            previous = job.snapshot().fraction();
        }
        job.complete();
        assertEquals(1, job.snapshot().total());
        assertEquals(1, job.snapshot().done());
    }

    @Test
    void missingModBuildsAreSkippedWithoutAnError() {
        var job = new CommunityDownloadProgress("Style");
        job.start(1);
        job.skipped(entry(0), true);
        job.complete();
        assertEquals(1, job.snapshot().skipped());
        assertEquals(0, job.snapshot().failed());
        assertTrue(job.snapshot().errors().isEmpty());
        assertEquals("Ready to use", job.snapshot().heading());
    }

    @Test
    void emptyOrAlreadyInstalledStyleFinishesWithoutErrors() {
        var empty = new CommunityDownloadProgress("Empty");
        empty.start(0);
        empty.complete();
        assertEquals("No buildings need downloading.", empty.snapshot().detail());
        assertTrue(empty.snapshot().errors().isEmpty());
        var installed = new CommunityDownloadProgress("Installed");
        installed.start(1);
        installed.skipped(entry(1), false);
        installed.complete();
        assertEquals("0 downloaded and loaded, 1 skipped, 0 failed.", installed.snapshot().detail());
        assertEquals("Ready to use", installed.snapshot().heading());
    }
}
