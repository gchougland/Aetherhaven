package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.AetherhavenRoleLabels;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * GUI portraits for {@link VillagerNeedsOverviewPage}, {@link GaiaStatueRevivePage}, etc.: PNGs under
 * {@code Common/Icons/ModelsGenerated/}
 * (asset path {@code Icons/ModelsGenerated/&lt;file&gt;}).
 *
 * <p>Use only {@code Aetherhaven_*.png} filenames here. Shipping {@code Farmer.png}, {@code Merchant.png}, etc. at the
 * stock paths overrides the game's shared villager icons and Memories portraits; incompatible textures have been
 * reported to break unrelated client UI (black panels) on some GPUs.
 */
public final class NpcPortraitProvider {
    private static final String ICON_DIR = "Icons/ModelsGenerated/";
    private static final String MISSING = "UI/Custom/Pages/Memories/MissingIcon.png";

    private static final Map<String, String> ROLE_ID_TO_FILE = Map.ofEntries(
        Map.entry(AetherhavenConstants.ELDER_NPC_ROLE_ID, "Aetherhaven_Elder_Lyren.png"),
        Map.entry(AetherhavenConstants.INNKEEPER_NPC_ROLE_ID, "Aetherhaven_Innkeeper.png"),
        Map.entry("Aetherhaven_Merchant", "Aetherhaven_Merchant.png"),
        Map.entry("Aetherhaven_Blacksmith", "Aetherhaven_Blacksmith.png"),
        Map.entry("Aetherhaven_Farmer", "Aetherhaven_Farmer.png"),
        Map.entry(AetherhavenConstants.NPC_PRIESTESS, "Aetherhaven_Priestess.png"),
        Map.entry(AetherhavenConstants.NPC_MINER, "Aetherhaven_Miner.png"),
        Map.entry(AetherhavenConstants.NPC_LOGGER, "Aetherhaven_Logger.png"),
        Map.entry(AetherhavenConstants.NPC_RANCHER, "Aetherhaven_Rancher.png")
    );

    private NpcPortraitProvider() {}

    @Nonnull
    public static String portraitPathForRoleId(@Nonnull String roleId) {
        String r = roleId.trim();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            VillagerDefinition d = plugin.getVillagerDefinitionCatalog().byNpcRoleId(r);
            if (d != null) {
                String p = d.getPortraitIcon();
                if (p != null && !p.isBlank()) {
                    String t = p.trim();
                    if (t.startsWith("UI/") || t.startsWith("Icons/")) {
                        return t;
                    }
                    return ICON_DIR + t;
                }
            }
        }
        String file = ROLE_ID_TO_FILE.get(r);
        return file != null ? ICON_DIR + file : MISSING;
    }

    @Nonnull
    public static String displayLabelForRoleId(@Nonnull String roleId) {
        return AetherhavenRoleLabels.displayNameForRoleId(roleId);
    }

    /**
     * Lang key {@code aetherhaven_jewelry_geode.aetherhaven.profession.kind.&lt;slug&gt;} for UI lines such as {@code Name (Profession)}.
     */
    @Nonnull
    public static String professionTranslationKey(@Nonnull String roleId, @Nonnull String kind) {
        return AetherhavenRoleLabels.professionTranslationKey(roleId, kind);
    }
}
