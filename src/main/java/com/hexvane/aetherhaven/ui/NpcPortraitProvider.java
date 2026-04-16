package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * GUI portraits for {@link VillagerNeedsOverviewPage}, {@link GaiaStatueRevivePage}, etc.: PNGs under
 * {@code Common/Icons/ModelsGenerated/}
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
        Map.entry("Aetherhaven_Farmer", "Farmer.png"),
        Map.entry(AetherhavenConstants.NPC_PRIESTESS, "Priestess.png")
    );

    /** Short labels for town needs UI (aligned with server.lang npcRoles.*.name). */
    private static final Map<String, String> ROLE_ID_TO_LABEL = Map.ofEntries(
        Map.entry(AetherhavenConstants.ELDER_NPC_ROLE_ID, "Elder Lyren"),
        Map.entry(AetherhavenConstants.INNKEEPER_NPC_ROLE_ID, "Corin Mosscup"),
        Map.entry(AetherhavenConstants.NPC_MERCHANT, "Vex Sunderlane"),
        Map.entry(AetherhavenConstants.NPC_BLACKSMITH, "Garren Vale"),
        Map.entry(AetherhavenConstants.NPC_FARMER, "Irienne Mossmark"),
        Map.entry(AetherhavenConstants.NPC_PRIESTESS, "Serah Thornwell")
    );

    private NpcPortraitProvider() {}

    @Nonnull
    public static String portraitPathForRoleId(@Nonnull String roleId) {
        String file = ROLE_ID_TO_FILE.get(roleId.trim());
        return file != null ? ICON_DIR + file : MISSING;
    }

    @Nonnull
    public static String displayLabelForRoleId(@Nonnull String roleId) {
        String label = ROLE_ID_TO_LABEL.get(roleId.trim());
        return label != null ? label : roleId.trim();
    }

    /**
     * Lang key {@code server.aetherhaven.profession.kind.&lt;slug&gt;} for UI lines such as {@code Name (Profession)}.
     */
    @Nonnull
    public static String professionTranslationKey(@Nonnull String roleId, @Nonnull String kind) {
        String k = kind != null ? kind.trim() : "";
        if (!k.isEmpty() && !TownVillagerBinding.isVisitorKind(k)) {
            return "server.aetherhaven.profession.kind." + k;
        }
        return "server.aetherhaven.profession.kind." + professionKindSlugFromRoleId(roleId.trim());
    }

    @Nonnull
    private static String professionKindSlugFromRoleId(@Nonnull String roleId) {
        if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equals(roleId)) {
            return TownVillagerBinding.KIND_ELDER;
        }
        if (AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equals(roleId)) {
            return TownVillagerBinding.KIND_INNKEEPER;
        }
        if (AetherhavenConstants.NPC_MERCHANT.equals(roleId)) {
            return TownVillagerBinding.KIND_MERCHANT;
        }
        if (AetherhavenConstants.NPC_BLACKSMITH.equals(roleId)) {
            return TownVillagerBinding.KIND_BLACKSMITH;
        }
        if (AetherhavenConstants.NPC_FARMER.equals(roleId)) {
            return TownVillagerBinding.KIND_FARMER;
        }
        if (AetherhavenConstants.NPC_PRIESTESS.equals(roleId)) {
            return TownVillagerBinding.KIND_PRIESTESS;
        }
        return "unknown";
    }
}
