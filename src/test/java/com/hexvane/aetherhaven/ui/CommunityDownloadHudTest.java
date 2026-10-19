package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.community.CommunityDownloadProgress;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class CommunityDownloadHudTest {
    @Test
    void anActiveDownloadBelongsToThePlayerAcrossMenusUntilItFinishesOrFails() {
        var player = UUID.randomUUID();
        var first = CommunityDownloadHud.begin(player, "First style");
        assertNotNull(first);
        assertNull(CommunityDownloadHud.begin(player, "Another menu"));
        first.loading("Loading resources...");
        assertNull(CommunityDownloadHud.begin(player, "Still loading"));
        first.complete();
        var next = CommunityDownloadHud.begin(player, "Second style");
        assertNotNull(next);
        assertNotSame(first, next);
        next.fail("Download failed");
        var retry = CommunityDownloadHud.begin(player, "Retry");
        assertNotNull(retry);
        retry.complete();
    }

    @Test
    void loadingAndErrorsRenderAsDistinctStatesEvenAfterAllDownloadsFinish() {
        var loading = new CommunityDownloadProgress.Snapshot("Farm style", CommunityDownloadProgress.Phase.LOADING,
            "Loading buildings...", 4, 8, 0, 0, List.of());
        assertTrue(value(loading, "#DownloadPhase.Text").contains("Loading resources"));
        assertTrue(value(loading, "#DownloadCount.Text").contains("4 remaining"));
        assertTrue(value(loading, "#DownloadRemainder.Visible").contains("true"));
        assertTrue(value(loading, "#DownloadErrorPanel.Visible").contains("false"));
        var failed = new CommunityDownloadProgress.Snapshot("Farm style", CommunityDownloadProgress.Phase.FAILED,
            "Could not load buildings", 8, 8, 0, 0, List.of("Could not load buildings"));
        assertTrue(value(failed, "#DownloadPhase.Text").contains("Download stopped"));
        assertTrue(value(failed, "#DownloadErrorPanel.Visible").contains("true"));
        assertTrue(value(failed, "#DownloadErrors.Text").contains("Could not load buildings"));
    }

    @Test
    void backgroundPanelsHaveBoundedHeightInsteadOfFillingTheScreen() throws Exception {
        String markup = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/resources/Common/UI/Custom/Aetherhaven/CommunityDownloadHud.ui"));
        for (String panel : List.of("DownloadPanel", "DownloadErrorPanel")) {
            var anchor = java.util.regex.Pattern.compile("Group #" + panel + "\\s*\\{.*?Anchor:\\s*\\(([^)]+)\\)",
                java.util.regex.Pattern.DOTALL).matcher(markup);
            assertTrue(anchor.find(), panel);
            var height = java.util.regex.Pattern.compile("Height:\\s*(\\d+)").matcher(anchor.group(1));
            assertTrue(height.find(), panel + " must specify its height");
            assertTrue(Integer.parseInt(height.group(1)) <= 220, panel + " must remain compact");
        }
    }

    private static String value(CommunityDownloadProgress.Snapshot state, String selector) {
        return Arrays.stream(CommunityDownloadHud.commands(state).getCommands())
            .filter(command -> selector.equals(command.selector)).findFirst().orElseThrow().data;
    }
}
