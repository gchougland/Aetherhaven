package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * GUI portraits for {@link VillagerNeedsOverviewPage}: PNGs under {@code Common/Icons/ModelsGenerated/}
 * (asset path {@code Icons/ModelsGenerated/&lt;file&gt;}).
 */
public final class NpcPortraitProvider {
    private static final String ICON_DIR = "Icons/ModelsGenerated/";
    private static final String MISSING = "UI/Custom/Pages/Memories/MissingIcon.png";

    private static final Map<String, String> ROLE_ID_TO_FILE = Map.ofEntries(
        Map.entry(AetherhavenConstants.ELDER_NPC_ROLE_ID, "Village_Elder.png"),
        Map.entry(AetherhavenConstants.INNKEEPER_NPC_ROLE_ID, "Innkeep.png"),
        Map.entry("Aetherhaven_Merchant", "Merchant.png"),
        Map.entry("Aetherhaven_Blacksmith", "Blacksmith.png"),
        Map.entry("Aetherhaven_Farmer", "Farmer.png")
    );

    private NpcPortraitProvider() {}

    @Nonnull
    public static String portraitPathForRoleId(@Nonnull String roleId) {
        String file = ROLE_ID_TO_FILE.get(roleId.trim());
        return file != null ? ICON_DIR + file : MISSING;
    }
}
