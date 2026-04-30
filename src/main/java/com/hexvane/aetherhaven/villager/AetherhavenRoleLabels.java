package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Role id → in-world character names, profession line keys, and short English labels (tooltips, server console, tithe
 * list when {@link com.hypixel.hytale.server.core.Message} is not used). GUI pages may use
 * {@link com.hexvane.aetherhaven.ui.NpcPortraitProvider#portraitPathForRoleId} for art.
 */
public final class AetherhavenRoleLabels {
    private static final Map<String, String> ROLE_ID_TO_LABEL = Map.ofEntries(
        Map.entry(AetherhavenConstants.ELDER_NPC_ROLE_ID, "Elder Lyren"),
        Map.entry(AetherhavenConstants.INNKEEPER_NPC_ROLE_ID, "Corin Mosscup"),
        Map.entry(AetherhavenConstants.NPC_MERCHANT, "Vex Sunderlane"),
        Map.entry(AetherhavenConstants.NPC_BLACKSMITH, "Garren Vale"),
        Map.entry(AetherhavenConstants.NPC_FARMER, "Irienne Mossmark"),
        Map.entry(AetherhavenConstants.NPC_PRIESTESS, "Serah Thornwell"),
        Map.entry(AetherhavenConstants.NPC_MINER, "Gorruk Stonevein"),
        Map.entry(AetherhavenConstants.NPC_LOGGER, "Seren Fairhollow"),
        Map.entry(AetherhavenConstants.NPC_RANCHER, "Thalen Meadowrun")
    );

    private static final Map<String, String> KIND_TO_ENGLISH_PROFESSION = Map.ofEntries(
        Map.entry(TownVillagerBinding.KIND_ELDER, "Elder"),
        Map.entry(TownVillagerBinding.KIND_INNKEEPER, "Innkeeper"),
        Map.entry(TownVillagerBinding.KIND_MERCHANT, "Merchant"),
        Map.entry(TownVillagerBinding.KIND_BLACKSMITH, "Blacksmith"),
        Map.entry(TownVillagerBinding.KIND_FARMER, "Farmer"),
        Map.entry(TownVillagerBinding.KIND_PRIESTESS, "Priestess"),
        Map.entry(TownVillagerBinding.KIND_MINER, "Miner"),
        Map.entry(TownVillagerBinding.KIND_LOGGER, "Logger"),
        Map.entry(TownVillagerBinding.KIND_RANCHER, "Rancher")
    );

    private AetherhavenRoleLabels() {}

    @Nonnull
    public static String displayNameForRoleId(@Nonnull String roleId) {
        String r = roleId.trim();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            VillagerDefinition d = plugin.getVillagerDefinitionCatalog().byNpcRoleId(r);
            if (d != null) {
                String n = d.getDisplayName();
                if (n != null && !n.isBlank()) {
                    return n.trim();
                }
            }
        }
        String label = ROLE_ID_TO_LABEL.get(r);
        return label != null ? label : r;
    }

    /**
     * Translation key (with {@code server.} prefix) for a villager’s job label, e.g. Blacksmith, Elder. Prefer
     * {@code bindingKind} for residents so inn visitors are not conflated with a permanent job plot.
     */
    @Nonnull
    public static String professionTranslationKey(@Nonnull String roleId, @Nonnull String kind) {
        String k = kind != null ? kind.trim() : "";
        if (!k.isEmpty() && !TownVillagerBinding.isVisitorKind(k)) {
            return "server.aetherhaven.profession.kind." + k;
        }
        return "server.aetherhaven.profession.kind." + professionKindSlugFromRoleId(roleId.trim());
    }

    /**
     * One-line label for server chat / text-only contexts: “Character name (Job)”, matching the treasury tithe list.
     */
    @Nonnull
    public static String listLinePlainEnglish(@Nullable String roleId, @Nonnull String bindingKind) {
        String name;
        if (roleId != null && !roleId.isBlank()) {
            name = displayNameForRoleId(roleId);
        } else {
            name = kindDisplayTitle(bindingKind);
        }
        String jobEn = professionEnglishFor(roleId, bindingKind);
        if (name.trim().equalsIgnoreCase(jobEn.trim())) {
            return name;
        }
        return name + " (" + jobEn + ")";
    }

    @Nonnull
    private static String professionEnglishFor(@Nullable String roleId, @Nonnull String bindingKind) {
        if (!bindingKind.isEmpty() && !TownVillagerBinding.isVisitorKind(bindingKind)) {
            String j = KIND_TO_ENGLISH_PROFESSION.get(bindingKind.trim());
            if (j != null) {
                return j;
            }
        }
        if (roleId != null && !roleId.isBlank()) {
            String k = professionKindSlugFromRoleId(roleId.trim());
            String fromSlug = mapSlugToEnglish(k);
            if (fromSlug != null) {
                return fromSlug;
            }
        }
        return "Resident";
    }

    @Nullable
    private static String mapSlugToEnglish(@Nonnull String slug) {
        return switch (slug) {
            case TownVillagerBinding.KIND_ELDER -> "Elder";
            case TownVillagerBinding.KIND_INNKEEPER -> "Innkeeper";
            case TownVillagerBinding.KIND_MERCHANT -> "Merchant";
            case TownVillagerBinding.KIND_BLACKSMITH -> "Blacksmith";
            case TownVillagerBinding.KIND_FARMER -> "Farmer";
            case TownVillagerBinding.KIND_PRIESTESS -> "Priestess";
            case TownVillagerBinding.KIND_MINER -> "Miner";
            case TownVillagerBinding.KIND_LOGGER -> "Logger";
            case TownVillagerBinding.KIND_RANCHER -> "Rancher";
            default -> null;
        };
    }

    @Nonnull
    public static String professionKindSlugFromRoleId(@Nonnull String roleId) {
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
        if (AetherhavenConstants.NPC_MINER.equals(roleId)) {
            return TownVillagerBinding.KIND_MINER;
        }
        if (AetherhavenConstants.NPC_LOGGER.equals(roleId)) {
            return TownVillagerBinding.KIND_LOGGER;
        }
        if (AetherhavenConstants.NPC_RANCHER.equals(roleId)) {
            return TownVillagerBinding.KIND_RANCHER;
        }
        return "unknown";
    }

    @Nonnull
    private static String kindDisplayTitle(@Nonnull String kind) {
        if (kind.isEmpty()) {
            return "Resident";
        }
        String[] parts = kind.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
        }
        return sb.length() > 0 ? sb.toString() : "Resident";
    }
}
