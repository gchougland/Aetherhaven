package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hexvane.aetherhaven.community.CommunityPaths;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.plotcreator.PlotTokenIconPng;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("construction")
final class ConstructionTokenIconPathEmptyIconTest {

    @TempDir
    Path dataDir;

    @Test
    void emptyOnDiskIconIsTreatedAsMissing() throws Exception {
        String id = "plot_community_test_empty_icon";
        Path communityIcon = CommunityPaths.iconFile(dataDir, id);
        Files.createDirectories(communityIcon.getParent());
        Files.write(communityIcon, new byte[0]);

        assertNull(ConstructionTokenIconPath.resolveRuntimeIconFile(dataDir, id));
        assertEquals(false, ConstructionTokenIconPath.isIconAvailable(id, dataDir));
    }

    @Test
    void validOnDiskIconIsResolved() throws Exception {
        String id = "plot_community_test_valid_icon";
        Path communityIcon = CommunityPaths.iconFile(dataDir, id);
        Files.createDirectories(communityIcon.getParent());
        byte[] png = new byte[PlotTokenIconPng.MIN_PNG_BYTES];
        png[0] = (byte) 0x89;
        png[1] = 0x50;
        png[2] = 0x4E;
        png[3] = 0x47;
        Files.write(communityIcon, png);

        assertEquals(communityIcon, ConstructionTokenIconPath.resolveRuntimeIconFile(dataDir, id));
        // Not registered in CommonAssetRegistry in unit tests — availability still requires registry.
        assertEquals(false, ConstructionTokenIconPath.isIconAvailable(id, dataDir));
    }

    @Test
    void customIconPreferredOverCommunityWhenValid() throws Exception {
        String id = "plot_custom_pref";
        Path custom = CustomBuildingsPaths.iconFile(dataDir, id);
        Path community = CommunityPaths.iconFile(dataDir, id);
        Files.createDirectories(custom.getParent());
        Files.createDirectories(community.getParent());
        byte[] png = new byte[PlotTokenIconPng.MIN_PNG_BYTES];
        png[0] = (byte) 0x89;
        png[1] = 0x50;
        png[2] = 0x4E;
        png[3] = 0x47;
        Files.write(custom, png);
        Files.write(community, new byte[0]);

        assertEquals(custom, ConstructionTokenIconPath.resolveRuntimeIconFile(dataDir, id));
    }
}
