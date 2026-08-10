package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves NPC role ids for important-spot villager previews. */
public final class PlotCreatorSpotPreviewRoles {
    private PlotCreatorSpotPreviewRoles() {}

    public static boolean usesVillagerPreview(@Nonnull PlotCreatorSubstepType type) {
        return switch (type) {
            case WORK_POI,
                BARD_WORK_POI,
                PLANNING_DESK_POI,
                SLEEP_POI,
                EAT_POI,
                FUN_POI,
                SHOP_POI,
                TOURIST_VISIT_POI,
                FESTIVAL_TOURIST_SPOT,
                INNKEEPER_SPAWN,
                VISITOR_SPAWN,
                GUILD_MASTER_SPAWN,
                ADVENTURER_SPAWN,
                FESTIVAL_NPC -> true;
            default -> false;
        };
    }

    @Nonnull
    public static String resolveNpcRoleId(
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind,
        @Nullable AetherhavenPlugin plugin
    ) {
        return switch (type) {
            case FESTIVAL_NPC ->
                workResidentKind != null && !workResidentKind.isBlank()
                    ? workResidentKind.trim()
                    : PlotCreatorFestivalNpcRoles.SEED_SELLER;
            case INNKEEPER_SPAWN -> AetherhavenConstants.INNKEEPER_NPC_ROLE_ID;
            case GUILD_MASTER_SPAWN -> AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID;
            case BARD_WORK_POI -> AetherhavenConstants.BARD_NPC_ROLE_ID;
            case PLANNING_DESK_POI ->
                roleForKind(
                    workResidentKind != null && !workResidentKind.isBlank()
                        ? workResidentKind
                        : TownVillagerBinding.KIND_ELDER,
                    plugin
                );
            case WORK_POI -> roleForKind(workResidentKind, plugin);
            case TOURIST_VISIT_POI,
                FESTIVAL_TOURIST_SPOT,
                SHOP_POI,
                VISITOR_SPAWN,
                ADVENTURER_SPAWN,
                SLEEP_POI,
                EAT_POI,
                FUN_POI -> AetherhavenConstants.NPC_TOWNSFOLK;
            default -> AetherhavenConstants.NPC_TOWNSFOLK;
        };
    }

    @Nonnull
    private static String roleForKind(@Nullable String workResidentKind, @Nullable AetherhavenPlugin plugin) {
        if (workResidentKind == null || workResidentKind.isBlank()) {
            return AetherhavenConstants.NPC_TOWNSFOLK;
        }
        String kind = workResidentKind.trim().toLowerCase(Locale.ROOT);
        if (TownVillagerBinding.KIND_ELDER.equals(kind)) {
            return AetherhavenConstants.ELDER_NPC_ROLE_ID;
        }
        if (TownVillagerBinding.KIND_INNKEEPER.equals(kind)) {
            return AetherhavenConstants.INNKEEPER_NPC_ROLE_ID;
        }
        if (TownVillagerBinding.KIND_GUILD_MASTER.equals(kind)) {
            return AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID;
        }
        if (TownVillagerBinding.KIND_BARD.equals(kind)) {
            return AetherhavenConstants.BARD_NPC_ROLE_ID;
        }
        if (plugin != null) {
            VillagerDefinition byKind = plugin.getVillagerDefinitionCatalog().byDialogueVillagerKind(kind);
            if (byKind != null && !byKind.getNpcRoleId().isEmpty()) {
                return byKind.getNpcRoleId();
            }
            VillagerDefinition byRole = plugin.getVillagerDefinitionCatalog().byNpcRoleId(workResidentKind.trim());
            if (byRole != null && !byRole.getNpcRoleId().isEmpty()) {
                return byRole.getNpcRoleId();
            }
        }
        return AetherhavenConstants.NPC_TOWNSFOLK;
    }
}
