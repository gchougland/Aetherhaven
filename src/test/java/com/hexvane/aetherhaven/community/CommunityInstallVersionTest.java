package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("construction")
class CommunityInstallVersionTest {
    @TempDir
    Path tempDir;

    @Test
    void hasUpdateWhenRemoteVersionIsHigher() throws Exception {
        CommunityManifestEntry entry = new CommunityManifestEntry();
        setField(entry, "id", "plot_community_test");
        setField(entry, "version", "3");
        Files.createDirectories(CommunityPaths.buildingsDirectory(tempDir));
        Files.writeString(CommunityPaths.buildingFile(tempDir, "plot_community_test"), "{}");
        CommunityInstallVersion.writeInstalledVersion(tempDir, "plot_community_test", "2");

        assertTrue(CommunityInstallVersion.hasUpdate(tempDir, entry));
    }

    @Test
    void noUpdateWhenVersionsMatch() throws Exception {
        CommunityManifestEntry entry = new CommunityManifestEntry();
        setField(entry, "id", "plot_community_test");
        setField(entry, "version", "2");
        Files.createDirectories(CommunityPaths.buildingsDirectory(tempDir));
        Files.writeString(CommunityPaths.buildingFile(tempDir, "plot_community_test"), "{}");
        CommunityInstallVersion.writeInstalledVersion(tempDir, "plot_community_test", "2");

        assertFalse(CommunityInstallVersion.hasUpdate(tempDir, entry));
    }

    @Test
    void legacyInstallDefaultsToVersionOne() throws Exception {
        CommunityManifestEntry entry = new CommunityManifestEntry();
        setField(entry, "id", "plot_community_test");
        setField(entry, "version", "2");
        Files.createDirectories(CommunityPaths.buildingsDirectory(tempDir));
        Files.writeString(CommunityPaths.buildingFile(tempDir, "plot_community_test"), "{}");

        assertEquals("1", CommunityInstallVersion.readInstalledVersion(tempDir, "plot_community_test"));
        assertTrue(CommunityInstallVersion.hasUpdate(tempDir, entry));
    }

    private static void setField(Object target, String name, String value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
