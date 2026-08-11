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
        Map.entry(AetherhavenConstants.NPC_RANCHER, "Thalen Meadowrun"),
        Map.entry(AetherhavenConstants.NPC_CRYSTAL_KEEPER, "Vaelith Prismshade"),
        Map.entry(AetherhavenConstants.NPC_PYROTECHNIC, "Grubble Sparkmatch"),
        Map.entry(AetherhavenConstants.NPC_CLOWN, "Bozo Bleak"),
        Map.entry(AetherhavenConstants.NPC_FLORIST, "Ivy Bloomwell"),
        Map.entry(AetherhavenConstants.NPC_CHEF, "Pepper Ashford"),
        Map.entry(AetherhavenConstants.NPC_BUILDER, "Rowan Ridgecraft"),
        Map.entry(AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID, "Lyra Fairhollow")
    );

    private static final Map<String, String> KIND_TO_ENGLISH_PROFESSION = Map.ofEntries(
        Map.entry(TownVillagerBinding.KIND_ELDER, "Elder"),
        Map.entry(TownVillagerBinding.KIND_INNKEEPER, "Innkeeper"),
        Map.entry(TownVillagerBinding.KIND_MERCHANT, "Merchant"),
        Map.entry(TownVillagerBinding.KIND_CHEF, "Chef"),
        Map.entry(TownVillagerBinding.KIND_BLACKSMITH, "Blacksmith"),
        Map.entry(TownVillagerBinding.KIND_FARMER, "Farmer"),
        Map.entry(TownVillagerBinding.KIND_PRIESTESS, "Priestess"),
        Map.entry(TownVillagerBinding.KIND_MINER, "Miner"),
        Map.entry(TownVillagerBinding.KIND_LOGGER, "Logger"),
        Map.entry(TownVillagerBinding.KIND_RANCHER, "Rancher"),
        Map.entry(TownVillagerBinding.KIND_CRYSTAL_KEEPER, "Crystal Keeper"),
        Map.entry(TownVillagerBinding.KIND_PYROTECHNIC, "Pyrotechnic"),
        Map.entry(TownVillagerBinding.KIND_CLOWN, "Clown"),
        Map.entry(TownVillagerBinding.KIND_FLORIST, "Florist"),
        Map.entry(TownVillagerBinding.KIND_BUILDER, "Builder"),
        Map.entry(TownVillagerBinding.KIND_GUILD_MASTER, "Guild Master"),
        Map.entry(TownVillagerBinding.KIND_TOWNSFOLK, "Townsfolk")
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
     * Translation key (bundle-prefixed, e.g. {@code aetherhaven_town.aetherhaven.profession.kind.miner})
     * for a villager’s job label, e.g. Blacksmith, Elder. Prefer
     * {@code bindingKind} for residents so inn visitors are not conflated with a permanent job plot.
     */
    @Nonnull
    public static String professionTranslationKey(@Nonnull String roleId, @Nonnull String kind) {
        String r = roleId.trim();
        String k = kind != null ? kind.trim() : "";
        if (TownVillagerBinding.KIND_GUARD.equals(k)) {
            return guardTypeTranslationKey(r);
        }
        if (TownVillagerBinding.KIND_TOWNSFOLK.equals(k)) {
            return "aetherhaven_town.aetherhaven.profession.kind.townsfolk";
        }
        if (!k.isEmpty() && !TownVillagerBinding.isVisitorKind(k)) {
            return "aetherhaven_town.aetherhaven.profession.kind." + k;
        }
        String slug = professionKindSlugFromRoleId(r);
        if (TownVillagerBinding.KIND_GUARD.equals(slug)) {
            return guardTypeTranslationKey(r);
        }
        return "aetherhaven_town.aetherhaven.profession.kind." + slug;
    }

    /** Guard class label (Knight / Archer / Mage) for patrol, tithe, and resident UI. */
    @Nonnull
    public static String guardTypeTranslationKey(@Nonnull String roleId) {
        if (AetherhavenConstants.NPC_GUARD_ARCHER.equals(roleId)) {
            return "aetherhaven_items.aetherhaven.patrolWand.guardTypeArcher";
        }
        if (AetherhavenConstants.NPC_GUARD_MAGE.equals(roleId)) {
            return "aetherhaven_items.aetherhaven.patrolWand.guardTypeMage";
        }
        if (AetherhavenConstants.NPC_GUARD_ROGUE.equals(roleId)) {
            return "aetherhaven_items.aetherhaven.patrolWand.guardTypeRogue";
        }
        return "aetherhaven_items.aetherhaven.patrolWand.guardTypeKnight";
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
        String kind = bindingKind.trim();
        if (TownVillagerBinding.KIND_GUARD.equals(kind)) {
            return guardTypeEnglish(roleId);
        }
        if (!kind.isEmpty() && !TownVillagerBinding.isVisitorKind(kind)) {
            String j = KIND_TO_ENGLISH_PROFESSION.get(kind);
            if (j != null) {
                return j;
            }
        }
        if (roleId != null && !roleId.isBlank()) {
            String r = roleId.trim();
            if (isGuardRoleId(r)) {
                return guardTypeEnglish(r);
            }
            String fromSlug = mapSlugToEnglish(professionKindSlugFromRoleId(r));
            if (fromSlug != null) {
                return fromSlug;
            }
        }
        return "Resident";
    }

    @Nonnull
    private static String guardTypeEnglish(@Nullable String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return "Knight";
        }
        String r = roleId.trim();
        if (AetherhavenConstants.NPC_GUARD_ARCHER.equals(r)) {
            return "Archer";
        }
        if (AetherhavenConstants.NPC_GUARD_MAGE.equals(r)) {
            return "Mage";
        }
        if (AetherhavenConstants.NPC_GUARD_ROGUE.equals(r)) {
            return "Rogue";
        }
        return "Knight";
    }

    private static boolean isGuardRoleId(@Nonnull String roleId) {
        return AetherhavenConstants.NPC_GUARD_KNIGHT.equals(roleId)
            || AetherhavenConstants.NPC_GUARD_ARCHER.equals(roleId)
            || AetherhavenConstants.NPC_GUARD_MAGE.equals(roleId)
            || AetherhavenConstants.NPC_GUARD_ROGUE.equals(roleId);
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
            case TownVillagerBinding.KIND_BUILDER -> "Builder";
            case TownVillagerBinding.KIND_GUILD_MASTER -> "Guild Master";
            case TownVillagerBinding.KIND_TOWNSFOLK -> "Townsfolk";
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
        if (AetherhavenConstants.NPC_CRYSTAL_KEEPER.equals(roleId)) {
            return TownVillagerBinding.KIND_CRYSTAL_KEEPER;
        }
        if (AetherhavenConstants.NPC_PYROTECHNIC.equals(roleId)) {
            return TownVillagerBinding.KIND_PYROTECHNIC;
        }
        if (AetherhavenConstants.NPC_CLOWN.equals(roleId)) {
            return TownVillagerBinding.KIND_CLOWN;
        }
        if (AetherhavenConstants.NPC_FLORIST.equals(roleId)) {
            return TownVillagerBinding.KIND_FLORIST;
        }
        if (AetherhavenConstants.NPC_CHEF.equals(roleId)) {
            return TownVillagerBinding.KIND_CHEF;
        }
        if (AetherhavenConstants.NPC_BUILDER.equals(roleId)) {
            return TownVillagerBinding.KIND_BUILDER;
        }
        if (AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID.equals(roleId)) {
            return TownVillagerBinding.KIND_GUILD_MASTER;
        }
        if (isGuardRoleId(roleId)) {
            return TownVillagerBinding.KIND_GUARD;
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
